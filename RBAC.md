# Role-Based Access Control (RBAC) in Cassandra

This document provides a detailed explanation of the Role-Based Access Control (RBAC) implementation in Apache Cassandra, including its architecture, core components, and the code-level mechanics of how it functions.

## 1. Introduction

Cassandra's RBAC model is designed to control access to database resources based on the roles assigned to users. This provides a powerful and flexible way to manage permissions within a Cassandra cluster. This document will guide you through the architecture, the flow of an access control request, and how roles and permissions are managed.

## 2. Core Components

The RBAC system in Cassandra is comprised of several key components that work together to provide a robust security framework.

### 2.1. `system_auth` Keyspace

The `system_auth` keyspace is the backbone of the RBAC system. It stores all the necessary information about roles, permissions, and their relationships. The key tables are:

*   **`roles`**: This table stores the list of all roles, whether they can log in, and whether they are a superuser. It also contains a `member_of` column that lists all the roles that this role inherits from.
*   **`role_members`**: This table defines the role hierarchy, mapping roles to the members that belong to them.
*   **`role_permissions`**: This table stores the permissions granted to each role on a specific resource.
*   **`resource_role_index`**: An index table to allow looking up which roles have permissions on a given resource.

### 2.2. Key Interfaces and Classes

*   **`IAuthenticator`**: The interface for authenticating users. The default implementation is `PasswordAuthenticator`.
*   **`IAuthorizer`**: The interface for authorizing access to resources. The default implementation is `CassandraAuthorizer`.
*   **`IRoleManager`**: The interface for managing roles. The default implementation is `CassandraRoleManager`.
*   **`ClientState`**: Represents the state of a client connection, including the authenticated user.
*   **`QueryProcessor`**: The entry point for all CQL queries.

## 3. Access Control Flow

Here is a step-by-step explanation of how an access control decision is made for a CQL query in the RBAC system.

### Step 1: Query Execution and Parsing

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

### The `authorize` Method in Detail

The `authorize(AuthenticatedUser user, IResource resource)` method in `CassandraAuthorizer.java` is the core of the authorization process. It is called by the `checkAccess()` method of a statement (e.g., `SelectStatement`) to determine if a user has the necessary permissions to access a resource.

Here is a step-by-step breakdown of its logic:

1.  **Check for Superuser:** The method first checks if the user is a superuser using `user.isSuper()`. If the user is a superuser, they have all applicable permissions on the resource, so the method returns `resource.applicablePermissions()` and the authorization check is complete.

2.  **Initialize Permissions Set:** If the user is not a superuser, an empty `EnumSet` called `permissions` is created to store the permissions that the user has on the resource.

3.  **Get User's Roles:** The method then retrieves all the roles granted to the user, including inherited roles, by calling `user.getRoleDetails()`. This information is typically retrieved from the `RolesCache` for performance.

4.  **Iterate Through Roles and Collect Permissions:** The method iterates through each of the user's roles. For each role, it calls the `addPermissionsForRole()` helper method.

5.  **`addPermissionsForRole()` Method:** This method takes the `permissions` set, the resource, and the current role as input. It then:
    *   Queries the `system_auth.role_permissions` table to get the permissions for the current role on the specified resource. This is done using a prepared statement (`authorizeRoleStatement`) for efficiency.
    *   If any permissions are found, they are added to the `permissions` set.

6.  **Return Permissions:** After iterating through all the user's roles, the `authorize` method returns the `permissions` set, which now contains all the permissions the user has on the resource, both directly and through role inheritance.

7.  **Exception Handling:** The entire process is wrapped in a `try-catch` block. If any `RequestExecutionException` or `RequestValidationException` occurs during the process, it is caught, and an `UnauthorizedException` is thrown.

The set of permissions returned by the `authorize` method is then checked against the required permission for the operation. If the required permission is in the set, the operation is allowed to proceed. Otherwise, an `UnauthorizedException` is thrown.

## 4. Query Execution Flow in `QueryProcessor.java`

To understand how RBAC is enforced, it's helpful to look at the control flow within `QueryProcessor.java`. This class is the entry point for all CQL queries and orchestrates the process of parsing, preparing, and executing them.

The primary entry point for a client request is the `process(String queryString, ...)` method. Here is a step-by-step breakdown of the control flow:

1.  **`process(String queryString, ...)`**: This is the main entry point for processing a CQL query.
    *   **Input**: The `queryString` variable holds the raw CQL query sent by the user.
    *   **Action**: It immediately calls the `parse` method to begin the process of turning the query string into an executable statement.

