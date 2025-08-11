# Requirements for Implementing Attribute-Based Access Control (ABAC) in Cassandra

This document outlines the high-level requirements and code changes needed to implement Attribute-Based Access Control (ABAC) in Apache Cassandra.

## 1. Core Model and Policy Engine

*   **New System Tables:**
    *   `system_auth.attributes`: To store key-value attributes for roles and resources.
        *   Schema: `(entity_type text, entity_name text, attributes map<text, text>, PRIMARY KEY (entity_type, entity_name))`
    *   `system_auth.policies`: To store ABAC policies.
        *   Schema: `(policy_id uuid, effect text, actions set<text>, condition text, PRIMARY KEY (policy_id))`
*   **New Component: Policy Evaluation Engine:**
    *   A new Java component responsible for parsing and evaluating policy conditions.
    *   This engine will take user, resource, and environment attributes as input and return an "allow" or "deny" decision.

## 2. Integration with Cassandra

*   **New `ABACAuthorizer` Class:**
    *   A new implementation of the `IAuthorizer` interface.
    *   This class will be responsible for fetching attributes, selecting relevant policies, and using the Policy Evaluation Engine to make authorization decisions.
*   **Modifications to `IAuthorizer.java`:**
    *   The interface may need to be extended to better support attribute-based checks, potentially by passing an "environment" context map to the `authorize` method.
*   **Configuration:**
    *   `conf/cassandra.yaml` will need a new option to select `ABACAuthorizer` as the authorizer.

## 3. Management and Tooling

*   **New CQL Commands:**
    *   `CREATE POLICY`, `ALTER POLICY`, `DROP POLICY`: For managing ABAC policies.
    *   `ALTER ROLE ... WITH ATTRIBUTES = {...}`: For assigning attributes to roles.
    *   `ALTER TABLE ... WITH ATTRIBUTES = {...}`: For assigning attributes to tables.
*   **CQL Grammar Changes:**
    *   `src/antlr/Cql.g` will need to be updated to include the new syntax for the commands above.
*   **New Statement Classes:**
    *   `CreatePolicyStatement.java`
    *   `AlterPolicyStatement.java`
    *   `DropPolicyStatement.java`
*   **Modifications to Existing Statement Classes:**
    *   `AlterRoleStatement.java`
    *   `AlterTableStatement.java`
*   **Tooling Updates:**
    *   `cqlsh` will need to be updated to support the new commands.

## 4. Testing and Documentation

*   **New Tests:**
    *   Unit tests for the Policy Evaluation Engine.
    *   Integration tests for the end-to-end ABAC flow.
    *   Dtests to verify the feature in a distributed environment.
*   **New Documentation:**
    *   Comprehensive documentation for administrators and users on how to configure and use the new ABAC feature.

## 5. Summary of Code Changes

### New Files:

*   `src/java/org/apache/cassandra/auth/ABACAuthorizer.java`
*   `src/java/org/apache/cassandra/auth/PolicyEvaluationEngine.java`
*   `src/java/org/apache/cassandra/cql3/statements/CreatePolicyStatement.java`
*   `src/java/org/apache/cassandra/cql3/statements/AlterPolicyStatement.java`
*   `src/java/org/apache/cassandra/cql3/statements/DropPolicyStatement.java`

### Modified Files:

*   `src/java/org/apache/cassandra/auth/IAuthorizer.java`
*   `conf/cassandra.yaml`
*   `src/antlr/Cql.g`
*   `src/java/org/apache/cassandra/cql3/statements/AlterRoleStatement.java`
*   `src/java/org/apache/cassandra/cql3/statements/schema/AlterTableStatement.java`
