
package com.jovial.db9010

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties
import java.util.logging.Logger


// A small, lightweight wrapper around SQL data types.  See the
// overview in src/docs/module.md and the package overview in
// src/docs/com.jovial.db9010/package.md

internal val dbLogger = Logger.getLogger("com.jovial.db9010")

/**
 * Represents a database that we connect to using JDBC.  This is the main
 * entry point in this library for operations on a database.
 *
 * Usage example:
 * ```
 *     Database.withConnection("jdbc:h2:mem:test", "root", "") { db ->
 *         various operations on db
 *     }
 * ```
 *
 */
class Database private constructor(val connection: Connection) {

    internal val insertStatements = StatementCache<InsertStatement.Key>("insert")
    internal val selectStatements = StatementCache<SelectQuery.Key>("select")
    internal val updateStatements = StatementCache<UpdateStatement.Key>("update")
    internal val deleteStatements = StatementCache<DeleteStatement.Key>("delete")
    internal val genericStatements = StatementCache<GenericStatement.Key>("generic")

    companion object {
        /**
         * Execute a body of code with a new JDBC connection that is held within a `Database` instance.
         * If the client code makes that `Database` instance available to other threads, it is
         * the client's responsibility do do any needed synchronization.  When the code body exits,
         * the connection is closed, and the `Database` instance is no longer usable.
         *
         * Usage example:
         * ```
         *     Database.withConnection("jdbc:h2:mem:test", "root", "") { db ->
         *         various operations on db
         *     }
         * ```
         */
        fun withConnection(url: String, user: String, password: String, body: (db: Database) -> Unit) {
            val connProperties = Properties()
            connProperties["user"] = user
            connProperties["password"] = password

            DriverManager.getConnection(url, connProperties).use { connection ->
                val db = Database(connection)
                try {
                    body(db)
                } finally {
                    for (s in db.insertStatements.values) s.close()
                    for (s in db.selectStatements.values) s.close()
                    for (s in db.updateStatements.values) s.close()
                    for (s in db.deleteStatements.values) s.close()
                    for (s in db.genericStatements.values) s.close()
                    connection.close()
                }
            }
        }
    }

    /**
     * Execute the code body within a JDBC transaction.  The transaction is committed
     * when the code body completes.  Set [maxRetries] to attempt the code body multiple
     * times, if an exception is thrown in [body] or while committing.
     */
    fun transaction(maxRetries: Int = 0, body: () -> Unit) {
        var retries = 0
        val wasAutoCommit = connection.autoCommit
        assert(wasAutoCommit)       // I think this is a valid assumption?
        connection.autoCommit = false
        val savePoint = connection.setSavepoint()
        while (true) {
            try {
                body()
                connection.commit()
                connection.releaseSavepoint(savePoint)
                connection.autoCommit = wasAutoCommit
                return
            } catch (e: Exception) {
                retries ++
                if (retries >= maxRetries) {
                    connection.releaseSavepoint(savePoint)
                    connection.autoCommit = wasAutoCommit
                    throw e
                }
                dbLogger.fine { "Transaction try # $retries:  $e" }
                connection.rollback(savePoint)
            }
        }
    }

    /**
     * Execute a SQL CREATE TABLE statement for [table].  Include SQL
     * "IF NOT EXISTS" if [ifNotExists] is set true.  The CREATE TABLE
     * statement is not cached.
     */
    fun createTable(table: Table, ifNotExists : Boolean = false) {
        val sql = StringBuilder()
        sql.append("CREATE TABLE ")
        if (ifNotExists) sql.append("IF NOT EXISTS ")
        sql.append("${table.tableName} (")
        appendCommaList(sql, table.columns) { c -> "${c.sqlName} ${c.sqlType}" }
        var listEmpty = table.columns.size == 0
        if (table.primaryKeys.size > 0) {
            if (!listEmpty) {
                sql.append(", ")
            }
            listEmpty = false
            sql.append("PRIMARY KEY (")
            appendCommaList(sql, table.primaryKeys) { key -> key.sqlName }
            sql.append(")")
        }
        if (table.constraintsSql != "") {
            if (!listEmpty) {
                sql.append(", ")
            }
            sql.append(table.constraintsSql)
        }
        sql.append(")")
        executeSqlUpdate(connection, sql.toString())
    }

    /**
     * Execute a SQL DROP TABLE statement for [table].  Include SQL
     * "IF EXISTS" if [ifExists] is set true.  The DROP TABLE
     * statement is not cached.
     */
    fun dropTable(table: Table, ifExists: Boolean = false) {
        val e = if (ifExists) "IF EXISTS" else ""
        executeSqlUpdate(connection, "DROP TABLE $e ${table.tableName}")
    }

