package org.opentaint.ir.api.jvm.storage.ers

interface BindingProvider {
    fun <T : Any> getBinding(clazz: Class<T>): Binding<T>
}