2.  **`parse(String queryString, ...)`**: This method is responsible for parsing the query string.
    *   **Action**: It calls `getStatement` to handle the parsing logic.

3.  **`getStatement(String queryStr, ...)`**: This method performs the core parsing logic.
    *   **Action**: It uses the ANTLR-based `CQLFragmentParser` to parse the `queryStr` into a `CQLStatement.Raw` object. This is a raw, unprepared representation of the query.
    *   **Action**: It then calls the `prepare(ClientState)` method on the raw statement. This crucial step validates the statement, resolves table and function names, and prepares it for execution, resulting in a fully-formed `CQLStatement` object.

4.  **`process(CQLStatement statement, ...)`**: After the statement is parsed and prepared, control returns to this method.
    *   **Action**: It prepares the query options and then calls `processStatement` to execute the query.

5.  **`processStatement(CQLStatement statement, ...)`**: This is the central method for executing a prepared statement.
    *   **`statement.authorize(clientState)`**: Before execution, this method is called to ensure the user has the necessary permissions to perform the requested operation. This is where the RBAC checks happen.
    *   **`statement.validate(clientState)`**: Next, this method is called to validate the query. This includes checks like ensuring the keyspace and table exist, the correct number of bind variables are provided, and the query is semantically correct.
    *   **`statement.execute(queryState, options, requestTime)`**: Finally, this method is called to execute the query. The actual implementation of `execute` is in the specific statement class (e.g., `SelectStatement`, `ModificationStatement`). This is where the database is actually read from or written to.

In summary, the control flow within `QueryProcessor.java` is as follows:

`process` (entry point) -> `parse` -> `getStatement` (parsing) -> `prepare` (validation and preparation) -> `processStatement` (authorization and execution) -> `statement.execute` (actual database operation).

## 5. Role and Permission Management

In RBAC, "policies" are created by granting permissions to roles and granting roles to other roles.

### Creating Roles

Roles are created with the `CREATE ROLE` command.

```cql
CREATE ROLE data_analyst WITH LOGIN = true;
```

This is handled by `CreateRoleStatement.java`. The `execute()` method in this class calls `DatabaseDescriptor.getRoleManager().createRole()`, which inserts a new row into the `system_auth.roles` table.

### Granting Permissions to Roles

Permissions are granted to roles using the `GRANT` command.

```cql
GRANT SELECT ON KEYSPACE my_keyspace TO data_analyst;
```

This is handled by `GrantPermissionsStatement.java`. The `execute()` method calls `DatabaseDescriptor.getAuthorizer().grant()`, which in turn calls `CassandraAuthorizer.grant()`. This method adds the specified permissions to the `system_auth.role_permissions` table.

### Granting Roles to Roles

Roles can be granted to other roles, creating a hierarchy.

```cql
CREATE ROLE senior_analyst;
GRANT data_analyst TO senior_analyst;
```

This is handled by `GrantRoleStatement.java`. The `execute()` method calls `DatabaseDescriptor.getRoleManager().grantRole()`. This method does two things:

1.  It adds the `senior_analyst` role to the `member_of` set in the `data_analyst` role's row in the `system_auth.roles` table.
2.  It inserts a new row into the `system_auth.role_members` table, with `role` = `senior_analyst` and `member` = `data_analyst`.

A user with the `senior_analyst` role will inherit all the permissions of the `data_analyst` role.

## 6. Caching

To improve performance, Cassandra's RBAC system uses two caches:

*   **`RolesCache`**: Caches the roles for each user, including inherited roles. This avoids repeated queries to the `system_auth.roles` and `system_auth.role_members` tables. The cache is populated by `CassandraRoleManager.bulkLoader()`.
*   **`PermissionsCache`**: Caches the permissions for each user on each resource. This avoids repeated queries to the `system_auth.role_permissions` table. The cache is populated by `CassandraAuthorizer.bulkLoader()`.

These caches are invalidated when roles or permissions are changed.

## 7. Configuration

To enable and configure RBAC in Cassandra, you need to set the following properties in `cassandra.yaml`:

*   **`authenticator`**: Set to `PasswordAuthenticator` to enable username/password authentication.
*   **`authorizer`**: Set to `CassandraAuthorizer` to enable RBAC.
*   **`role_manager`**: Set to `CassandraRoleManager` to enable role management.

## 8. Key Files and Code Reference

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
*   **CQL Statements**:
    *   `src/java/org/apache/cassandra/cql3/statements/CreateRoleStatement.java`
    *   `src/java/org/apache/cassandra/cql3/statements/GrantRoleStatement.java`
    *   `src/java/org/apache/cassandra/cql3/statements/GrantPermissionsStatement.java`
