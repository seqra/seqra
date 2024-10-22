package org.opentaint.ir.impl.types

import org.opentaint.ir.api.Classpath
import org.opentaint.ir.api.MethodParameterId
import org.opentaint.ir.api.throwClassNotFound
import org.opentaint.ir.impl.suspendableLazy

class MethodParameterIdImpl(private val info: ParameterInfo, private val classpath: Classpath) :
    MethodParameterId {

    override suspend fun access() = info.access

    override val name: String?
        get() = info.name

    private val lazyType = suspendableLazy {
        classpath.findClassOrNull(info.type) ?: info.type.throwClassNotFound()
    }
    private val lazyAnnotations = suspendableLazy {
        info.annotations?.map {
            AnnotationIdImpl(info = it, classpath)
        }
    }


    override suspend fun paramClassId() = lazyType()

    override suspend fun annotations() = lazyAnnotations().orEmpty()
}