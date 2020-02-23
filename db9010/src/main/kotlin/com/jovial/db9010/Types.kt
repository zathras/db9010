package com.jovial.db9010

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet


/**
 * Interface describing an object that is used to adapt a [PreparedStatement] or a
 * [ResultSet] by getting/setting a value of the appropriate type.
 */
interface ValueAdapter<T> {

    /**
     * Set the column at [index] within the [statement] to [value].
     */
    fun set(statement: PreparedStatement, index: Int, value: T)

    /**
     * Update (set) the column at [index] in [set] to [value].
     */
    fun update(set: ResultSet, index: Int, value: T)

    /**
     * Get the column at [index] in [set].
     */
    fun get(set: ResultSet, index: Int) : T

    /**
     * Give the [java.sql.Types] type of this adapter if it is an adapter for
     * nullable types.  If it is not, fail with an IllegalArgumentException.
     * This value is needed when setting a value to null.
     * See [PreparedStatement.setNull]
     */
    val javaSqlTypesTypeOrFail : Int
}

/**
 * Abstract superclass for all value adapters of non-nullable types.  This
 * provides an implementation of a method that creates a value adapter of the
 * corresponding nullable type.
 */
abstract class ValueAdapterNotNull<T>(): ValueAdapter<T> {

    // This map probably only ever has zero or one entry, but it could have
    // a few more.  Multiple SQL types could map to the same underlying Kotlin type,
    // especially for sqlObject.
    private val nullableTypes = mutableMapOf<Int, ValueAdapter<T?>>()

    override val javaSqlTypesTypeOrFail : Int get() {
        throw IllegalArgumentException("Illegal null value")
    }

    /**
     * Get the column at [index] in [set], returning a nullable value.
     */
    abstract protected fun getOrNull(set: ResultSet, index: Int) : T?

    override fun get(set: ResultSet, index: Int) : T =
        getOrNull(set, index)!!

    /**
     * Create a nullable version of this ValueAdapter.  For this, we need the
     * [java.sql.Types] type, which is an integer constant, because
     * [PreparedStatement.setNull] demands it.
     */
    @Synchronized
    fun nullable(javaSqlTypesType: Int) = nullableTypes.computeIfAbsent(javaSqlTypesType) {
        object : ValueAdapter<T?> {
            override fun set(statement: PreparedStatement, index: Int, value: T?) {
                if (value == null) {
                    statement.setNull(index, javaSqlTypesType)
                } else {
                    this@ValueAdapterNotNull.set(statement, index, value)
                }
            }

            override fun update(set: ResultSet, index: Int, value: T?) {
                if (value == null) {
                    set.updateNull(index)
                } else {
                    this@ValueAdapterNotNull.update(set, index, value)
                }
            }

            override fun get(set: ResultSet, index: Int): T? {
                val result = this@ValueAdapterNotNull.getOrNull(set, index)
                if (set.wasNull()) {
                    return null
                } else {
                    return result
                }
            }

            override val javaSqlTypesTypeOrFail : Int get() = javaSqlTypesType
        }
    }
}

object Types {
    /**
     * Adapter for a column that holds a SQL type represented as a Bool in Kotlin
     */
    val sqlBoolean = object: ValueAdapterNotNull<Boolean>() {
        override fun set(statement: PreparedStatement, index: Int, value: Boolean) =
            statement.setBoolean(index, value)
        override fun update(set: ResultSet, index: Int, value: Boolean) =
            set.updateBoolean(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Boolean? =
            set.getBoolean(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a byte[] in Kotlin
     */
    val sqlBytes = object: ValueAdapterNotNull<ByteArray>() {
        override fun set(statement: PreparedStatement, index: Int, value: ByteArray) =
            statement.setBytes(index, value)
        override fun update(set: ResultSet, index: Int, value: ByteArray) =
            set.updateBytes(index, value)
        override fun getOrNull(set: ResultSet, index: Int): ByteArray? =
            set.getBytes(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a Float in Kotlin
     */
    val sqlFloat = object: ValueAdapterNotNull<Float>() {
        override fun set(statement: PreparedStatement, index: Int, value: Float) =
            statement.setFloat(index, value)
        override fun update(set: ResultSet, index: Int, value: Float) =
            set.updateFloat(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Float? =
            set.getFloat(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a Double in Kotlin
     */
    val sqlDouble = object: ValueAdapterNotNull<Double>() {
        override fun set(statement: PreparedStatement, index: Int, value: Double) =
            statement.setDouble(index, value)
        override fun update(set: ResultSet, index: Int, value: Double) =
            set.updateDouble(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Double? =
            set.getDouble(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a Double in Kotlin
     */
    val sqlShort = object: ValueAdapterNotNull<Short>() {
        override fun set(statement: PreparedStatement, index: Int, value: Short) =
            statement.setShort(index, value)
        override fun update(set: ResultSet, index: Int, value: Short) =
            set.updateShort(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Short? =
            set.getShort(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as an Int in Kotlin
     */
    val sqlInt = object: ValueAdapterNotNull<Int>() {
        override fun set(statement: PreparedStatement, index: Int, value: Int) =
            statement.setInt(index, value)
        override fun update(set: ResultSet, index: Int, value: Int) =
            set.updateInt(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Int? =
            set.getInt(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a Long in Kotlin
     */
    val sqlLong = object: ValueAdapterNotNull<Long>() {
        override fun set(statement: PreparedStatement, index: Int, value: Long) =
            statement.setLong(index, value)
        override fun update(set: ResultSet, index: Int, value: Long) =
            set.updateLong(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Long? =
            set.getLong(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a String in Kotlin
     */
    val sqlString = object: ValueAdapterNotNull<String>() {
        override fun set(statement: PreparedStatement, index: Int, value: String) =
            statement.setString(index, value)
        override fun update(set: ResultSet, index: Int, value: String) =
            set.updateString(index, value)
        override fun getOrNull(set: ResultSet, index: Int): String? =
            set.getString(index)
    }

    /**
     * Adapter for a column that holds a SQL type represented as a BigDecimal in Kotlin
     */
    val sqlBigDecimal = object: ValueAdapterNotNull<BigDecimal>() {
        override fun set(statement: PreparedStatement, index: Int, value: BigDecimal) =
            statement.setBigDecimal(index, value)
        override fun update(set: ResultSet, index: Int, value: BigDecimal) =
            set.updateBigDecimal(index, value)
        override fun getOrNull(set: ResultSet, index: Int): BigDecimal? =
            set.getBigDecimal(index)
    }

    /**
     * A column type that uses JDBC's set/get/updateObject methods.  How well this works
     * might depend on the driver
     */
    val sqlObject = object: ValueAdapterNotNull<Any>() {
        override fun set(statement: PreparedStatement, index: Int, value: Any) =
            statement.setObject(index, value)
        override fun update(set: ResultSet, index: Int, value: Any) =
            set.updateObject(index, value)
        override fun getOrNull(set: ResultSet, index: Int): Any? =
            set.getObject(index)

    }

    /**
     * A column type, if you want to just access the underlying JDBC structures directly.
     * This might be useful for more exotic JDBC types, like the stream types that take
     * a length, or the date/time types that take a Calendar.  In those cases, you call
     * the get/set JDBC methods directly, using an index you look up by field in a map
     * provided by this library.
     */
    val sqlOther = object: ValueAdapterNotNull<Unit>() {
        override fun set(statement: PreparedStatement, index: Int, value: Unit) { }
        override fun update(set: ResultSet, index: Int, value: Unit) { }
        override fun getOrNull(set: ResultSet, index: Int) { }
    }
}

