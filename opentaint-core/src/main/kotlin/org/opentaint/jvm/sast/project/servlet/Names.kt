package org.opentaint.jvm.sast.project.servlet

import org.opentaint.ir.api.jvm.JIRAnnotation
import org.opentaint.ir.api.jvm.JIRClassOrInterface
import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.ir.api.jvm.ext.allSuperHierarchySequence
import org.opentaint.jvm.sast.dataflow.matchedAnnotations

private const val WebServletAnnotation = "javax.servlet.annotation.WebServlet"
private const val ServletRequest = "javax.servlet.ServletRequest"

private val servletMethods = setOf(
    "doGet",
    "doPost",
    "doPut",
    "doDelete",
    "doHead",
    "doOptions",
    "doTrace"
)

fun JIRMethod.isServletRequestMethod(): Boolean =
    enclosingClass.name == ServletRequest

fun JIRMethod.isWebServletMethod(): Boolean =
    name in servletMethods && collectWebServletAnnotations() != null

fun JIRMethod.collectWebServletAnnotations(): List<JIRAnnotation>? {
    enclosingClass.collectWebServletAnnotations()?.let { return it }

    return enclosingClass.allSuperHierarchySequence
        .firstNotNullOfOrNull { it.collectWebServletAnnotations()  }
}

fun JIRClassOrInterface.collectWebServletAnnotations(): List<JIRAnnotation>? =
    matchedAnnotations({ it == WebServletAnnotation }).takeIf { it.isNotEmpty() }
