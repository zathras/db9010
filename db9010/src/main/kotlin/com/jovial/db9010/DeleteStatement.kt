package com.jovial.db9010

import java.sql.PreparedStatement


/**
 * An SQL delete statement.  See [Database.deleteFrom].
 */
class DeleteStatement internal constructor(internal val paramSetter : ParameterSetterImpl)
        : ParameterSetter by paramSetter {

    /**
     * Key used for caching delete statements by [Database].
     */
    data class Key(private val table: Table, private val text: String)

    /**
     * A builder for a [DeleteStatement].  See [Database.deleteFrom].
     */
    class Builder(private val db: Database, private val table: Table) : TextStatementBuilder<Key>() {

        private var text : String = ""

        /**
         * Run [setBody] to set any needed [Parameter] values on the delete statement, then execute
         * the statement.  See [Database.deleteFrom].
         */
        infix fun run(setBody: (DeleteStatement) -> Unit) : Int {
            val statement = DeleteStatement(ParameterSetterImpl())
            setBody(statement)
            statement.paramSetter.checkParameters(parameters)
            text = textBuilder.toString()
            return db.deleteStatements.withStatement(this) { stmt ->
                statement.paramSetter.setParameters(parameters, stmt)
                stmt.executeUpdate()
            }
        }

        /**
         * Gives a reference to a reusable statement object.  This allows clients to avoid the (small)
         * lookup overhead for a frequently-used delete statement.
         *
         * Usage:
         * ```
         * val intParam = Parameter(Types.sqlInt)
         * val deleteFrom = (db.deleteFrom(TestTable) + " WHERE " + TestTable.id + "=" + intParam).getReusable()
         * val id = <...>
         * val deleted = deleteFrom run {
         *     it[intParam] = id
         * }
         * ```
         * Throws [IllegalStateException] if this statement is not cacheable.
         */
        fun getReusable(): ReusableDeleteStatement {
            if (!doCache) {
                throw IllegalStateException("Statement is not cacheable")
            }
            text = textBuilder.toString()
            return db.deleteStatements.withStatement(this) { stmt ->
                ReusableDeleteStatement(stmt, parameters)
            }
        }

        override val statementKey: Key get() = Key(table, text)

        override fun prepareStatement(): PreparedStatement {
            val text = text
            val sql = StringBuilder()
            sql.append("DELETE FROM ${table.tableName}")
            if (text != "") {
                sql.append(" ")
                sql.append(text)
            }
            val s = sql.toString()
            dbLogger.fine(s)
            return db.connection.prepareStatement(s)
        }
    }
}

/**
 * A delete statement that can be stored in a variable, and used multipe times.  This saves the
 * (small) overhead of a hash table lookup that's incurred when using [Database.deleteFrom] every
 * time the same statement is run.
 */
class ReusableDeleteStatement(
    private val statement : PreparedStatement,
    private val parameters : List<Parameter<*>>
) {
    /**
     * Run [setBody] to set any needed [Parameter] values on the delete statement, then execute
     * the statement.  See [Database.deleteFrom].
     */
    infix fun run(setBody: (DeleteStatement) -> Unit) : Int {
        val ds = DeleteStatement(ParameterSetterImpl())
        setBody(ds)
        ds.paramSetter.checkParameters(parameters)
        ds.paramSetter.setParameters(parameters, statement)
        return statement.executeUpdate()
    }
}
