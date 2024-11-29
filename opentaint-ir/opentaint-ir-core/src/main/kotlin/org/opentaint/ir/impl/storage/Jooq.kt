package org.opentaint.opentaint-ir.impl.storage

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

fun <ELEMENT, RECORD : Record> Connection.insertElements(
    table: Table<RECORD>,
    elements: Iterable<ELEMENT>,
    map: PreparedStatement.(ELEMENT) -> Unit
) {
    if (!elements.iterator().hasNext()) {
        return
    }
    autoCommit = false
    val fields = table.fields()
    val query =
        "INSERT INTO ${table.name}(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES (${fields.joinToString { "?" }})"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        elements.forEach {
            stmt.map(it)
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
    commit()
    autoCommit = true
}

fun <RECORD : Record> Connection.runBatch(table: Table<RECORD>, batchedAction: PreparedStatement.() -> Unit) {
    autoCommit = false
    val fields = table.fields()
    val query =
        "INSERT INTO ${table.name}(${fields.joinToString { "\"" + it.name + "\"" }}) VALUES (${fields.joinToString { "?" }})"
    prepareStatement(query, Statement.NO_GENERATED_KEYS).use { stmt ->
        stmt.batchedAction()
        stmt.executeBatch()
    }
    commit()
    autoCommit = true
}

fun DSLContext.executeQueries(query: String) {
    connection {
        query.split(";").forEach {
            if (it.isNotBlank()) {
                execute(it)
            }
        }
    }
}

fun DSLContext.executeQueriesFrom(location: String) {
    val stream = javaClass.classLoader.getResourceAsStream(location)
        ?: throw IllegalStateException("no sql script for $location found")
    executeQueries(stream.bufferedReader().readText())
}

fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
    if (value == null) {
        setNull(index, Types.BIGINT)
    } else {
        setLong(index, value)
    }
}

fun PreparedStatement.setNullableString(index: Int, value: String?) {
    if (value == null) {
        setNull(index, Types.VARCHAR)
    } else {
        setString(index, value)
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

class BatchedSequence<T>(private val batchSize: Int, private val getNext: (Long?, Int) -> List<Pair<Long, T>>) : Sequence<T> {

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