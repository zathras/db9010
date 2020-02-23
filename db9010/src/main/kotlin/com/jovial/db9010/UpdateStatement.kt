package com.jovial.db9010

import java.sql.PreparedStatement

/**
 * An SQL update statement.  See [Database.update].
 */
class UpdateStatement private constructor(
    table : Table,
    private val paramSetter : ParameterSetterImpl
) : ColumnSetter(table), ParameterSetter by paramSetter {

    /**
     * Key used for caching update statements by [Database].
     */
    data class Key (private val table: Table, private val columns: List<Column<*>>,
                    private val text: String)

    /**
     * A builer for [UpdateStatement].  See [Database.update].
     */
    class Builder(private val db: Database, private val table: Table) : TextStatementBuilder<Key>() {

        private var text = ""
        private val statement = UpdateStatement(table, ParameterSetterImpl())

        /**
         * Run [setBody] to set any needed [Parameter] values on the delete statement, and the
         * [TableColumn] values to be updated.  After [setBody] executes, the update statement
         * is executed, and the number of rows updated is returned.
         */
        infix fun run(setBody: (UpdateStatement) -> Unit) : Int {
            setBody(statement)
            statement.paramSetter.checkParameters(parameters)
            text = textBuilder.toString()
            return db.updateStatements.withStatement(this) { stmt ->
                for (i in statement.columns.indices) {
                    statement.setters[i](i+1, stmt)
                }
                statement.paramSetter.setParameters(parameters, stmt, statement.columns.size)
                // The parameters come after the columns in the statement
                stmt.executeUpdate()
            }
        }

        override val statementKey: Key get() = Key(table, statement.columns, text)

        override fun prepareStatement(): PreparedStatement {
            val sql = StringBuilder()
            sql.append("UPDATE ${table.tableName} SET ")
            appendCommaList(sql, statement.columns) { col -> "${col.sqlName}=?" }
            if (text != "") {
                sql.append(" ")
                sql.append(text)
            }
            dbLogger.fine { sql.toString() }
            return db.connection.prepareStatement(sql.toString())
        }
    }
}