    /**
     * Run an insert statement, and collect results, i.e. the values of primary key column(s).
     * For example:
     * ```
     *     val db : Database = ...
     *     pragueId = db.insertInto(this)  { row ->
     *         row[name] = "Prague"
     *     } run { r -> r[id] }    // r[id] is assigned to pragueId
     * ```
     * See [InsertStatement.Builder] for parameters and arguments that can be set.  The
     * statement is run with [InsertStatement.Builder.run].
     *
     */
    fun insertInto(table: Table, setBody: (InsertStatement) -> Unit) =
        InsertStatement.Builder(this, table, setBody)

    /**
     * Run an insert statement, but ignore any results, e.g. the values of primary keys.
     * For example:
     * ```
     *     val db : Database = ...
     *     db.insertInto(People) run { row ->
     *         row[People.name] = "Alfred E. Neuman"
     *         row[People.worried] = false
     *      }
     * ```
     * See [InsertStatement.BuilderNoResults] for parameters and arguments that can be set.
     * The statement is run with [InsertStatement.BuilderNoResults.run]
     */
    fun insertInto(table: Table) =
        InsertStatement.BuilderNoResults(this, table)

    /**
     * Run a SELECT statement over the given columns.  See
     * [SelectQuery.Builder] for parameters and arguments that can be
     * set.  The query is run within the code block given to
     * [SelectQuery.Builder.run] when that code executes [SelectQuery.next].
     *
     * Usage example:
     * ```
     *     db.select(People.columns).from(People) + "WHERE " + People.name + " <> " + param run
     *     { query ->
     *         query[param] = "Bob Dobbs"      // Hide Bob, if he's there
     *         while (query.next()) {
     *             println("name:  ${query[People.name]}")
     *             println("worried:  ${query[People.worried]}")
     *         }
     *     }
     * ```
     */
    fun select(columns: List<Column<*>>) =
        SelectQuery.Builder(this, columns)

    /**
     * Run a SELECT statement over the given columns.  See
     * [SelectQuery.Builder] for parameters and arguments that can be
     * set.  The query is run within the code block given to
     * [SelectQuery.Builder.run] when that code executes [SelectQuery.next].
     *
     * Usage example:
     * ```
     *     db.select(Users.name, Cities.name).from(Users) +
     *             " INNER JOIN "  + Cities + " ON " + Cities.id + "=" + Users.cityId +
     *             " ORDER BY " + Users.name run
     *     { r ->
     *         while (r.next()) {
     *             println("\t${r[Users.name]}\t${r[Cities.name]}")
     *         }
     *     }
     * ```
     */
    fun select(vararg columns: Column<*>) =
        SelectQuery.Builder(this, columns.toList())

    /**
     * Run an UPDATE statement on the given table.  Rows to be updated are identified
     * with calls to [ParameterSetter.set] within the code body passed to
     * [UpdateStatement.Builder.run].
     *
     * Usage example:
     * ```
     *     val idStr = Parameter(Types.sqlString)
     *     val num = db.update(Users) + "WHERE " + id + "=" + idStr run { u ->
     *         u[idStr] = "alex"   // Sets the parameter
     *         u[Users.name] = "Alexy"   // Sets the new value of the name column
     *         u[users.cityId] = pragueId    // Sets the new value of the cityId column
     *     }
     *     println("Updated $num row(s)")
     * ```
     */
    fun update(table: Table) =
        UpdateStatement.Builder(this, table)

    /**
     * Run a DELETE FROM statement for the given table.  See [DeleteStatement.Builder] for
     * setting additional arguments and parameters.  The statement is run after the code
     * body sent to [DeleteStatement.Builder.run] completes.
     *
     * Usage example:
     * ```
     *     val param = Parameter(Types.sqlString)
     *     val deleted = db.deleteFrom(Users) + "WHERE " +
     *             Users.name + " LIKE " + param run {  d ->
     *         d[param] = "%thing"
     *     }
     *     println("$deleted rows deleted.")
     * ```
     */
    fun deleteFrom(table: Table) =
        DeleteStatement.Builder(this, table)

    /**
     * Run a generic SQL statement.  See [GenericStatement.Builder] for setting
     * arguments and parameters, including the statement text.A
     *
     * Usage example:
     * ```
     *     db.statement().qualifyColumnNames(false).doNotCache() +
     *             "CREATE INDEX " + People + "_name on " +
     *             People + "(" + People.name + ")" run {}
     * ```
     */
    fun statement() = GenericStatement.Builder(this)
}

/**
 * Little class to maintain a cache of statements inside a
 * hash map.  This handles preparing statements, and closing
 * statments when they are done if [StatementBuilder.doNotCache] is set.
 * Otherwise, statements are cached forever.  It is expected that typical
 * applications will have a small number of frequently-reused statements,
 * and that parameters will be set as [Parameter] values, and not by changing
 * the statement text.
 */
