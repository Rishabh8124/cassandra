# ABAC Tables for Cassandra (Native Implementation)

This document defines the new tables required to support a native implementation of Attribute-Based Access Control (ABAC) in Cassandra. These tables will be created in the `system_auth` keyspace.

## 1. Keyspace

All tables will be created in the `system_auth` keyspace.

## 2. Table Definitions

### 2.1. `attribute_definitions`

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

### 2.2. `user_attribute_values`

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

### 2.3. `resource_attribute_values`

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

### 2.4. `abac_rules`

This table stores the ABAC policy rules.

**Schema:**
```cql
CREATE TABLE system_auth.abac_rules (
    rule_name text PRIMARY KEY,
    permissions set<text>,
    resource_type text,
    user_attribute_conditions map<text, text>,
    resource_attribute_conditions map<text, text>,
    environment_attribute_conditions map<text, text>,
    effect text
);
```

**Columns:**

*   `rule_name`: The unique name of the rule.
*   `permissions`: The set of permissions being granted or denied.
*   `resource_type`: The type of resource the rule applies to.
*   `user_attribute_conditions`: A map of user attributes and their required values.
*   `resource_attribute_conditions`: A map of resource attributes and their required values.
*   `environment_attribute_conditions`: A map of environment attributes and their required values.
*   `effect`: The effect of the policy, which can be either `GRANT` or `DENY`.
