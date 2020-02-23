package com.jovial.db9010

import java.sql.PreparedStatement

/**
 * A generic SQL statement.  The entire statement body is set by user code.
 * See [Database.statement].
 */
class GenericStatement private constructor(private val paramSetter : ParameterSetterImpl)
    : ParameterSetter by paramSetter {

    /**
     * A key used by [Database] for caching generic statements.
     */
    data class Key (private val text: String)

    /**
     * Handler that is called after executing the PreparedStatement.  The first argument
     * is the return value of PreparedStatement.execute().  Client code can set this to a
     * different value in order to obtain the [PreparedStatement] and the return value
     * of [PreparedStatement.execute] after it is called by [Builder.run].
     */
    var resultHandler : ((Boolean, PreparedStatement) -> Unit)? = null

    /**
     * A builder for a generic statement.  See [Database.statement].
     */
    class Builder(private val db: Database) : TextStatementBuilder<Key>() {

        private var text = ""
        private val statement = GenericStatement(ParameterSetterImpl())

        /**
         * Call [setBody] to set any statement [Parameter] values, then call
         * [PreparedStatement.execute] to run the SQL statement.  If [setBody] sets
         * a value for [GenericStatement.resultHandler], call that with the result of
         * [PreparedStatement.execute].
         */
        infix fun run(setBody: (GenericStatement) -> Unit) {
            setBody(statement)
            statement.paramSetter.checkParameters(parameters)
            text = textBuilder.toString()
            return db.genericStatements.withStatement(this) { stmt ->
                statement.paramSetter.setParameters(parameters, stmt)
                val r : Boolean = stmt.execute()
                statement.resultHandler?.invoke(r, stmt)
            }
        }

        override val statementKey: Key get() = Key(text)

        override fun prepareStatement(): PreparedStatement {
            dbLogger.fine(text)
            return db.connection.prepareStatement(text)
        }
    }
}