internal class StatementCache<K> (
    val name: String
) {
    private val map = mutableMapOf<K, PreparedStatement>()

    val values = map.values

    fun <T> withStatement(builder: StatementBuilder<K>, body: (PreparedStatement) -> T) : T {
        val doCache = builder.doCache
        val statement = if (doCache) {
            val key = builder.statementKey
            dbLogger.finest { "Checking $name statement cache for $key"}
            map.computeIfAbsent(key, { _ -> builder.prepareStatement() })
        } else {
            builder.prepareStatement()
        }
        try {
            return body(statement)
        } finally {
            if (!doCache) {
                statement.close()
            }
        }
    }
}

/**
 * Superclass for statements that can set table columns:  [UpdateStatement] and
 * [InsertStatement].  This class maintains a list of the columns that have been
 * set, and a code block to set each value.
 */
abstract class ColumnSetter (
    private val table: Table
) {
    internal val columns = mutableListOf<TableColumn<*>>()
    internal val setters = mutableListOf<(Int, PreparedStatement) -> Unit>()

    /**
     * Set the given [column] to [value].
     */
    operator fun <T> set(column: TableColumn<in T>, value: T) =
        setFromLambda(column, {i: Int, stmt: PreparedStatement -> column.adapter.set(stmt, i, value)})

    /**
     * Set the column [c], using a code block that operates on a [PreparedStatement]
     * using the provided column index.
     */
    fun <T> setFromLambda(c: TableColumn<in T>, setter: (Int, PreparedStatement) -> Unit) {
        if (c.table != table) {
            throw IllegalArgumentException("Attempt to set column from a different table")
        }
        columns.add(c)
        setters.add(setter)
    }

    /**
     * Set zero or more columns from a JSON-friendly map of column names to values,
     * [jsonValue].  The values are set using [PreparedStatement.setObject], or, if
     * null, [PreparedStatement.setNull].  See also [Types.sqlObject].
     */
    fun setFromJson(jsonValue: Map<String, Any?>) {
        jsonValue.forEach { key, value ->
            val c = table.columnByJsonName[key] ?:
                    throw IllegalArgumentException("Column $key not found in ${table.columnByJsonName}")
            setFromLambda(c) {i: Int, stmt: PreparedStatement ->
                if (value == null) {
                    Types.sqlObject.nullable(c.adapter.javaSqlTypesTypeOrFail).set(stmt, i, value)
                } else {
                    Types.sqlObject.set(stmt, i, value)
                }
            }
        }
    }
}


/**
 * Type for statements that can set parameters:  [DeleteStatement], [UpdateStatement],
 * [SelectQuery] and [GenericStatement].  This class maintains a map of the parameters
 * that have been set to code blocks that set the parameters on a [PreparedStatement], given
 * the value index.
 */
interface ParameterSetter {

    /**
     * Set the given parameter [p] to [value].
     */
    operator fun <T> set(p: Parameter<in T>, value: T) =
        setFromLambda(p, {i: Int, stmt: PreparedStatement -> p.adapter.set(stmt, i, value)})

    /**
     * Set the given parameter [p], using a code block that operates on a JDBC [PreparedStatement]
     * using the provided value index.
     */
    fun <T> setFromLambda(p: Parameter<in T>, setter: (Int, PreparedStatement) -> Unit)
}

/**
 * Implementation of ParameterSetter.  Subclasses use delegation for this, because some
 * subclasses already have an abstract class as a supertype.
 */
internal class ParameterSetterImpl : ParameterSetter {
    /**
     * Map of [Parameter] instances to the code body that sets the given parameter.
     * This is an internal data structure that should not be accessed by client code.
     * Kotlin does not allow interface members to be internal :-(
     */
    internal val parameterSetters = mutableMapOf<Parameter<*>, (Int, PreparedStatement) -> Unit>()

    override fun <T> setFromLambda(p: Parameter<in T>, setter: (Int, PreparedStatement) -> Unit) {
        parameterSetters[p] = setter
    }

    // Check that all of the expected parameters have been set, and no attempt was
    // made to set an unexpected parameter.  This insures that the parameters in the
    // built statement are all set in the setter body passed to the builder.
    internal fun checkParameters(parameters: List<Parameter<*>>) {
        if (parameterSetters.keys != parameters.toSet()) {
            throw IllegalArgumentException("Extra parameter or parameter not found")
        }
    }

    // Set the given parameters in the given statement.  The value index is offset
    // by offset.  This is needed for the select statement, where table columns take the
    // first range of index values.
    internal fun setParameters(parameters: List<Parameter<*>>, stmt: PreparedStatement, offset: Int = 0) {
        for (i in parameters.indices) {
            val s = parameterSetters[parameters[i]]!!   // Null caught by checkParameters
            s(i+1+offset, stmt)
        }
    }
}

