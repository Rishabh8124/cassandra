package org.apache.cassandra.cql3.statements.schema;

import org.apache.cassandra.audit.AuditLogContext;
import org.apache.cassandra.audit.AuditLogEntryType;
import org.apache.cassandra.auth.IResource;
import org.apache.cassandra.auth.Permission;
import org.apache.cassandra.cql3.RoleName;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
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

public class GrantAttributeStatement extends AlterSchemaStatement
{
    private final String attributeType; // "USER" or "RESOURCE"
    private final String name;
    private final String attributeName;
    private final Term.Raw attributeValue;

    public GrantAttributeStatement(String attributeType, String name, String attributeName, Term.Raw attributeValue)
    {
        super("system_auth");
        this.attributeType = attributeType;
        this.name = name;
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
    }

    @Override
    public void authorize(ClientState state) throws RequestValidationException
    {
        state.ensureAllKeyspacesPermission(Permission.AUTHORIZE);
    }

    @Override
    public void validate(ClientState state) throws RequestValidationException
    {
    }

    @Override
    public ResultMessage execute(QueryState state, QueryOptions options, Dispatcher.RequestTime requestTime) throws RequestExecutionException, RequestValidationException
    {
        String tableName = attributeType.equalsIgnoreCase("USER") ? "user_attribute_values" : "resource_attribute_values";
        String columnName = attributeType.equalsIgnoreCase("USER") ? "user_name" : "resource_name";

        String insertQuery = String.format("INSERT INTO system_auth.%s (%s, attribute_name, attribute_value) VALUES (?, ?, ?)", tableName, columnName);
        QueryProcessor.executeInternal(insertQuery, name, attributeName, attributeValue.getText());

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
        return new AuditLogContext(AuditLogEntryType.GRANT, keyspace(), name);
    }

    Event.SchemaChange schemaChangeEvent(KeyspacesDiff diff)
    {
        String tableName = attributeType.equalsIgnoreCase("USER") ? "user_attribute_values" : "resource_attribute_values";
        return new Event.SchemaChange(Event.SchemaChange.Change.UPDATED, Event.SchemaChange.Target.TABLE, keyspace(), tableName);
    }

    public static final class Raw extends CQLStatement.Raw
    {
        private final String attributeType; // "USER" or "RESOURCE"
        private RoleName user;
        private IResource resource;
        private final ColumnIdentifier attributeName;
        private final Term.Raw attributeValue;

        public Raw(RoleName user, ColumnIdentifier attributeName, Term.Raw attributeValue)
        {
            this.attributeType = "USER";
            this.user = user;
            this.attributeName = attributeName;
            this.attributeValue = attributeValue;
        }

        public Raw(IResource resource, ColumnIdentifier attributeName, Term.Raw attributeValue)
        {
            this.attributeType = "RESOURCE";
            this.resource = resource;
            this.attributeName = attributeName;
            this.attributeValue = attributeValue;
        }

        public GrantAttributeStatement prepare(ClientState state)
        {
            String name = attributeType.equals("USER") ? user.getName() : resource.getName();
            return new GrantAttributeStatement(attributeType, name, attributeName.toString(), attributeValue);
        }
    }
}