
# Package com.jovial.db9010

## Package Documentation

In order to fluently write code as described in the
[introduction](../index.html), it's helpful to know something about
how the API is structured.  There are three main parts:  
The table and type definitions, 
the `Database` entry point, 
and the five statement
types.  In the following, these areas are illustrated with UML
diagrams

### Table and Type Definitions

[$IMAGE("../types.svg" height=100 style="display: block; float: left; border: 2px solid; margin-right: 10px")](types_uml.html)

The first step in writing a database system is normally to create
table definitions.  These classes support building up a representation
of tables, which are made up of columns.  Each column has a SQL type
that gets mapped to and from Kotlin types.  There are also query
columns that contain expressions, like "`SELECT COUNT(*)`" and so forth.

$IMAGE ("../transparent_pixel.png" width="100%" height="1")

### The Database Entry Point

[$IMAGE("../main.svg" height=100 style="display: block; float: left; border: 2px solid; margin-right: 10px")](main_uml.html)

Next, we look at `Database` class, which is the main entry point to the
system.  It lets us make a JDBC connection, and various statement types.
It has a series of methods that return different `Builder`
objects for five types of SQL statement:  `SelectQuery`, 
`DeleteStatement`, `UpdateStatement`,
`InsertStatement`, and `GenericStatement`.

$IMAGE ("../transparent_pixel.png" width="100%" height="1")


### Statements

[$IMAGE("../statements.svg" height=100 style="display: block; float: left; border: 2px solid; margin-right: 10px")](statements_uml.html)

Finally, we consider the implementation of the five statement types.
When a builder runs the client code, it give a reference to an object
representing the statement.  This can be used by the client code to
set any needed parameters, and set and get row values.

$IMAGE ("../transparent_pixel.png" width="100%" height="1")

