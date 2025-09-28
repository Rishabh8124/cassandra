package org.apache.cassandra.cql3.statements.schema;

import java.util.Map;
import java.util.Set;

import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.audit.AuditLogEntryType;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.terms.Maps;
import org.apache.cassandra.cql3.terms.Term;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.exceptions.RequestExecutionException;
import org.apache.cassandra.exceptions.RequestValidationException;
import org.apache.cassandra.schema.Keyspaces;
import org.apache.cassandra.schema.Keyspaces.KeyspacesDiff;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.tcm.ClusterMetadata;
import org.apache.cassandra.transport.Dispatcher;
import org.apache.cassandra.transport.Event;
import org.apache.cassandra.transport.messages.ResultMessage;

public class CreateRuleStatement extends AlterSchemaStatement
{
    private final String ruleName;
    private final Set<Permission> permissions;
    private final IResource resource;
    private final Maps.Literal userConditions;
    private final Maps.Literal resourceConditions;
    private final Maps.Literal envConditions;
    private final String effect;
    private final boolean ifNotExists;

    public CreateRuleStatement(String ruleName,
                               Set<Permission> permissions,
                               IResource resource,
                               Maps.Literal userConditions,
                               Maps.Literal resourceConditions,
                               Maps.Literal envConditions,
                               String effect,
                               boolean ifNotExists)
    {
        super("system_auth");
        this.ruleName = ruleName;
        this.permissions = permissions;
        this.resource = resource;
        this.userConditions = userConditions;
        this.resourceConditions = resourceConditions;
        this.envConditions = envConditions;
        this.effect = effect;
        this.ifNotExists = ifNotExists;
    }

    @Override
    public void authorize(ClientState state) throws RequestValidationException
    {
        state.ensureAllKeyspacesPermission(Permission.CREATE);
    }

    @Override
    public void validate(ClientState state) throws RequestValidationException
    {
    }

    private static Map<String, String> convert(Maps.Literal map)
    {
        if (map == null)
            return null;
        Map<String, String> result = new java.util.HashMap<>();
        for (org.apache.cassandra.utils.Pair<Term.Raw, Term.Raw> entry : map.entries)
        {
            result.put(entry.left.getText(), entry.right.getText());
        }
        return result;
    }

    @Override
    public ResultMessage execute(QueryState state, QueryOptions options, Dispatcher.RequestTime requestTime) throws RequestExecutionException, RequestValidationException
    {
        String insertQuery = "INSERT INTO system_auth.abac_rules (rule_name, permissions, resource_name, user_attribute_conditions, resource_attribute_conditions, environment_attribute_conditions, effect) VALUES (?, ?, ?, ?, ?, ?, ?)";
        if (ifNotExists) {
            insertQuery += " IF NOT EXISTS";
        }

        Map<String, String> userConds = convert(userConditions);
        Map<String, String> resourceConds = convert(resourceConditions);
        Map<String, String> envConds = convert(envConditions);
        String resourceName = resource != null ? resource.getName() : null;

        QueryProcessor.executeInternal(insertQuery, ruleName, permissions, resourceName, userConds, resourceConds, envConds, effect);

        return new ResultMessage.SchemaChange(schemaChangeEvent(null));
    }

    @Override
    public Keyspaces apply(ClusterMetadata metadata)
    {
        return metadata.schema.getKeyspaces();
    }

    @Override
    public AuditLogContext getAuditLogContext()
    {
        return new AuditLogContext(AuditLogEntryType.CREATE_ROLE, keyspace(), ruleName);
    }

    Event.SchemaChange schemaChangeEvent(KeyspacesDiff diff)
    {
        return new Event.SchemaChange(Event.SchemaChange.Change.UPDATED, Event.SchemaChange.Target.TABLE, keyspace(), "abac_rules");
    }

    public static final class Raw extends CQLStatement.Raw
    {
        private final ColumnIdentifier ruleName;
        private final Set<Permission> permissions;
        private final IResource resource;
        private final Maps.Literal userConditions;
        private final Maps.Literal resourceConditions;
        private final Maps.Literal envConditions;
        private final String effect;
        private final boolean ifNotExists;

        public Raw(ColumnIdentifier ruleName,
                   Set<Permission> permissions,
                   IResource resource,
                   Maps.Literal userConditions,
                   Maps.Literal resourceConditions,
                   Maps.Literal envConditions,
                   String effect,
                   boolean ifNotExists)
        {
            this.ruleName = ruleName;
            this.permissions = permissions;
            this.resource = resource;
            this.userConditions = userConditions;
            this.resourceConditions = resourceConditions;
            this.envConditions = envConditions;
            this.effect = effect;
            this.ifNotExists = ifNotExists;
        }

        public CreateRuleStatement prepare(ClientState state)
        {
            return new CreateRuleStatement(ruleName.toString(),
                                           permissions,
                                           resource,
                                           userConditions,
                                           resourceConditions,
                                           envConditions,
                                           effect,
                                           ifNotExists);
        }
    }
}