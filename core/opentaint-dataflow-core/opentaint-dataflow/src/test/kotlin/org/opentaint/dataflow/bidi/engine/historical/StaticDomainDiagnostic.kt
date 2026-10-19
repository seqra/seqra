package org.opentaint.dataflow.bidi.engine.historical

// Historical pure reference model. This code is never part of the runtime artifact.
import org.opentaint.dataflow.bidi.engine.*

import org.opentaint.dataflow.ap.ifds.ClassStaticAccessor
import org.opentaint.dataflow.ap.ifds.FieldAccessor
import org.opentaint.dataflow.bidi.ap.*
import org.opentaint.dataflow.bidi.store.CallerRef

/** Only the failing guard calls this borrowed decoder. The exception retains just its bounded text.
 * No Facts/Pattern conversion, array snapshot, alias query, rule evaluation or call resolution occurs.
 * The caller has already checked cancellation; all diagnostic loops have fixed limits. */
internal fun staticDomainMissMessage(
    ap: ApManager, owner: MethodKey?, ref: CallerRef, input: Facts,
    footprint: Run1StaticFootprint, cls: Int, node: TrieNode<*>, field: Int? = null,
): String {
    val prefix = if (field == null) "Static input class is absent from the frozen prescan universe"
        else "Static input field is absent from the frozen prescan universe"
    return try {
        val out = DomainDiagnosticText()
        out.text(prefix).text("; STATIC_DOMAIN_MISS")
        fun accessor(id: Int, symbolBytes: Int = 2048) {
            out.text(id.toString()).text(":")
            when (val value = ap.accessors.accessor(id)) {
                is ClassStaticAccessor -> out.value(value.typeName, symbolBytes)
                is FieldAccessor -> out.value(value.className, symbolBytes).text("#").value(value.fieldName, symbolBytes)
                    .text(":").value(value.fieldType, symbolBytes)
                else -> out.value(value.toString(), symbolBytes)
            }
        }
        fun path(ids: IntArray) {
            out.text("[")
            for (index in 0 until minOf(ids.size, MAX_IDS)) {
                if (index != 0) out.text(",")
                accessor(ids[index])
            }
            out.text("];omitted=").text((ids.size - minOf(ids.size, MAX_IDS)).toString())
        }
        fun exclusion(e: ExclusionSet) {
            when (e) {
                ExclusionSet.Empty -> out.text("Empty")
                is ExclusionSet.Concrete -> { out.text("Concrete"); path(e.data) }
            }
        }
        fun marks(ids: IntArray) {
            out.text("[")
            for (index in 0 until minOf(ids.size, MAX_IDS)) {
                if (index != 0) out.text(",")
                out.text(ids[index].toString()).text(":").value(ap.marks.name(TaintMark(ids[index])))
            }
            out.text("];omitted=").text((ids.size - minOf(ids.size, MAX_IDS)).toString())
        }
        fun method(key: MethodKey?) {
            if (key == null) { out.text("UNAVAILABLE"); return }
            out.text("{method=").value(key.method.toString())
                .text(";name=").value(key.method.name)
                .text(";entry=").text(key.statement.location.index.toString())
                .text(";context=").value(key.context.toString()).text("}")
        }
        // Keep the failing coordinate/counts ahead of optional premise/header detail.
        out.text(";class="); accessor(cls)
        if (field != null) { out.text(";missingField="); accessor(field) }
        val fields = node.accessorData ?: EMPTY_PATH
        out.text(";fieldCount=").text(fields.size.toString())
            .text(";domainClasses=").text(footprint.classUniverse.size.toString())
            .text(";domainFields=").text(footprint.fieldUniverse.size.toString())
            .text(";fields=[")
        // 32 * (3 * 64 symbol bytes + scalar ID/separators) stays below 8192 bytes.
        for (index in 0 until minOf(fields.size, MAX_IDS)) {
            if (index != 0) out.text(",")
            accessor(fields[index], symbolBytes = 64)
        }
        out.text("];omittedFields=").text((fields.size - minOf(fields.size, MAX_IDS)).toString())
        out.text(";callee="); method(owner)
        out.text(";caller="); method(ref.caller)
        out.text(";callMethod=").value(ref.call.location.method.toString())
            .text(";call=").text(ref.call.location.index.toString())
            .text(";callerLayer=").text(ref.callerLayer.name)
            .text(";premiseCount=").text(ref.premise.size.toString()).text(";premises=[")
        for (index in 0 until minOf(ref.premise.size, MAX_PREMISES)) {
            if (index != 0) out.text(",")
            val member = ref.premise.member(index)
            out.text("{id=").text(member.id.toString()).text(";base=").value(member.base.toString())
                .text(";tail=").text(member.tail.name).text(";path=")
            path(member.pathData)
            out.text(";E="); exclusion(member.exclusion)
            out.text(";mark=")
            when (val mark = member.mark) {
                is MarkSlot.Concrete -> marks(intArrayOf(mark.mark.id))
                is MarkSlot.Star -> { out.text("STAR;mx="); marks(mark.excluded.data) }
            }
            out.text("}")
        }
        out.text("];omittedPremises=").text((ref.premise.size - minOf(ref.premise.size, MAX_PREMISES)).toString())
            .text(";inputKind=").text(when (input) { is Reach -> "REACH"; is FlowTree -> "FLOW"; is TaintTree -> "TAINT" })
            .text(";inputBase=").value(input.base.toString()).text(";inputLayer=").text(input.layer.name)
            .text(";E=")
        exclusion(when (input) { is Reach -> ExclusionSet.Empty; is FlowTree -> input.exclusion; is TaintTree -> input.exclusion })
        out.text(";mx="); marks((input as? FlowTree)?.markExclusion?.data ?: EMPTY_PATH)
        out.finish()
    } catch (error: Exception) {
        // Preserve the original guard even if an identifier cannot be decoded.
        "$prefix; STATIC_DOMAIN_MISS classId=$cls fieldId=$field diagnosticUnavailable=${error.javaClass.name}"
    }
}

