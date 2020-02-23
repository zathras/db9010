package com.jovial.db9010

import org.junit.Assert.*
import java.sql.Types as JDBCTypes
import org.junit.*
import java.math.BigDecimal

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

        val testColumn : Int
        val keepalive : Database
        // We need to keep one connection active across tests, or h2 will drop everything.
        val intParam = Parameter(Types.sqlInt)

        init {
            keepalive = Database.open("jdbc:h2:mem:test", "root", "")
            keepalive.createTable(TestTable)
            testColumn = TestTable.run {
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

    private fun<T> withDb(body: (Database) -> T) =
        Database.withConnection("jdbc:h2:mem:test", "root", "") {
            body(it)
        }

    private fun<T> getValue(db: Database, col: TableColumn<T>, rowId : Int = testColumn) : T =
        db.select(col).from(TestTable) + "WHERE " + TestTable.id + "=" + intParam run { query ->
            query[intParam] = rowId
            query.nextOrFail()
            query[col]
        }

    private fun<T> getValue(col: TableColumn<T>, rowId : Int = testColumn) : T =
        withDb { db -> getValue(db, col, rowId) }

    @Test
    fun testBoolean() {
        assertEquals(getValue(TestTable.booleanColumn), true)
    }

    @Test
    fun testBytes() {
        assertArrayEquals(getValue(TestTable.bytesColumn), "zero".toByteArray())
    }

    @Test
    fun testFloat() {
        assertEquals(getValue(TestTable.floatColumn), 1.1f)
    }

    @Test
    fun testDouble() {
        assertEquals(getValue(TestTable.doubleColumn), 2.2)
    }

    @Test
    fun testShort() {
        assertEquals(getValue(TestTable.shortColumn), 3.toShort())
    }

    @Test
    fun testInt() {
        assertEquals(getValue(TestTable.intColumn), 4)
    }

    @Test
    fun testLong() {
        assertEquals(getValue(TestTable.longColumn), 5L)
    }

    @Test
    fun testDecimal() {
        assertEquals(getValue(TestTable.decimalColumn), BigDecimal("6.60"))
    }

    @Test
    fun testNulls() {
        withDb { db ->
            val id = db.insertInto(TestTable) {
                it[TestTable.testName] = "testNulls()"
            } run { it[TestTable.id] }
            assertEquals(getValue(db, TestTable.decimalColumn, id), null);
            assertEquals(getValue(db, TestTable.longColumn, id), null)
            assertEquals(getValue(db, TestTable.intColumn, id), null)
            assertEquals(getValue(db, TestTable.shortColumn, id), null)
            assertEquals(getValue(db, TestTable.doubleColumn, id), null)
            assertEquals(getValue(db, TestTable.floatColumn, id), null)
            assertEquals(getValue(db, TestTable.bytesColumn, id), null)
            assertEquals(getValue(db, TestTable.booleanColumn, id), null)
        }
    }

    @Test
    fun select() {
    }

    @Test
    fun testSelect() {
    }

    @Test
    fun update() {
    }

    @Test
    fun deleteFrom() {
    }

    @Test
    fun statement() {
    }
}
