package com.jovial.db9010

import java.sql.PreparedStatement


/**
 * An SQL delete statement.  See [Database.deleteFrom].
 */
class DeleteStatement private constructor(private val paramSetter : ParameterSetterImpl)
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
         * the statment.  See [Database.deleteFrom].
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
