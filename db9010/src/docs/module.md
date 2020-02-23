# Module db9010
$IMAGE("90_10_rule.jpg" style="display: block; float: right")

## db9010: A JDBC Database Wrapper

The module provides a wrapper over 
[JDBC](https://en.wikipedia.org/wiki/Java_Database_Connectivity)
that makes it more Kotlin-friendly.  It's inspired by
[JetBrains/Exposed](https://github.com/JetBrains/Exposed), but
it's vastly simpler.  Indeed, it was designed with the 90/10 rule in
mind:  Often, you can get 90% of the benefit for 10% of the work
(and complexity!).

[See the package com.jovial.db9010 here.](com.jovial.db9010/index.html)

It aims to:

*  Provide type-safe access to databases
*  Handle value conversions, including to and from `null`
*  Prevent misspelling of field names and tables, by representing them as
   kotlin typed values.
*  Provide a reasonably pretty, intuitive way of expressing SQL in Kotlin.
*  Cache prepared SQL statements for performance.
*  Let the user easily access the underlying JDBC structures for more
   advanced uses (like some of the more esoteric SQL types).
*  Partially automate translation to and from JSON.
*  Not use reflection, or other techniques that make client code obscure.

This library does not try to model all of SQL, nor does it provide an
interoperability layer over different database implementations.
It just tries to take care of the more error-prone and annoying aspects
of dealing with SQL, while being small, efficient, and (hopefully) simple to
use.

There's a demo program that uses an in-memory SQL database.  It demonstrates
code a little like this:
```
object People : Table("People") {

    val id = TableColumn(this, "id", "INT AUTO_INCREMENT", Types.sqlInt)
    val name = TableColumn(this, "name", "VARCHAR(50) NOT NULL", Types.sqlString)
    val worried = TableColumn(this, "worried", "BOOLEAN", Types.sqlBoolean)

    override val primaryKeys = listOf(id)
}

fun main() {
    Database.withConnection("jdbc:h2:mem:test", "root", "") { db ->

        db.createTable(People)

        db.statement().qualifyColumnNames(false).doNotCache() +
            "CREATE INDEX " + People + "_name on " + 
            People + "(" + People.name + ")" run {}


        People.apply {
            db.insertInto(this) run { row ->
                row[name] = "Alfred E. Neuman"
                row[worried] = false
            }
        }

        val param = Parameter(Types.sqlString)
        db.select(People.columns).from(People) + 
                "WHERE " + People.name + " <> " + param run { query ->
            query[param] = "Bob Dobbs"      // Hide Bob, if he's there

            while (query.next()) {
                val idValue = query[People.id];  // It's type-safe
                println("id:  ${idValue}")
                println("name:  ${query[People.name]}")
                println("worried?  ${query[People.worried]}")
            }
        }

        db.dropTable(People)
    }
}
```

### Threading Considerations

This library assumes that each `Database` object is accessed within one
thread.  If the client code allows multiple threads to access a
`Database` object, it is up to the client code to do appropriate locking
so that only one thread can use the `Database` at a time.  This library is built
on JDBC, which is inherently synchronous.

### Logging

This library uses the `java.util.logging` module, using the
logger for the package `com.jovial.db9010`.  When an SQL 
statement is prepared, it's logged at the `FINE` logging level.
