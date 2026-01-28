/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.cassandra.concurrent.ScheduledExecutors;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.schema.SchemaConstants;

import static java.lang.String.format;

/**
 * Manages multiple, distinct attribute hierarchies in-memory for fast checking.
 * This class is a pure in-memory cache. It is not responsible for persistence.
 * The caller is responsible for persisting changes and then calling addEdge to update the cache.
 */
public class AttributeHierarchyManager
{
    private static final Logger logger = LoggerFactory.getLogger(AttributeHierarchyManager.class);
    public static final AttributeHierarchyManager instance = new AttributeHierarchyManager();

    private static final String HIERARCHY_CACHE_FILENAME = "hierarchy.cache";
    private static final String HIERARCHY_METADATA_KEY = "singleton";
    private static final long SAVE_INTERVAL_SECONDS = 300; // 5 minutes

    private final Map<ImmutablePair<String, String>, Set<String>> descendants = new ConcurrentHashMap<>();
    private final Map<ImmutablePair<String, String>, Set<String>> ancestors = new ConcurrentHashMap<>();

    private volatile boolean initialized = false;
    private volatile boolean isDirty = false;
    private ScheduledFuture<?> saveTaskFuture;

    private final Kryo kryo = new Kryo();

    private AttributeHierarchyManager() {
        // Register common classes used in our maps for better performance and robustness
        kryo.register(ConcurrentHashMap.class);
        kryo.register(HashSet.class);
        kryo.register(ImmutablePair.class);
        kryo.register(String.class);
    }

    public synchronized void initialize() {

        if (initialized) return;

        Path cacheFile = getCacheFile();
        long cacheTimestamp = -1;
        try {
            if (Files.exists(cacheFile)) {
                cacheTimestamp = Files.getLastModifiedTime(cacheFile).toMillis();
            }
        } catch (IOException e) {
            logger.warn("Could not get last modified time for hierarchy cache file {}. Assuming stale cache.", cacheFile, e);
        }
        long dbTimestamp = getDatabaseTimestamp();

        if (cacheTimestamp != -1 && cacheTimestamp >= dbTimestamp) {
            logger.info("Hierarchy cache is fresh. Loading from file.");
            loadCache(cacheFile);
        } else {
            logger.info("Hierarchy cache is stale or missing. Rebuilding from database.");
            rebuildFromDB();
            saveCache();
        }

        startPeriodicSave();
        initialized = true;
    }

    public synchronized void shutdown() {
        if (saveTaskFuture != null) {
            saveTaskFuture.cancel(false);
            saveTaskFuture = null;
        }
        saveCache();
    }

    private void rebuildFromDB() {
        descendants.clear();
        ancestors.clear();

        String query = format("SELECT attribute_name, parent, child FROM %s.%s", SchemaConstants.AUTH_KEYSPACE_NAME, AuthKeyspace.ATTRIBUTE_EDGES);
        UntypedResultSet results = QueryProcessor.executeInternal(query);

        for (UntypedResultSet.Row row : results) {
            String attributeName = row.getString("attribute_name");
            String parent = row.getString("parent");
            String child = row.getString("child");
            addEdge(attributeName, parent, child);
        }
    }

    private long getDatabaseTimestamp() {
        try {
            String query = format("SELECT last_modified FROM %s.%s WHERE key = '%s'",
                                  SchemaConstants.AUTH_KEYSPACE_NAME,
                                  AuthKeyspace.HIERARCHY_METADATA,
                                  HIERARCHY_METADATA_KEY);
            UntypedResultSet result = QueryProcessor.executeInternal(query);

            if (result.isEmpty()) return -1;

            Date timestamp = result.one().getTimestamp("last_modified");    
            return timestamp == null ? -1 : timestamp.getTime();
        
        } catch (Exception e) {
            logger.error("Failed to read hierarchy metadata timestamp", e);
            return -1;
        }
    }

    private void loadCache(Path cacheFile) {
        try (Input input = new Input(Files.newInputStream(cacheFile))) {
            Map<ImmutablePair<String, String>, Set<String>> loadedDescendants = (Map<ImmutablePair<String, String>, Set<String>>) kryo.readObject(input, ConcurrentHashMap.class);
            Map<ImmutablePair<String, String>, Set<String>> loadedAncestors = (Map<ImmutablePair<String, String>, Set<String>>) kryo.readObject(input, ConcurrentHashMap.class);
            descendants.clear();
            ancestors.clear();
            descendants.putAll(loadedDescendants);
            ancestors.putAll(loadedAncestors);
        } catch (Exception e) {
            logger.error("Failed to load hierarchy cache from file. Rebuilding from database.", e);
            rebuildFromDB();
        }
    }

    public synchronized void saveCache() {
        if (!isDirty) return;

        Path cacheFile = getCacheFile();
        try (Output output = new Output(Files.newOutputStream(cacheFile))) {
            kryo.writeObject(output, descendants);
            kryo.writeObject(output, ancestors);
            isDirty = false;
            logger.info("Successfully saved hierarchy cache to {}", cacheFile.toAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to save hierarchy cache to file", e);
        }
    }

    private void startPeriodicSave() {
        if (saveTaskFuture == null) {
            saveTaskFuture = ScheduledExecutors.optionalTasks.scheduleWithFixedDelay(this::saveCache,
                                                                                     SAVE_INTERVAL_SECONDS,
                                                                                     SAVE_INTERVAL_SECONDS,
                                                                                     TimeUnit.SECONDS);
            logger.info("Started periodic hierarchy cache saver task.");
        }
    }

    private Path getCacheFile() {
        String dataDir = DatabaseDescriptor.getAllDataFileLocations()[0];
        return Path.of(dataDir, HIERARCHY_CACHE_FILENAME);
    }

    public boolean check(String attributeName, String potentialAncestor, String potentialDescendant) {
        Set<String> descendantList = descendants.get(ImmutablePair.of(attributeName, potentialAncestor));

        logger.info("Parent: {}, Child: {}", potentialAncestor, potentialDescendant);

        if (descendantList == null)
            return false;
        return descendantList.contains(potentialDescendant);
    }

    public synchronized void addEdge(String attributeName, String parent, String child) {
        Set<String> ancestorOfParent = new HashSet<>(ancestors.getOrDefault(ImmutablePair.of(attributeName, parent), Collections.emptySet()));
        ancestorOfParent.add(parent);

        Set<String> descendantsOfChild = new HashSet<>(descendants.getOrDefault(ImmutablePair.of(attributeName, child), Collections.emptySet()));
        descendantsOfChild.add(child);

        for (String ancestor : ancestorOfParent) descendants.computeIfAbsent(ImmutablePair.of(attributeName, ancestor), k -> new HashSet<>()).addAll(descendantsOfChild);
        for (String descendant : descendantsOfChild) ancestors.computeIfAbsent(ImmutablePair.of(attributeName, descendant), k -> new HashSet<>()).addAll(ancestorOfParent);

        isDirty = true;
    }

}
