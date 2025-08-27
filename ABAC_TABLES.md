# ABAC Tables for Cassandra (Native Implementation)

This document defines the new tables required to support a native implementation of Attribute-Based Access Control (ABAC) in Cassandra. These tables will be created in the `system_auth` keyspace.

## 1. Keyspace

All tables will be created in the `system_auth` keyspace.

## 2. Table Definitions

### 2.1. `attribute_definitions`

This table stores the schema for the attributes that can be used in the ABAC system.

**Purpose:**

This table defines the allowed attributes, their data types, and their possible values. This ensures that only well-defined attributes are used in policies and assigned to resources.

**Schema:**

```cql
CREATE TABLE system_auth.attribute_definitions (
    attribute_name text PRIMARY KEY,
    attribute_type text,
    allowed_values set<text>
);
```

**Columns:**

*   `attribute_name`: The unique name of the attribute (e.g., 'department', 'region', 'sensitivity').
*   `attribute_type`: The data type of the attribute (e.g., 'text', 'int', 'boolean').
*   `allowed_values`: An optional set of predefined allowed values for the attribute. If this is null, the attribute can have any value of the specified type.

### 2.2. `attributes`

This table stores the attributes for users and resources.

**Purpose:**

This table holds the key-value attributes that are assigned to users and resources. The values assigned to attributes in this table will be validated against the `attribute_definitions` table.

**Schema:**

```cql
CREATE TABLE system_auth.attributes (
    resource_type text,
    resource_name text,
    attributes map<text, text>,
    PRIMARY KEY ((resource_type, resource_name))
);
```

**Columns:**

*   `resource_type`: The type of the resource (e.g., 'user', 'table').
*   `resource_name`: The name of the resource (e.g., a username or a table name).
*   `attributes`: A map of key-value pairs representing the attributes of the resource.

### 2.3. `abac_policies`

This table stores the ABAC policy rules.

**Purpose:**

This table holds the core ABAC policies that are evaluated to make access control decisions. Each row represents a single policy.

**Schema:**

```cql
CREATE TABLE system_auth.abac_policies (
    policy_name text PRIMARY KEY,
    resource text,
    conditions list<text>,
    effect text,
    permissions set<text>
);
```

**Columns:**

*   `policy_name`: The unique name of the policy.
*   `resource`: The resource to which the policy applies (e.g., a keyspace, table, or role).
*   `conditions`: A list of conditions that must be met for the policy to be applied. Each condition is stored as a string.
*   `effect`: The effect of the policy, which can be either `GRANT` or `DENY`.
*   `permissions`: The set of permissions that are granted or denied by the policy.