# Proposal for a Native ABAC Implementation in Cassandra

## 1. Introduction

This document outlines a proposal for a native implementation of Attribute-Based Access Control (ABAC) in Apache Cassandra. The proposed changes introduce a comprehensive ABAC system that allows for fine-grained, conditional access control based on the attributes of users, resources, and the environment.

This proposal includes extensions to the Cassandra Query Language (CQL) for managing the ABAC system, as well as a set of new tables to be added to the `system_auth` keyspace to store the necessary metadata.

## 2. Proposed CQL Extensions

The following new and modified CQL statements are proposed to support the ABAC system.

### 2.1. Attribute Management

#### `CREATE ATTRIBUTE`

Defines a new attribute that can be used in ABAC policies.

**Syntax:**
```cql
CREATE (USER | RESOURCE) ATTRIBUTE <attribute_name>
    WITH TYPE <data_type>
    [AND VALUES IN (<value1>, <value2>, ...)];
```

#### `ALTER ATTRIBUTE`

Alters an existing attribute definition. It can be used to change the data type of an attribute, and to add or remove values from the set of allowed values.

**Syntax:**
```cql
ALTER ATTRIBUTE <attribute_name>
    [SET TYPE = <data_type>]
    [ADD VALUES = (<value1>, <value2>, ...)]
    [DROP VALUES = (<value1>, <value2>, ...)];
```

#### `DROP ATTRIBUTE`

Drops an existing attribute definition.

**Syntax:**
```cql
DROP (USER | RESOURCE) ATTRIBUTE [IF EXISTS] <attribute_name>;
```

#### `GRANT ATTRIBUTE`

Assigns an attribute value to a user or a resource.

**Syntax:**
```cql
GRANT USER ATTRIBUTE <attribute_name> = <value> TO <user_name>;
GRANT RESOURCE ATTRIBUTE <attribute_name> = <value> TO <resource_name>;
```

#### `REVOKE ATTRIBUTE`

Revokes an attribute value from a user or a resource.

**Syntax:**
```cql
REVOKE USER ATTRIBUTE <attribute_name> FROM <user_name>;
REVOKE RESOURCE ATTRIBUTE <attribute_name> FROM <resource_name>;
```

### 2.2. Policy Management

#### `CREATE RULE`

Creates a new ABAC policy rule.

**Syntax:**
```cql
CREATE RULE <rule_name>
    FOR <permissions>
    [ON <resource_type>]
    OF USER ATTRIBUTE <conditions>
    [AND RESOURCE ATTRIBUTE <conditions>]
    [AND ENVIRONMENT ATTRIBUTE <conditions>];
```

#### `DROP RULE`

Drops an existing ABAC policy rule.

**Syntax:**
```cql
DROP RULE <rule_name>;
```

## 3. Proposed Database Schema

The following new tables are proposed to be added to the `system_auth` keyspace to store the ABAC metadata.

### 3.1. `attribute_definitions`

This table stores the schema for the attributes that can be used in the ABAC system.

**Schema:**
```cql
CREATE TABLE system_auth.attribute_definitions (
    attribute_name text PRIMARY KEY,
    attribute_type text,
    allowed_values set<text>
);
```

**Columns:**

*   `attribute_name`: The unique name of the attribute.
*   `attribute_type`: The data type of the attribute.
*   `allowed_values`: An optional set of predefined allowed values for the attribute.

### 3.2. `user_attribute_values`

This table assigns attribute values to users.

**Schema:**
```cql
CREATE TABLE system_auth.user_attribute_values (
    user_name text,
    attribute_name text,
    attribute_value text,
    PRIMARY KEY (user_name, attribute_name)
);
```

**Columns:**

*   `user_name`: The name of the user.
*   `attribute_name`: The name of the attribute.
*   `attribute_value`: The value of the attribute for the user.

### 3.3. `resource_attribute_values`

This table assigns attribute values to resources.

**Schema:**
```cql
CREATE TABLE system_auth.resource_attribute_values (
    resource_name text,
    attribute_name text,
    attribute_value text,
    PRIMARY KEY (resource_name, attribute_name)
);
```

**Columns:**

*   `resource_name`: The name of the resource.
*   `attribute_name`: The name of the attribute.
*   `attribute_value`: The value of the attribute for the resource.

### 3.4. `abac_rules`

This table stores the ABAC policy rules.

**Schema:**
```cql
CREATE TABLE system_auth.abac_rules (
    rule_name text PRIMARY KEY,
    permissions set<text>,
    resource_type text,
    user_attribute_conditions map<text, text>,
    resource_attribute_conditions map<text, text>,
    environment_attribute_conditions map<text, text>
);
```

**Columns:**

*   `rule_name`: The unique name of the rule.
*   `permissions`: The set of permissions being granted or denied.
*   `resource_type`: The type of resource the rule applies to.
*   `user_attribute_conditions`: A map of user attributes and their required values.
*   `resource_attribute_conditions`: A map of resource attributes and their required values.
*   `environment_attribute_conditions`: A map of environment attributes and their required values.
