package org.opentaint.dataflow.jvm.ap.ifds

import org.opentaint.ir.api.jvm.JIRMethod
import org.opentaint.dataflow.jvm.ap.ifds.LambdaAnonymousClassFeature.JIRLambdaClass
import java.util.concurrent.ConcurrentHashMap

class JIRLambdaTracker {
    private val lambdaTrackers = ConcurrentHashMap<JIRMethod, LambdaTracker>()

    fun registerLambda(lambda: JIRLambdaClass) {
        val methodLambdas = lambdaTrackers.computeIfAbsent(lambda.lambdaMethod) {
            LambdaTracker(lambda.lambdaMethod)
        }

        methodLambdas.addLambda(lambda)
    }

    fun subscribeOnLambda(method: JIRMethod, subscriber: LambdaSubscriber) {
        val methodLambdas = lambdaTrackers.computeIfAbsent(method) {
            LambdaTracker(method)
        }

        methodLambdas.addSubscriber(subscriber)
    }

    fun forEachRegisteredLambda(method: JIRMethod, subscriber: LambdaSubscriber) {
        val methodLambdas = lambdaTrackers[method] ?: return
        methodLambdas.forEachRegisteredLambda(subscriber)
    }

    private class LambdaTracker(private val method: JIRMethod) {
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
