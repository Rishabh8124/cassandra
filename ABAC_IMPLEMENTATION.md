# ABAC Implementation Plan for Cassandra (Native Implementation)

This document outlines the plan for a native implementation of Attribute-Based Access Control (ABAC) in Cassandra, inspired by the ASQLPLUS approach.

## 1. Core Design: Native Implementation

The implementation will involve direct modification of the Cassandra source code to build the ABAC system into the core of the database. This approach will provide a tightly integrated and potentially high-performance ABAC solution.

The key areas of modification will be:

*   **CQL Parser:** The Cassandra CQL parser (which uses ANTLR) will be extended to recognize the new ABAC-related statements defined in `ABAC_GRAMMAR.md`.
*   **System Tables:** New tables will be added to the `system_auth` keyspace to store the ABAC attributes and policies, as defined in `ABAC_TABLES.md`.
*   **Query Execution Path:** The query execution path will be modified to include a new authorization step. This step will evaluate the ABAC policies before a query is executed.

## 2. Policy Enforcement

For efficient policy evaluation, the implementation will use a tree-based data structure, such as a `PolTree`, as described in the research paper "PolTree: A Data Structure for Making Efficient Access Decisions in ABAC".

The policy enforcement process will be as follows:

1.  When a user makes a request, the system will first gather the attributes of the user, the resource being accessed, and the environment.
2.  It will then traverse the `PolTree` data structure to efficiently find the applicable policies.
3.  The conditions of the applicable policies will be evaluated against the gathered attributes.
4.  If a policy grants access, the request is allowed to proceed.
5.  If a policy denies access, the request is denied.
6.  If no policy applies, the request will be denied by default (or, alternatively, could fall back to the existing RBAC system, though the current plan is for a "pure" ABAC system).

## 3. Key Implementation Steps

1.  **Modify the CQL Grammar:** Add the new ABAC statements to the `Cql.g` ANTLR grammar file.
2.  **Implement the Statement Classes:** Create new Java classes for each of the new ABAC statements (e.g., `CreateRuleStatement`, `GrantAttributeStatement`).
3.  **Create the New System Tables:** Add the new ABAC tables to the `system_auth` keyspace schema.
4.  **Implement the Policy Evaluation Logic:** Implement the `PolTree` data structure and the logic for evaluating policies.
5.  **Integrate into the Query Execution Path:** Modify the `QueryProcessor` to call the new ABAC authorization logic before executing a statement.

This native implementation approach is a significant undertaking, but it will result in a powerful and efficient ABAC system that is deeply integrated into Cassandra.