private const val MAX_IDS = 32
private const val MAX_PREMISES = 8

/** UTF-8 upper bound: no unbounded collection rendering or substring/byte-array copy. */
private class DomainDiagnosticText {
    private val data = StringBuilder()
    private var bytes = 0
    private var omittedUtf16Units = 0L
    fun text(value: String): DomainDiagnosticText = append(value, MAX_BYTES)
    fun value(value: String, limit: Int = MAX_VALUE_BYTES): DomainDiagnosticText = append(value, limit)

    private fun append(value: String, limit: Int): DomainDiagnosticText {
        var index = 0
        var localBytes = 0
        while (index < value.length) {
            val cp = Character.codePointAt(value, index)
            val chars = Character.charCount(cp)
            val width = when { cp < 0x80 -> 1; cp < 0x800 -> 2; cp < 0x10000 -> 3; else -> 4 }
            val escaped = cp == '\n'.code || cp == '\r'.code || cp == '\t'.code
            val need = if (escaped) 2 else width
            if (localBytes + need > limit || bytes + need > MAX_BYTES) {
                // No walk of an unprinted suffix. This is omitted UTF-16 units, not exact points.
                omittedUtf16Units += value.length - index
                break
            }
            if (escaped) data.append('\\').append(when (cp) { 10 -> 'n'; 13 -> 'r'; else -> 't' })
            else data.appendCodePoint(cp)
            bytes += need; localBytes += need; index += chars
        }
        return this
    }

    fun finish(): String = data.append(";diagnosticOmittedUtf16Units=").append(omittedUtf16Units)
        .append(";diagnosticBodyMaxBytes=").append(MAX_BYTES).toString()

    companion object {
        private const val MAX_BYTES = 32768
        private const val MAX_VALUE_BYTES = 2048
    }
}
