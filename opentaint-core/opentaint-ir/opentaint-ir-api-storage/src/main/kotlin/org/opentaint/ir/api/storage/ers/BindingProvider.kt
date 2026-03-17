package org.opentaint.ir.api.storage.ers

interface BindingProvider {
    fun <T : Any> getBinding(clazz: Class<T>): Binding<T>
}
