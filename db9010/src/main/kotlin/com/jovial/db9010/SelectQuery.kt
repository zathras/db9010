package com.jovial.db9010

import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * An SQL select query.  See [Database.select].
 */
class SelectQuery private constructor(
    columns: List<Column<*>>,
    private val parameters : List<Parameter<*>>,
    columnIndex: Map<Column<*>, Int>,
    private val statement: PreparedStatement,
    private val forUpdate: Boolean,
    private val paramSetter : ParameterSetterImpl
) : ResultsHolder(columns, columnIndex), ParameterSetter by paramSetter {
    // A SelectQuery can set columns when it's forUpdate(), but we don't use
    // ColumnSetter.  That's because a SelectQuery sets the columns on a ResultSet,
    // not on a PreparedStatement (like insert and update).

    /**
     * Key used for caching select queries by [Database].
     */
    data class Key (private val columns: List<Column<*>>, private val tables : List<Table>,
                    private val forUpdate: Boolean, private val text : String)

    private val columnByJsonName by lazy { columns.associateBy { it.jsonName } }
    private var queryHasRun = false
    private var closed = false

    /**
     * A builder for a [SelectQuery].  See [Database.select].
     */
    class Builder(private val db: Database, private val columns: List<Column<*>>)
            : TextStatementBuilder<Key>() {
        private var tables: List<Table>? = null
        private val tablesNotNull get() = tables ?: throw IllegalStateException("from() not called")
        private var forUpdate : Boolean = false
        private var text = ""

        /**
         * Specify the tables for the FROM clause of this SELECT statement.  Must
         * be called exactly once per statement.
         */
        fun from(vararg tables: Table): Builder {
            if (this.tables != null) {
                throw IllegalStateException("from() called twice")
            }
            this.tables = tables.toList()   // Need a list, not an array, for SelectKey's comparison
            return this
        }

        /**
         * Sets this select statement as FOR UPDATE, thereby enabling calls to
         * [SelectQuery.update], [SelectQuery.delete] and [SelectQuery.insert].
         */
        fun forUpdate() : Builder {
            forUpdate = true
            return this
        }

        /**
         * Runs [body] with a reference to this query.  The [body] should set any needed
         * [Parameter] values using the appropriate setters, then execute the query by
         * calling [SelectQuery.next] or [SelectQuery.nextOrFail].  It can supsequently fetch
         * results by calling the appropriate setters.
         */
        infix fun <T> run(body: (SelectQuery) -> T): T {
            text = textBuilder.toString()
            return db.selectStatements.withStatement(this) { stmt ->
                val columnIndex = columns.indices.associateBy({ i -> columns[i] }, { i -> i+1 })
                val query = SelectQuery(columns, parameters, columnIndex, stmt, forUpdate, ParameterSetterImpl())
                try {
                    body(query)
                } finally {
                    query.close()
                }
            }
        }

        override val statementKey: Key get() = Key(columns, tablesNotNull, forUpdate, text)

        override fun prepareStatement(): PreparedStatement {
            val sql = StringBuilder()
            sql.append("SELECT ")
            appendCommaList(sql, columns, { col -> col.qualifiedName })
            sql.append(" FROM ")
            appendCommaList(sql, tablesNotNull.toList()) { t: Table -> t.tableName }
            if (text != "") {
                sql.append(" ")
                sql.append(text)
            }
            if (forUpdate) {
                sql.append(" FOR UPDATE")
            }
            val s = sql.toString()
            dbLogger.fine(s)
            if (forUpdate) {
                return db.connection.prepareStatement(s,
                    ResultSet.TYPE_SCROLL_SENSITIVE,
                    ResultSet.CONCUR_UPDATABLE)
            } else {
                return db.connection.prepareStatement(s)
            }
        }
    }


    private fun checkForUpdate() {
        if (!forUpdate) throw IllegalStateException("Select is not forUpdate()")
    }

    // internal
    override val result : ResultSet get() {
        if (!queryHasRun) throw IllegalStateException("Query not yet run - call next()")
        if (closed) throw IllegalStateException("Statement closed")
        return resultOrExecute
    }

    private val resultOrExecute: ResultSet by lazy {
        assert(!queryHasRun)
        queryHasRun = true
        paramSetter.setParameters(parameters, statement)
        statement.executeQuery()
    }

    // internal
    override fun close() {
        if (queryHasRun) {
            super.close()
        }
        closed = true
    }

    override fun <T> setFromLambda(p: Parameter<in T>, setter: (Int, PreparedStatement) -> Unit) {
        if (queryHasRun) throw IllegalStateException("Query already run")
        paramSetter.setFromLambda(p, setter)
    }

    /**
     * Update the column [c] with a new value.
     * The update is written to the database with [update].
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     */
    operator fun<T> set(c: TableColumn<in T>, value: T) =
        setFromLambda(c) { s, i -> c.adapter.update(s, i, value) }

    /**
     * Update the column [c] with a new value, using a code body that takes
     * a [ResultSet] and a column index.
     * The update is written to the database with [update].
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     */
    fun<T> setFromLambda(c: TableColumn<in T>, body: (ResultSet, Int) -> Unit)
        = setFromLambdaInternal(c, body)

    // Takes Column instead of TableColumn so that it is callable from setFromJson.
    // JDBC/SQL won't allow expression columns in a SELECT FOR UPDATE, so an attempt
    // to list a Column that isn't a TableColumn would have been caught already, when
    // the statement was prepared.
    private fun<T> setFromLambdaInternal(c: Column<in T>, body: (ResultSet, Int) -> Unit){
        checkForUpdate()
        val i = columnIndex[c]
        if (i == null) {
            throw IllegalArgumentException("$c not found in $columns")
        }
        body(result, i)
    }

    /**
     * Update the columns named in the JSON-friendly [jsonValue]'s keys to their
     * corresponding values.  See also [Types.sqlObject].
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     */
    fun setFromJson(jsonValue: Map<String, Any?>) {
        jsonValue.forEach { key, value ->
            val c : Column<*> = columnByJsonName[key] ?:
                    throw IllegalArgumentException("Column $key not found in ${columnByJsonName}")
            setFromLambdaInternal(c) { rs, i ->
                if (value == null) {
                    Types.sqlObject.nullable(c.adapter.javaSqlTypesTypeOrFail).update(rs, i, value)
                } else {
                    Types.sqlObject.update(rs, i, value)
                }
            }
        }
    }


    /**
     * Move the select cursor to the next row in the query.  Gives true if there is a next row,
     * false otherwise.  See [ResultSet.next].
     */
    fun next() : Boolean = resultOrExecute.next()   // Runs query if needed

    /**
     * Move the select cursor to the next row, or fail with an IllegalStateException if there
     * is not a next row.  See [next].
     */
    fun nextOrFail() {
        if (!next()) {
            throw IllegalStateException("expected result not found")
        }
    }

    /**
     * Delete the currently selected row.  The query must have been marked as
     * FOR UPDATE with [Builder.forUpdate]
     */
    fun delete() {
        checkForUpdate()
        return result.deleteRow()
    }

    /**
     * Update the currently selected row.  with values set by this query's [TableColumn] setters.
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     */
    fun update() {
        checkForUpdate()
        result.updateRow()
    }

    /**
     * Insert a new row with the values set in [body], using the [TableColumn] setters of this query.
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     */
    fun insert(body: () -> Unit) {
        checkForUpdate()
        resultOrExecute.moveToInsertRow()
        body()
        resultOrExecute.insertRow()
    }

    /**
     * Insert a new row with the values set by [jsonValue], using [setFromJson].
     * The query must have been marked as FOR UPDATE with [Builder.forUpdate]
     * See also [Types.sqlObject].
     */
    fun insertFromJson(jsonValue: Map<String, Any?>) {
        resultOrExecute.moveToInsertRow()
        setFromJson(jsonValue)
        resultOrExecute.insertRow()
    }
}
