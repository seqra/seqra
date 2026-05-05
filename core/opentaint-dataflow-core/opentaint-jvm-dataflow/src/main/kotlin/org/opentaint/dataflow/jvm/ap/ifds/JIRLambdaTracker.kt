package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaClass
import org.opentaint.ir.api.jvm.JIRMethod

object JIRLambdaTracker {
    class LambdaTracker(val method: JIRMethod) {
        private val subscribers = hashSetOf<LambdaSubscriber>()
        private val registeredLambdas = hashSetOf<JIRLambdaClass>()

        fun addLambda(lambda: JIRLambdaClass) = synchronized(this) {
            if (!registeredLambdas.add(lambda)) return
            subscribers.forEach { it.newLambda(method, lambda) }
        }

        fun addSubscriber(subscriber: LambdaSubscriber) = synchronized(this) {
            if (!subscribers.add(subscriber)) return
            registeredLambdas.forEach { subscriber.newLambda(method, it) }
        }

        fun forEachRegisteredLambda(subscriber: LambdaSubscriber) = synchronized(this) {
            registeredLambdas.forEach { subscriber.newLambda(method, it) }
        }
    }

    interface LambdaSubscriber {
        fun newLambda(method: JIRMethod, lambdaClass: JIRLambdaClass)
    }
}
