package org.opentaint.dataflow.bidi.engine

import org.opentaint.dataflow.bidi.ap.MethodKey

/** Actual method actors use the run's storage and its required local and call services. */
class RunMethodHandlers(
    private val localFactory: MethodLocalServicesFactory,
    private val callFactory: MethodCallServicesFactory,
) : RunnerHandlerFactory<RunMethodAnalyzer> {
    override fun create(port: RunnerPort<RunMethodAnalyzer>): RunnerHandler<RunMethodAnalyzer> =
        Handler(port, localFactory, callFactory)

    private class Handler(
        port: RunnerPort<RunMethodAnalyzer>,
        private val localFactory: MethodLocalServicesFactory,
        private val callFactory: MethodCallServicesFactory,
    ) : RunnerHandler<RunMethodAnalyzer>, SubscriptionHandler {
        private var runner: RunnerPort<RunMethodAnalyzer>? = port
        private fun active(): RunnerPort<RunMethodAnalyzer> =
            checkNotNull(runner) { "The method handler was released" }.also {
                it.shared.cancellation.checkpoint()
            }
        private var subscriptions: SubscriptionManager? = SubscriptionManager(object : SubscriptionPort {
            override val config get() = active().config
            override val ops get() = active().ops
            override val steps get() = active().steps
            override fun summaryStorage(key: MethodKey): SummaryStorage = active().summaryStorage(key)
            override fun applier(key: MethodKey): SummaryApplier = active().analyzer(key)
            override fun post(event: RunEvent.Delivery): Boolean = active().post(event)
        })

        override fun createActor(key: MethodKey): RunMethodAnalyzer {
            val port = active()
            return RunMethodAnalyzer(key, port,
                PublicationSink { method, batch -> active().summaryStorage(method).publish(batch) },
                localFactory, callFactory)
        }

        override fun handle(event: RunEvent) {
            val port = active()
            when (event) {
                is RunEvent.Start -> port.analyzer(event.root).addRootZero()
                is RunEvent.LinkIn -> port.analyzer(event.callee).addLink(event.ref, event.linkLayer, event.added)
                is RunEvent.ZeroIn -> port.analyzer(event.callee).addZeroEntry()
                is RunEvent.RequestIn -> port.analyzer(event.method).addRequest(event.premise, event.request)
                is RunEvent.Delivery -> checkNotNull(subscriptions).onDelivery(event.callee, event.publications)
                is RunEvent.Work -> error("Work is handled by its owning unit runner")
            }
        }

        override fun subscribe(sub: Subscription) {
            active()
            checkNotNull(subscriptions).subscribe(sub)
        }

        override fun freeze() {
            subscriptions?.freeze()
            subscriptions = null
            runner = null
        }
    }
}
