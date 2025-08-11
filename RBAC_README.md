# Role-Based Access Control (RBAC) in Cassandra - Architecture and Workflow

This document provides a detailed explanation of the existing Role-Based Access Control (RBAC) implementation in Apache Cassandra.

## 1. Introduction

Cassandra's RBAC model is designed to control access to resources based on the roles assigned to users. This document details the architecture, the flow of an access control request, and how roles and permissions are managed.

## 2. Architecture Diagram

The following diagram illustrates the high-level architecture of the RBAC system and its interaction with other Cassandra components.

```
+-----------------+      +----------------------+      +---------------------+
|   CQL Client    |----->|  QueryProcessor.java |----->|  Statement Classes  |
+-----------------+      +----------------------+      +---------------------+
       (CQL Query)                  |                      (e.g., SelectStatement)
                                    |                              |
                                    v                              v
+-------------------------+      +----------------------+      +---------------------+
| IAuthenticator          |<-----|      ClientState     |----->|    IAuthorizer      |
| (PasswordAuthenticator) |      +----------------------+      | (CassandraAuthorizer) |
+-------------------------+                                    +---------------------+
       (Authenticates User)                                              |
                                                                         v
                                                      +------------------------------------+
                                                      |      IRoleManager / IAuthorizer    |
                                                      |    (CassandraRoleManager /         |
                                                      |     CassandraAuthorizer)           |
                                                      +------------------------------------+
                                                                 |           ^
                                                                 |           | (Roles & Permissions)
                                                                 v           |
                                                      +------------------------------------+
                                                      |      system_auth Keyspace          |
                                                      |------------------------------------|
                                                      | - roles                            |
                                                      | - role_members                     |
                                                      | - role_permissions                 |
                                                      +------------------------------------+
```

### Components:

*   **QueryProcessor.java**: The entry point for all CQL queries. It parses the query and passes it to the appropriate statement class. (Located in `src/java/org/apache/cassandra/cql3/`)
*   **Statement Classes**: (e.g., `SelectStatement.java`, `ModificationStatement.java`) These classes represent the parsed CQL queries and are responsible for their execution. (Located in `src/java/org/apache/cassandra/cql3/statements/`)
*   **IAuthenticator**: The interface for authenticating users. `PasswordAuthenticator` is the default implementation. (Located in `src/java/org/apache/cassandra/auth/`)
*   **ClientState**: Represents the state of a client connection, including the authenticated user. (Located in `src/java/org/apache/cassandra/service/`)
*   **IAuthorizer**: The interface for authorizing access to resources. `CassandraAuthorizer` is the default implementation. (Located in `src/java/org/apache/cassandra/auth/`)
*   **IRoleManager**: The interface for managing roles. `CassandraRoleManager` is the default implementation. (Located in `src/java/org/apache/cassandra/auth/`)
*   **system_auth Keyspace**: Contains the tables that store role and permission data.

## 3. Detailed Access Control Flow

Here is a step-by-step explanation of how an access control decision is made for a CQL query in the RBAC system.

### Step 1: Command Execution and Parsing

1.  A user executes a CQL query (e.g., `SELECT * FROM my_keyspace.my_table;`) through a client.
2.  The query is received by `QueryProcessor.java`.
3.  The query is parsed using ANTLR (grammar defined in `src/antlr/Cql.g`), and an appropriate statement object is created (e.g., `SelectStatement`).

### Step 2: Authentication

1.  Before executing the statement, the user must be authenticated. The `ClientState` object for the connection calls the configured `IAuthenticator` (e.g., `PasswordAuthenticator`).
2.  The authenticator verifies the user's credentials.
3.  If authentication is successful, an `AuthenticatedUser` object is created and stored in the `ClientState`. This object contains the user's name, which is their primary role.

### Step 3: Authorization (RBAC)

1.  The statement's `checkAccess()` method is called, which in turn calls `IAuthorizer.authorize()`.
2.  The default `CassandraAuthorizer.authorize()` method performs the following steps:

    a. **Fetch Roles:**
        *   It gets the `AuthenticatedUser` from the `ClientState`.
        *   It calls `Roles.getRoleDetails(user.getPrimaryRole())` to get the full set of roles granted to the user, including inherited roles.
        *   This information is retrieved from the `RolesCache`. If not in the cache, the `CassandraRoleManager` queries the `system_auth.roles` and `system_auth.role_members` tables.

    b. **Fetch Permissions:**
        *   For each role the user has, the `CassandraAuthorizer` checks the permissions for that role on the resource being accessed.
        *   This is done by calling `permissionsCache.getPermissions(user, resource)`.
        *   If the permissions are not in the cache, the `CassandraAuthorizer` queries the `system_auth.role_permissions` table.

    c. **Access Decision:**
        *   The `CassandraAuthorizer` combines all the permissions the user has on the resource (from all their roles).
        *   It then checks if the required permission for the current operation is in the set of granted permissions.
        *   If the permission is present, the `authorize()` method returns, and the query execution proceeds.
        *   If the permission is not present, the `authorize()` method throws an `UnauthorizedException`, and the query fails.

## 4. Role and Permission Creation (RBAC Policies)

In RBAC, "policies" are created by granting permissions to roles and granting roles to other roles.

### Creating Roles

Roles are created with the `CREATE ROLE` command.
```cql
CREATE ROLE data_analyst WITH LOGIN = true;
```
This is handled by `CreateRoleStatement.java` (`o.a.c.cql3.statements.CreateRoleStatement`).

### Granting Permissions to Roles

Permissions are granted to roles using the `GRANT` command.
```cql
GRANT SELECT ON KEYSPACE my_keyspace TO data_analyst;
```
This is handled by `GrantPermissionsStatement.java` (`o.a.c.cql3.statements.GrantPermissionsStatement`).

### Granting Roles to Roles

Roles can be granted to other roles, creating a hierarchy.
```cql
CREATE ROLE senior_analyst;
GRANT data_analyst TO senior_analyst;
```
This is handled by `GrantRoleStatement.java` (`o.a.c.cql3.statements.GrantRoleStatement`). A user with the `senior_analyst` role will inherit all the permissions of the `data_analyst` role.

## 5. Code and File References

*   **CQL Grammar**: `src/antlr/Cql.g`
*   **Query Processing**: `src/java/org/apache/cassandra/cql3/QueryProcessor.java`
*   **Authentication**:
    *   `src/java/org/apache/cassandra/auth/IAuthenticator.java`
    *   `src/java/org/apache/cassandra/auth/PasswordAuthenticator.java`
    *   `src/java/org/apache/cassandra/auth/AuthenticatedUser.java`
*   **Authorization**:
    *   `src/java/org/apache/cassandra/auth/IAuthorizer.java`
    *   `src/java/org/apache/cassandra/auth/CassandraAuthorizer.java`
*   **Role Management**:
    *   `src/java/org/apache/cassandra/auth/IRoleManager.java`
    *   `src/java/org/apache/cassandra/auth/CassandraRoleManager.java`
*   **Caching**:
    *   `src/java/org/apache/cassandra/auth/RolesCache.java`
    *   `src/java/org/apache/cassandra/auth/PermissionsCache.java`
*   **Data Storage**: The `system_auth` keyspace.
