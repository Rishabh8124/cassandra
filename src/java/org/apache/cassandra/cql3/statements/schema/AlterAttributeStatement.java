package org.apache.cassandra.cql3.statements.schema;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.audit.AuditLogEntryType;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.terms.Term;
import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.CQL3Type;
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

public class AlterAttributeStatement extends AlterSchemaStatement
{
    private final String attributeName;
    private final CQL3Type.Raw type;
    private final List<Term.Raw> valuesToAdd;
    private final List<Term.Raw> valuesToDrop;

    public AlterAttributeStatement(String attributeName, CQL3Type.Raw type, List<Term.Raw> valuesToAdd, List<Term.Raw> valuesToDrop)
    {
        super("system_auth");
        this.attributeName = attributeName;
        this.type = type;
        this.valuesToAdd = valuesToAdd;
        this.valuesToDrop = valuesToDrop;
    }

    @Override
    public void authorize(ClientState state) throws RequestValidationException
    {
        state.ensureAllKeyspacesPermission(Permission.ALTER);
    }

    @Override
    public void validate(ClientState state) throws RequestValidationException
    {
    }

    @Override
    public ResultMessage execute(QueryState state, QueryOptions options, Dispatcher.RequestTime requestTime) throws RequestExecutionException, RequestValidationException
    {
        if (type != null) {
            String updateQuery = String.format("UPDATE system_auth.attribute_definitions SET attribute_type = '%s' WHERE attribute_name = '%s'", type.toString(), attributeName);
            QueryProcessor.executeInternal(updateQuery);
        }

        if (valuesToAdd != null && !valuesToAdd.isEmpty()) {
            Set<String> toAdd = valuesToAdd.stream().map(Term.Raw::getText).collect(Collectors.toSet());
            String updateQuery = String.format("UPDATE system_auth.attribute_definitions SET allowed_values = allowed_values + ? WHERE attribute_name = '%s'", attributeName);
            QueryProcessor.executeInternal(updateQuery, toAdd);
        }

        if (valuesToDrop != null && !valuesToDrop.isEmpty()) {
            Set<String> toDrop = valuesToDrop.stream().map(Term.Raw::getText).collect(Collectors.toSet());
            String updateQuery = String.format("UPDATE system_auth.attribute_definitions SET allowed_values = allowed_values - ? WHERE attribute_name = '%s'", attributeName);
            QueryProcessor.executeInternal(updateQuery, toDrop);
        }

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
        return new AuditLogContext(AuditLogEntryType.ALTER_ROLE, keyspace(), attributeName);
    }

    Event.SchemaChange schemaChangeEvent(KeyspacesDiff diff)
    {
        return new Event.SchemaChange(Event.SchemaChange.Change.UPDATED, Event.SchemaChange.Target.TABLE, keyspace(), "attribute_definitions");
    }

    public static final class Raw extends CQLStatement.Raw
    {
        private final ColumnIdentifier name;
        private final CQL3Type.Raw type;
        private final List<Term.Raw> valuesToAdd;
        private final List<Term.Raw> valuesToDrop;

        public Raw(ColumnIdentifier name, CQL3Type.Raw type, List<Term.Raw> valuesToAdd, List<Term.Raw> valuesToDrop)
        {
            this.name = name;
            this.type = type;
            this.valuesToAdd = valuesToAdd;
            this.valuesToDrop = valuesToDrop;
        }

        public AlterAttributeStatement prepare(ClientState state)
        {
            String attributeName = name.toString();
            return new AlterAttributeStatement(attributeName, type, valuesToAdd, valuesToDrop);
        }
    }
}