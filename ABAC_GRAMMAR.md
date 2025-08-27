# Proposed ABAC Grammar for Cassandra

This document outlines the proposed changes to the Cassandra Query Language (CQL) grammar to support an initial implementation of Attribute-Based Access Control (ABAC).

## 1. Proposed Statements

The following statements are proposed to be added or modified in the CQL grammar to support ABAC:

*   **`CREATE ATTRIBUTE`**: A new statement to define an attribute.
*   **`ALTER ATTRIBUTE`**: A new statement to alter an existing attribute definition.
*   **`DROP ATTRIBUTE`**: A new statement to drop an existing attribute definition.
*   **`CREATE POLICY`**: A new statement to create an ABAC policy.
*   **`ALTER POLICY`**: A new statement to alter an existing ABAC policy.
*   **`DROP POLICY`**: A new statement to drop an existing ABAC policy.
*   **`ALTER USER`**: An extension to the existing `ALTER USER` statement to allow adding attributes to a user.
*   **`ALTER TABLE`**: An extension to the existing `ALTER TABLE` statement to allow adding attributes to a table.

## 2. Proposed Grammar Rules

The following sections describe the proposed grammar rules for these statements. These rules are intended to be added to the `Cql.g` ANTLR grammar file.

### 2.1. `CREATE ATTRIBUTE`

This proposed new statement defines a new attribute that can be used in ABAC policies.

**Proposed Syntax:**

```cql
CREATE ATTRIBUTE <attribute_name>
    WITH TYPE <data_type>
    [AND VALUES IN (<value1>, <value2>, ...)];
```

**Proposed Grammar:**

```antlr
createAttributeStatement returns [CreateAttributeStatement.Raw statement]
    : K_CREATE K_ATTRIBUTE attributeName=cqlIdentifier
      K_WITH K_TYPE dataType=native_type
      (K_AND K_VALUES K_IN L_PAREN values=setLiteral R_PAREN)?
    {
        $statement = new CreateAttributeStatement.Raw(attributeName, dataType, values);
    }
    ;
```

### 2.2. `ALTER ATTRIBUTE`

This proposed new statement alters an existing attribute definition. It can be used to change the data type of an attribute, and to add or remove values from the set of allowed values.

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

### 2.3. `DROP ATTRIBUTE`

This proposed new statement drops an existing attribute definition.

**Proposed Syntax:**

```cql
DROP ATTRIBUTE [IF EXISTS] <attribute_name>;
```

**Proposed Grammar:**

```antlr
dropAttributeStatement returns [DropAttributeStatement.Raw statement]
    : K_DROP K_ATTRIBUTE (K_IF K_EXISTS)? attributeName=cqlIdentifier
    {
        $statement = new DropAttributeStatement.Raw(attributeName, $K_IF != null);
    }
    ;
```

### 2.4. `CREATE POLICY`

This proposed new statement creates an ABAC policy.

**Proposed Syntax:**

```cql
CREATE POLICY [IF NOT EXISTS] <policy_name>
    ON <resource>
    IF <conditions>
    THEN <effect> <permissions>;
```

**Proposed Grammar:**

```antlr
createPolicyStatement returns [CreatePolicyStatement.Raw statement]
    : K_CREATE K_POLICY (K_IF K_NOT K_EXISTS)? policyName=cqlIdentifier
      K_ON resource=resource
      K_IF conditions=abacConditions
      K_THEN effect=( K_GRANT | K_DENY ) permissions=permissionSet
    {
        $statement = new CreatePolicyStatement.Raw(policyName, resource, conditions, effect, permissions, $K_IF != null);
    }
    ;
```

### 2.5. `ALTER POLICY`

This proposed new statement alters an existing ABAC policy.

**Proposed Syntax:**

```cql
ALTER POLICY <policy_name>
    [SET CONDITIONS = <conditions>]
    [SET EFFECT = <effect>]
    [SET PERMISSIONS = <permissions>];
```

**Proposed Grammar:**

```antlr
alterPolicyStatement returns [AlterPolicyStatement.Raw statement]
    : K_ALTER K_POLICY policyName=cqlIdentifier
      ( K_SET K_CONDITIONS K_EQ conditions=abacConditions
      | K_SET K_EFFECT K_EQ effect=( K_GRANT | K_DENY )
      | K_SET K_PERMISSIONS K_EQ permissions=permissionSet
      )+
    {
        $statement = new AlterPolicyStatement.Raw(policyName, conditions, effect, permissions);
    }
    ;
```

### 2.6. `DROP POLICY`

This proposed new statement drops an existing ABAC policy.

**Proposed Syntax:**

```cql
DROP POLICY [IF EXISTS] <policy_name>;
```

**Proposed Grammar:**

```antlr
dropPolicyStatement returns [DropPolicyStatement.Raw statement]
    : K_DROP K_POLICY (K_IF K_EXISTS)? policyName=cqlIdentifier
    {
        $statement = new DropPolicyStatement.Raw(policyName, $K_IF != null);
    }
    ;
```

### 2.7. `ALTER USER` Extension

This proposal extends the existing `ALTER USER` statement to support attributes.

**Proposed Syntax Extension:**

```cql
ALTER USER <user_name> WITH ATTRIBUTES = <map_literal>;
```

**Proposed Grammar Modification:**

The existing `alterUserStatement` rule would be modified to include an optional `WITH ATTRIBUTES` clause.

```antlr
alterUserStatement returns [AlterUserStatement.Raw statement]
    : K_ALTER K_USER user=username ( K_WITH options=userOptions (K_AND K_ATTRIBUTES K_EQ attributes=mapLiteral)? )?
    {
        $statement = new AlterUserStatement.Raw(user, options, attributes);
    }
    ;
```

### 2.8. `ALTER TABLE` Extension

This proposal extends the existing `ALTER TABLE` statement to support attributes.

**Proposed Syntax Extension:**

```cql
ALTER TABLE <table_name> WITH ATTRIBUTES = <map_literal>;
```

**Proposed Grammar Modification:**

The existing `alterTableStatement` rule would be modified to include an optional `WITH ATTRIBUTES` clause.

```antlr
alterTableStatement returns [AlterTableStatement.Raw statement]
    : K_ALTER K_TABLE name=tableName (K_WITH K_ATTRIBUTES K_EQ attributes=mapLiteral)? (alter=alterTableOperation)?
    {
        $statement = new AlterTableStatement.Raw(name, attributes, alter);
    }
    ;
```

### 2.9. Common Grammar Rules

```antlr
abacConditions returns [List<AbacCondition> conditions]
    : term=abacTerm (K_OR term2=abacTerm)*
    {
        // ... logic to handle OR conditions ...
    }
    ;

abacTerm returns [AbacCondition condition]
    : factor=abacFactor (K_AND factor2=abacFactor)*
    {
        // ... logic to handle AND conditions ...
    }
    ;

abacFactor returns [AbacCondition condition]
    : attribute=cqlIdentifier op=relationType value=term
    | L_PAREN cond=abacConditions R_PAREN
    ;
```

## 3. Proposed Lexer Tokens

The following new lexer tokens are proposed to be added:

```
K_ATTRIBUTE: 'ATTRIBUTE';
K_TYPE: 'TYPE';
K_VALUES: 'VALUES';
K_POLICY: 'POLICY';
K_ATTRIBUTES: 'ATTRIBUTES';
K_CONDITIONS: 'CONDITIONS';
K_EFFECT: 'EFFECT';
```