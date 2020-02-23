package com.jovial.db9010

import java.sql.Types as JDBCTypes
import org.junit.*
import java.math.BigDecimal
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

object TestTable : Table("test_table") {
    val id = TableColumn(this, "id", "INT AUTO_INCREMENT", Types.sqlInt)
    val testName = TableColumn(this, "test_name", "VARCHAR(40) NOT NULL", Types.sqlString)
    val booleanColumn = TableColumn(this, "boolean_column", "BOOLEAN", Types.sqlBoolean.nullable(JDBCTypes.BOOLEAN))
    val bytesColumn = TableColumn(this, "binary_column", "VARBINARY(20)", Types.sqlBytes.nullable(JDBCTypes.BINARY))
    val floatColumn = TableColumn(this, "float_column", "FLOAT", Types.sqlFloat.nullable(JDBCTypes.FLOAT))
    val doubleColumn = TableColumn(this, "double_column", "DOUBLE", Types.sqlDouble.nullable(JDBCTypes.DOUBLE))
    val shortColumn = TableColumn(this, "short_column", "SMALLINT", Types.sqlShort.nullable(JDBCTypes.SMALLINT))
    val intColumn = TableColumn(this, "int_column", "INT", Types.sqlInt.nullable(JDBCTypes.INTEGER))
    val longColumn = TableColumn(this, "long_column", "BIGINT", Types.sqlLong.nullable(JDBCTypes.BIGINT))
    val decimalColumn = TableColumn(this, "decimal_column", "DECIMAL(10, 2)",
            Types.sqlBigDecimal.nullable(JDBCTypes.DECIMAL))
    // TODO val fooColumn = TableColumn(this, "foo_column", "XX", Types.sqlOther)

    override val primaryKeys = listOf(id)
}

class DatabaseTest {

    companion object {

        val testRow : Int
        val keepalive : Database
        // We need to keep one connection active across tests, or h2 will drop everything.
        val intParam = Parameter(Types.sqlInt)
        val stringParam = Parameter(Types.sqlString)
        val countColumn = QueryColumn("COUNT(*)", Types.sqlInt)

        init {
            // Send logging to stdout so we can see the statements
            val logger = Logger.getLogger("com.jovial.db9010")
            val handler = object : Handler() {
                override fun publish(r: LogRecord) = println(r.message)
                override fun flush() { }
                override fun close() { }
            }
            logger.addHandler(handler)
            logger.level = Level.FINE

            keepalive = Database.open("jdbc:h2:mem:test", "root", "")
            keepalive.createTable(TestTable)
            testRow = TestTable.run {
                keepalive.insertInto(TestTable) { row ->
                    row[testName] = "Setup Row"
                    row[booleanColumn] = true
                    row[bytesColumn] = "zero".toByteArray()
                    row[floatColumn] = 1.1f
                    row[doubleColumn] = 2.2
                    row[shortColumn] = 3
                    row[intColumn] = 4
                    row[longColumn] = 5L
                    row[decimalColumn] = BigDecimal("6.60")
                } run { it[id] }
            }
        }

        @AfterClass @JvmStatic
        fun tearDown() {
            try {
                keepalive.dropTable(TestTable)
            } finally {
                keepalive.close()
            }
        }
    }

    private fun<T> withDb(body: (Database) -> T) : T =
        Database.withConnection("jdbc:h2:mem:test", "root", "") {
            body(it)
        }

    private fun<T> getValue(db: Database, col: Column<T>, rowId : Int = testRow) : T =
        db.select(col).from(TestTable) + "WHERE " + TestTable.id + "=" + intParam run { query ->
            query[intParam] = rowId
            query.nextOrFail()
            val r = query[col]
            assert(!query.next())
            r
        }

    private fun<T> getValue(col: Column<T>, rowId : Int = testRow) : T =
        withDb { db -> getValue(db, col, rowId) }

    private fun insertRow(db: Database, name: String) =
        db.insertInto(TestTable) {
            it[TestTable.testName] =  name
        } run { it[TestTable.id] }

    @Test
    fun testBoolean() {
        assert(getValue(TestTable.booleanColumn) == true)
    }

    @Test
    fun testBytes() {
        assert(Arrays.equals(getValue(TestTable.bytesColumn), "zero".toByteArray()))
    }

    @Test
    fun testFloat() {
        assert(getValue(TestTable.floatColumn) == 1.1f)
    }

