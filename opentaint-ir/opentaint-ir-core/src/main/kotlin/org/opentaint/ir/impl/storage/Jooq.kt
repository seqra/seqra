package org.opentaint.ir.impl.storage

import com.google.common.hash.Hashing
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Table
import org.jooq.TableField
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Types

val String.longHash: Long
    get() {
        return Hashing.sipHash24().hashBytes(toByteArray()).asLong()
    }

inline fun DSLContext.withoutAutoCommit(crossinline action: (Connection) -> Unit) {
    connection {
        val ac = it.autoCommit
        it.autoCommit = false
        try {
            action(it)
        } catch (e: Exception) {
            it.rollback()
            throw e
        } finally {
            it.autoCommit = ac
        }
    }
}

fun <ELEMENT, RECORD : Record> Connection.insertElements(
    table: Table<RECORD>,
    elements: Iterable<ELEMENT>,
    autoIncrementId: Boolean = false,
    onConflict: String = "",
    map: PreparedStatement.(ELEMENT) -> Unit
) {
    if (!elements.iterator().hasNext()) {
        return
    }
    val fields = when {
        autoIncrementId -> table.fields().drop(1)
        else -> table.fields().toList()
    }

    val values = fields.joinToString { "?" }

    val query =
        "INSERT INTO \"${table.name}\"(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES ($values) $onConflict"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        elements.forEach {
            stmt.map(it)
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
}

fun <RECORD : Record> Connection.runBatch(table: Table<RECORD>, batchedAction: PreparedStatement.() -> Unit) {
    val fields = table.fields()
    val query =
        "INSERT INTO \"${table.name}\"(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES (${fields.joinToString { "?" }})"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        stmt.batchedAction()
        stmt.executeBatch()
    }
}

fun DSLContext.executeQueries(query: String, asSingle: Boolean = false) {
    connection {
        when (asSingle) {
            true -> execute(query)
            else -> query.split(";").forEach {
                if (it.isNotBlank()) {
                    execute(it)
                }
            }
        }
    }
}

fun DSLContext.executeQueriesFrom(location: String, asSingle: Boolean = false) {
    val stream = javaClass.classLoader.getResourceAsStream(location)
        ?: throw IllegalStateException("no sql script for $location found")
    executeQueries(stream.bufferedReader().readText(), asSingle)
}

fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}

fun <T> Field<T>.eqOrNull(value: T?): Condition {
    if (value == null) {
        return isNull
    }
    return eq(value)
}

fun TableField<*, Long?>.maxId(jooq: DSLContext): Long? {
    return jooq.select(DSL.max(this))
        .from(table).fetchAny()?.component1()
}

class BatchedSequence<T>(private val batchSize: Int, private val getNext: (Long?, Int) -> List<Pair<Long, T>>) :
    Sequence<T> {

    private val result = arrayListOf<T>()
    private var position = 0
    private var maxId: Long? = null

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {

            override fun hasNext(): Boolean {
                if (result.size == position && position % batchSize == 0) {
                    val incomingRecords = getNext(maxId, batchSize)
                    if (incomingRecords.isEmpty()) {
                        return false
                    }
                    result.addAll(incomingRecords.map { it.second })
                    maxId = incomingRecords.maxOf { it.first }
                }
                return result.size > position
            }

            override fun next(): T {
                position++
                return result[position - 1]
            }

        }
    }
}