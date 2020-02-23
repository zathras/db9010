package com.jovial.db9010


/**
 * A parameter to an SQL statement.  Parameters are represented as "?" in the text
 * of a SQL statement.  Before executing a statement, they can be set, using
 * [ParameterSetter.set], which sets one the underlying [java.sql.PreparedStatement]'s parameter
 * values.
 */
class Parameter<T> (

    /**
     * This parameters Kotlin type, and a converter between that and JDBC's type system.
     */
    val adapter: ValueAdapter<T>
)

/**
 * A column in a select statement.  This can be either a [TableColumn] or a
 * [QueryColumn].
 */
abstract class Column<T> (

    /**
     * The SQL name of this column, like "myColumn", or "COUNT(*)".
     */
    val sqlName : String,

    /**
     * This column's Kotlin type, and a converter between that and JDBC's type system.
     */
    val adapter: ValueAdapter<T>,

    /**
     * The name of this column when JSON-friendly maps are used.  A reasonable
     * default is provided if client code doesn't explicitly set a JSON name.
     * See [SelectQuery.setFromJson], [ColumnSetter.setFromJson] and [ResultsHolder.getJson].
     */
    val jsonName : String
) {

    /**
     * The name of this column, qualified by the table name if there is one.
     * Gives a value like "MyTable.myColumn".
     */
    open val qualifiedName : String get() = sqlName

    override fun toString() = "Column($qualifiedName)"
}

/**
 * A column in a [Table].  The SQL type of the column is determined by
 * [sqlType], and the Kotlin type is determined by [adapter].  See [Table]
 */
class TableColumn<T>(

    /**
     * The [Table] of which this column is a part.
     */
    val table : Table,

    sqlName : String,    // Like "MyTable.name", or "name", or "COUNT(*)"

    /**
     * The SQL type of this column, like "VARCHAR(50) NOT NULL".
     */
    val sqlType: String,

    adapter: ValueAdapter<T>,

    jsonName : String = "${table.tableName}.$sqlName"

) : Column<T>(sqlName, adapter, jsonName) {

    init {
        table.register(this)
    }

    override val qualifiedName : String get() = "${table.tableName}.$sqlName"
}

/**
 * A query column with a SQL expression, like "COUNT(*)".  [QueryColumn] values
 * are read-only, and query columns cannot appear in a [SelectQuery] marked as
 * for update.
 */
class QueryColumn<T>(
    sqlName: String,
    adapter: ValueAdapter<T>,
    jsonName: String = sqlName
) : Column<T>(sqlName, adapter, jsonName)

/**
 * Base class for Table definitions.  A table is defined by making a singleton object
 * that implements this type, with [TableColumn] members.  Columns register themselves
 * with their table, which is the first parameter to their constructor.
 *
 * Usage example:
 * ```
 *     object People : Table("People") {
 *         val id = TableColumn(this, "id", "INT AUTO_INCREMENT", Types.sqlInt)
 *         val name = TableColumn(this, "name", "VARCHAR(50) NOT NULL", Types.sqlString)
 *         val worried = TableColumn(this, "worried", "BOOLEAN", Types.sqlBoolean)
 *
 *         override val primaryKeys = listOf(id)
 *     }
 * ```
 */
abstract class Table(

    /**
     * This table's name.  Used for the CREATE TABLE and DROP TABLE statements.
     */
    val tableName : String
) {

    private val _columns = mutableListOf<TableColumn<*>>()

    /**
     * The columns that make up this table.
     */
    val columns : List<TableColumn<*>> get() = _columns

    internal fun register(c: TableColumn<*>) = _columns.add(c)

    // Map to look up a column by its jsonName.
    internal val columnByJsonName by lazy {
        columns.associateBy { c -> c.jsonName }
    }

    /**
     * A string value put at the end of the CREATE TABLE statement,
     * typically to define constraints, like foreign keys.  For example, the demo
     * program has a table that overrides this to be
     * `FOREIGN KEY (city_id) REFERENCES Cities(id) ON DELETE RESTRICT ON UPDATE RESTRICT`.
     */
    open val constraintsSql get() = ""

    /**
     * The primary key(s) of this table.  If specified, the CREATE TABLE will
     * include a PRIMARY KEY section, which has the effect of automatically
     * creating an index.  See also [InsertStatement.Builder] for the variant of
     * insert statements that allows code to get the value of the primary key columns.
     */
    open val primaryKeys get() = listOf<TableColumn<*>>()
}

