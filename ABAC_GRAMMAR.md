# Proposed ABAC Grammar for Cassandra (Native Implementation)

This document outlines the proposed changes to the Cassandra Query Language (CQL) grammar to support a native implementation of Attribute-Based Access Control (ABAC).

## 1. Proposed Statements

The following new statements are proposed to be added to the CQL grammar to support a comprehensive ABAC system:

*   **`CREATE ATTRIBUTE`**: A new statement to define an attribute.
*   **`ALTER ATTRIBUTE`**: A new statement to alter an existing attribute definition.
*   **`DROP ATTRIBUTE`**: A new statement to drop an existing attribute definition.
*   **`GRANT ATTRIBUTE`**: A new statement to assign an attribute value to a user or resource.
*   **`REVOKE ATTRIBUTE`**: A new statement to revoke an attribute value from a user or resource.
*   **`CREATE RULE`**: A new statement to create an ABAC policy rule.
*   **`DROP RULE`**: A new statement to drop an existing ABAC policy rule.

## 2. Proposed Grammar Rules

The following sections describe the proposed grammar rules for these new statements.

### 2.1. Attribute Management

#### `CREATE ATTRIBUTE`
Defines a new attribute in the global attribute namespace.

**Proposed Syntax:**
```cql
CREATE ATTRIBUTE [IF NOT EXISTS] <attribute_name>
    WITH TYPE <data_type>
    [AND VALUES IN (<value1>, <value2>, ...)];
```

**Proposed Grammar:**
```antlr
createAttributeStatement returns [CreateAttributeStatement.Raw statement]
    : K_CREATE K_ATTRIBUTE (K_IF K_NOT K_EXISTS)? attributeName=cqlIdentifier
      K_WITH K_TYPE dataType=native_type
      (K_AND K_VALUES K_IN L_PAREN values=setLiteral R_PAREN)?
    {
        $statement = new CreateAttributeStatement.Raw(attributeName, dataType, values, $K_IF != null);
    }
    ;
```

#### `ALTER ATTRIBUTE`
Alters an existing attribute definition.

**Proposed Syntax:**
```cql
ALTER ATTRIBUTE <attribute_name>
    [SET TYPE = <data_type>]
    [ADD VALUES = (<value1>, <value2>, ...)]
    [DROP VALUES = (<value1>, <value2>, ...)];
```

**Proposed Grammar:**
```antlr
alterAttributeStatement returns [AlterAttributeStatement.Raw statement]
    : K_ALTER K_ATTRIBUTE attributeName=cqlIdentifier
      ( K_SET K_TYPE K_EQ dataType=native_type
      | K_ADD K_VALUES K_EQ addValues=setLiteral
      | K_DROP K_VALUES K_EQ dropValues=setLiteral
      )+
    {
        $statement = new AlterAttributeStatement.Raw(attributeName, dataType, addValues, dropValues);
    }
    ;
```

#### `DROP ATTRIBUTE`
Drops an existing attribute definition.

**Proposed Syntax:**
```cql
DROP ATTRIBUTE [IF EXISTS] <attribute_name>;
```

**Proposed Grammar:**
```antlr
dropAttributeStatement:
    K_DROP K_ATTRIBUTE (K_IF K_EXISTS)? attributeName=cqlIdentifier;
```

#### `GRANT ATTRIBUTE`
Assigns an attribute value to a user or a resource.

**Proposed Syntax:**
```cql
GRANT USER ATTRIBUTE <attribute_name> = <value> TO <user_name>;
GRANT RESOURCE ATTRIBUTE <attribute_name> = <value> TO <resource_name>;
```

**Proposed Grammar:**
```antlr
grantAttributeStatement:
    K_GRANT (K_USER | K_RESOURCE) K_ATTRIBUTE attributeName=cqlIdentifier K_EQ value=term K_TO name=cqlIdentifier;
```

#### `REVOKE ATTRIBUTE`
Revokes an attribute value from a user or a resource.

**Proposed Syntax:**
```cql
REVOKE USER ATTRIBUTE <attribute_name> FROM <user_name>;
REVOKE RESOURCE ATTRIBUTE <attribute_name> FROM <resource_name>;
```

**Proposed Grammar:**
```antlr
revokeAttributeStatement:
    K_REVOKE (K_USER | K_RESOURCE) K_ATTRIBUTE attributeName=cqlIdentifier K_FROM name=cqlIdentifier;
```

### 2.2. Policy Management

#### `CREATE RULE`
Creates a new ABAC policy rule.

**Proposed Syntax:**
```cql
CREATE RULE [IF NOT EXISTS] <rule_name>
    FOR <permissions>
    [ON <resource_type>]
    OF USER ATTRIBUTE <conditions>
    [AND RESOURCE ATTRIBUTE <conditions>]
    [AND ENVIRONMENT ATTRIBUTE <conditions>]
    WITH EFFECT (GRANT | DENY);
```

**Proposed Grammar:**
```antlr
createRuleStatement:
    K_CREATE K_RULE (K_IF K_NOT K_EXISTS)? ruleName=cqlIdentifier
    K_FOR permissions=permissionSet
    (K_ON resourceType=(K_TABLE | K_KEYSPACE | ...))?
    K_OF K_USER K_ATTRIBUTE userConditions=abacConditions
    (K_AND K_RESOURCE K_ATTRIBUTE resourceConditions=abacConditions)?
    (K_AND K_ENVIRONMENT K_ATTRIBUTE envConditions=abacConditions)?
    K_WITH K_EFFECT effect=(K_GRANT | K_DENY);
```

#### `DROP RULE`
Drops an existing ABAC policy rule.

**Proposed Syntax:**
```cql
DROP RULE [IF EXISTS] <rule_name>;
```

**Proposed Grammar:**
```antlr
dropRuleStatement:
    K_DROP K_RULE (K_IF K_EXISTS)? ruleName=cqlIdentifier;
```

## 3. Proposed Lexer Tokens

```
K_RULE: 'RULE';
K_ATTRIBUTE: 'ATTRIBUTE';
K_ENVIRONMENT: 'ENVIRONMENT';
K_EFFECT: 'EFFECT';
```