/**
 * Abstract superclass for objects that hold a [ResultSet]:  [SelectQuery] and [InsertStatement.Result].
 * This class maintains a map that is used to look up a desired [Column], and find it in
 * a [ResultSet].
 */
abstract class ResultsHolder internal constructor(
    protected val columns: List<Column<*>>,
    protected val columnIndex: Map<Column<*>, Int>
) {
    abstract internal val result: ResultSet

    /**
     * Get the value from column [c], as the type determined by [Column.adapter]]
     */
    operator fun<T> get(c: Column<out T>) : T =
        getFromLambda<T>(c) { r: ResultSet, i: Int -> c.adapter.get(r, i) }

    /**
     * Get the vaule from column [c], as returned by [body], which takes a [ResultSet] and
     * a column index.
     */
    fun<T> getFromLambda(c: Column<out T>, body: (ResultSet, Int) -> T) : T {
        val i = columnIndex[c]
        if (i == null) {
            throw IllegalArgumentException("$c not in result set")
        }
        return body(result, i)
    }

    /**
     * Get a JSON-friendly map of column names to column values.  The values are obtained
     * from [ResultSet.getObject].  See also [Types.sqlObject].
     */
    fun getJson() : Map<String, Any?> =
        columns.associateBy( { c -> c.jsonName }) { c ->
            getFromLambda (c) { r, i ->
                val rv = r.getObject(i)
                if (r.wasNull()) {
                    null
                } else {
                    rv
                }
            }
        }

    open internal fun close() {
        result.close()
    }
}

/**
 * Abstract superclass for the builder of all statement types.
 * See also [StatementBuilder.doNotCache]
 */
abstract class StatementBuilder <K> {
    internal var doCache = true

    abstract internal val statementKey : K;

    abstract internal fun prepareStatement() : PreparedStatement
}

/**
 * Method to set this builder so that it creates a statement that is not cached
 * by the [Database] object.  By default, statements are cached.
 */
fun<T: StatementBuilder<*>> T.doNotCache() : T {
    doCache = false
    return this
}

/**
 * Superclass for the builder of all statements that can have SQL strings
 * added to the statement body.  See [TextStatementBuilder.plus].
 */
abstract class TextStatementBuilder<K> : StatementBuilder<K>() {
    internal var qualifyColumnNames : Boolean = true
    internal val textBuilder = StringBuilder()
    internal val parameters = mutableListOf<Parameter<*>>()
}

// Covariant return types FTW!
/**
 * Add a SQL string to the statement being built.
 */
operator fun <T: TextStatementBuilder<*>> T.plus(s: String): T {
    textBuilder.append(s)
    return this
}

/**
 * Add a parameter to the statement being built.  The text "?" is added, and the
 * parameter is recorded so that the parameter's index can be determined when the
 * statement is run.
 */
operator fun <T: TextStatementBuilder<*>> T.plus(p: Parameter<*>): T {
    parameters.add(p)
    textBuilder.append("?")
    return this
}

/**
 * Add a column to the statement being built.  The column's string representation in SQL
 * is added.  See [TextStatementBuilder.qualifyColumnNames]
 */
operator fun <T: TextStatementBuilder<*>> T.plus(c: Column<*>): T {
    if (qualifyColumnNames) {
        textBuilder.append(c.qualifiedName)
    } else {
        textBuilder.append(c.sqlName)
    }
    return this
}

/**
 * Add a table to the statement being built.  The table's name is appended.
 */
operator fun <T: TextStatementBuilder<*>> T.plus(t: Table): T {
    textBuilder.append(t.tableName)
    return this
}

/**
 * Determine whether or not subsequent calls to [TextStatementBuilder.plus] with a
 * [TableColumn] argument will use the qualified name (table_name.column_name), or just
 * the column name.  If this method is not called, the default behavior is to qualify names.
 */
fun <T: TextStatementBuilder<*>> T.qualifyColumnNames(v: Boolean = true) : T {
    qualifyColumnNames = v
    return this
}


internal fun executeSqlUpdate(db: Connection, sql: String) {
    db.createStatement().use { stmt ->
        dbLogger.fine(sql)
        stmt.executeUpdate(sql)
    }
}

// Deal with the annoying fact that the last item in a comma list doesn't have a comma after it
internal fun <T> appendCommaList(sb: StringBuilder, values: List<T>, valToString: (T) -> String) {
    for (i in values.indices) {
        sb.append(valToString(values[i]))
        if (i != values.lastIndex) {
            sb.append(", ")
        }
    }
}
