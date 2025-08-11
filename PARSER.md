# CQL Query Parsing Flow in Cassandra

This document provides a detailed explanation of how a CQL query is parsed and processed in Apache Cassandra, from the moment a command is entered in the shell until just before the authorization checks are performed.

## 1. High-Level Overview

The parsing process transforms a raw CQL query string into a structured, validated, and executable `CQLStatement` object. This involves several key components, from the client-side shell to the server-side parsing and semantic analysis engine.

```
+---------+      +----------------+      +---------------------+      +-----------------------+
|  cqlsh  |----->|  Netty Server  |----->|  QueryProcessor.java  |----->|   ANTLR-generated     |
| (Python)|      | (transport pkg)  |      | (cql3 pkg)            |      |   Parser (from Cql.g) |
+---------+      +----------------+      +---------------------+      +-----------------------+
                                                                                |
                                                                                v
+-----------------------+      +-----------------------+      +-----------------------+
|   CQLStatement        |<-----|   RawStatement.prepare()  |<-----|      RawStatement     |
| (e.g. SelectStatement)|      | (Semantic Analysis)   |      | (e.g. SelectStatement.Raw)|
+-----------------------+      +-----------------------+      +-----------------------+
```

## 2. Step-by-Step Breakdown

Let's trace the journey of a CQL query through the system.

### Step 1: The Client (`cqlsh`)

When you type a command like `SELECT * FROM ks.tbl WHERE key = 1;` and press Enter in `cqlsh`:

1.  **`cqlsh` (bin/cqlsh.py):** This Python application is the command-line client. It takes the raw query string you entered.
2.  **Native Protocol:** It sends this string as a `QUERY` message to the Cassandra server over the native transport protocol. The client doesn't parse or understand the CQL; it simply sends the string to the server for processing.

### Step 2: The Server - Receiving the Query

1.  **Netty Server (`org.apache.cassandra.transport`):** The query arrives at the server's Netty-based transport layer. The `Message.Request#execute` method is the entry point for handling incoming requests.
2.  **`QueryProcessor` (`org.apache.cassandra.cql3.QueryProcessor`):** The request is passed to the `QueryProcessor`. The `processStatement` method is the main entry point for handling the CQL query string.

### Step 3: Lexical and Syntactic Analysis (Parsing)

This is where the raw string is converted into a structured representation.

1.  **`QueryProcessor.parseStatement()`:** This method takes the raw CQL string and the `QueryState` (which contains information about the client session, like the current keyspace).
2.  **ANTLR Grammar (`src/antlr/Cql.g`):** Cassandra uses ANTLR (ANother Tool for Language Recognition) to define the CQL grammar. The `Cql.g` file contains the formal definition of the CQL language syntax.
3.  **Lexer and Parser:** From the `Cql.g` grammar file, ANTLR generates two key Java classes:
    *   `CqlLexer`: Breaks the input string into a sequence of tokens (e.g., `SELECT`, `*`, `FROM`, `ks`, `.`, `tbl`).
    *   `CqlParser`: Takes the stream of tokens from the lexer and builds a **Parse Tree**. The parse tree is a hierarchical representation of the query that reflects its grammatical structure.

### Step 4: Building the `RawStatement`

The parse tree is a raw, syntactic representation. It needs to be converted into a more useful Java object.

1.  **`CqlParser.query()`:** The parsing process starts with the `query` rule in the grammar.
2.  **`RawStatement` Creation:** As the parser walks the parse tree, it constructs a `RawStatement` object. This is a lightweight, unprepared representation of the query. For our example, it would create a `SelectStatement.Raw` object.

### Step 5: Semantic Analysis and Preparation

This is the crucial step where the raw, syntactic statement is validated against the database schema and converted into a fully executable object.

1.  **`RawStatement.prepare()`:** The `QueryProcessor` calls the `prepare()` method on the `RawStatement` object (e.g., `SelectStatement.Raw#prepare()`).
2.  **Schema Validation:** The `prepare()` method performs semantic analysis. It uses the `ClientState` to resolve names and validate the query against the database schema. For our `SELECT` example, it would:
    *   Check if the keyspace `ks` exists.
    *   Check if the table `tbl` exists within `ks`.
    *   Validate that the columns mentioned in the `WHERE` clause (`key`) exist in the table definition.
    *   Check the data types of the values being used.
3.  **`CQLStatement` Creation:** If all checks pass, the `prepare()` method creates a fully prepared and validated `CQLStatement` object (e.g., `SelectStatement`). This object contains all the information needed for execution, including references to the table metadata and column definitions.

## 3. Example Trace: `SELECT * FROM ks.tbl WHERE key = 1;`

1.  **`cqlsh`** sends the string to the server.
2.  **`QueryProcessor.processStatement()`** receives the string.
3.  It calls **`QueryProcessor.parseStatement()`**.
4.  The **`CqlLexer`** tokenizes the string.
5.  The **`CqlParser`** builds a parse tree based on the `selectStatement` rule in `Cql.g`.
6.  A **`SelectStatement.Raw`** object is created from the parse tree.
7.  `QueryProcessor` calls **`prepare()`** on the `SelectStatement.Raw` object.
8.  Inside `prepare()`:
    *   It looks up the metadata for the keyspace `ks` and table `tbl`.
    *   It validates the `WHERE` clause, ensuring the `key` column exists and that `1` is a valid value for its type.
9.  A new, fully-prepared **`SelectStatement`** object is created and returned.

At this point, the parsing and preparation phase is complete. The resulting `SelectStatement` object is now ready to be passed to the next stage: **Authorization**. The `checkAccess()` method will be called on the statement before it is finally executed.