    @Test
    fun testDouble() {
        assert(getValue(TestTable.doubleColumn) == 2.2)
    }

    @Test
    fun testShort() {
        assert(getValue(TestTable.shortColumn) == 3.toShort())
    }

    @Test
    fun testInt() {
        assert(getValue(TestTable.intColumn) == 4)
    }

    @Test
    fun testLong() {
        assert(getValue(TestTable.longColumn) == 5L)
    }

    @Test
    fun testDecimal() {
        assert(getValue(TestTable.decimalColumn) == BigDecimal("6.60"))
    }

    @Test
    fun testNulls() {
        withDb { db ->
            val id = db.insertInto(TestTable) {
                it[TestTable.testName] = "testNulls()"
            } run { it[TestTable.id] }
            assert(getValue(db, TestTable.decimalColumn, id) == null)
            assert(getValue(db, TestTable.longColumn, id) == null)
            assert(getValue(db, TestTable.intColumn, id) == null)
            assert(getValue(db, TestTable.shortColumn, id) == null)
            assert(getValue(db, TestTable.doubleColumn, id) == null)
            assert(getValue(db, TestTable.floatColumn, id) == null)
            assert(getValue(db, TestTable.bytesColumn, id) == null)
            assert(getValue(db, TestTable.booleanColumn, id) == null)
        }
    }

    @Test
    fun testNoResultsInsert() {
        val testName = "testNoResultsInsert()"
        withDb { db ->
            db.insertInto(TestTable) run {
                it[TestTable.testName] = testName
            }
            val count = db.select(countColumn).from(TestTable) +
                    " WHERE " + TestTable.testName + "=" + stringParam run { query ->
                query[stringParam] = testName
                query.nextOrFail()
                query[countColumn]
            }
            assert(count == 1)
        }
    }

    @Test
    fun deleteFrom() {
        val testName = "deleteFrom()"
        withDb { db ->
            val id = insertRow(db, testName)
            assert(getValue(db, countColumn, id) == 1)
            val deleted = db.deleteFrom(TestTable) + " WHERE " + TestTable.id + "=" + intParam run {
                it[intParam] = id
            }
            assert(deleted == 1)
            assert(getValue(db, countColumn, id) == 0)
        }
    }

    @Test
    fun update() {
        val testName = "update()"
        withDb { db ->
            val id = insertRow(db, testName)
            assert(getValue(db, countColumn, id) == 1)
            assert(getValue(db, TestTable.testName, id) == testName)
            val newName = testName + " mod"
            val updated = db.update(TestTable) + "WHERE " + TestTable.id + "=" + intParam run {
                it[intParam] = id
                it[TestTable.testName] = newName
            }
            assert(updated == 1)
            assert(getValue(db, TestTable.testName, id) == newName)
        }
    }

    @Test
    fun statement() {
        val result = withDb { db ->
            var r = ""
            db.statement() + "SELECT " + TestTable.testName + " FROM " + TestTable +
                    " WHERE " + TestTable.id + "=" + intParam run { generic ->
                generic[intParam] = testRow
                generic.resultHandler = { execOK, stmt ->
                    assert(execOK)
                    val rs = stmt.resultSet
                    var found : Boolean = rs.next()
                    assert(found)
                    r = rs.getString(1)
                    found = rs.next()
                    assert(!found)
                }
            }
            r
        }
        assert(result == "Setup Row")
    }

    @Test
    fun transaction() {
        withDb { db ->
            var row1 = -1
            var row2 = -1
            try {
                db.transaction {
                    row1 = insertRow(db, "transaction()")
                    row2 = insertRow(db, "transaction()")
                    assert(getValue(db, countColumn, row1) == 1)
                    assert(getValue(db, countColumn, row2) == 1)
                    throw Exception("Abort transaction")
                }
            } catch (e : Exception) {
            }
            assert(getValue(db, countColumn, row1) == 1)
            assert(getValue(db, countColumn, row2) == 1)

            db.transaction {
                row1 = insertRow(db, "transaction()")
                row2 = insertRow(db, "transaction()")
                assert(getValue(db, countColumn, row1) == 1)
                assert(getValue(db, countColumn, row2) == 1)
            }
            assert(getValue(db, countColumn, row1) == 1)
            assert(getValue(db, countColumn, row2) == 1)
        }
    }
}
