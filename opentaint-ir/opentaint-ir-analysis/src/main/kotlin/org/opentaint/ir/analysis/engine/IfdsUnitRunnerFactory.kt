package org.opentaint.ir.analysis.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.opentaint.ir.api.JIRMethod
import org.opentaint.ir.api.analysis.JIRApplicationGraph

/**
 * Represents a runner and allows to manipulate it.
 *
 * By convention, runners are created by instances of [IfdsUnitRunnerFactory],
 * but this creation doesn't launch anything.
 * The work itself is launched by [launchIn] method, which should be called exactly once.
 * This method returns a [Job] instance representing a launched coroutine.
 * This [Job] could also be further obtained via [job] property.
 *
 * It is not recommended to implement this interface directly, instead,
 * [AbstractIfdsUnitRunner] should be extended.
 */
interface IfdsUnitRunner {
    val unit: UnitType
    val job: Job?

    fun launchIn(scope: CoroutineScope): Job

    /**
     * Submits a new [IfdsEdge] to runner's queue. Should be called only after [launchIn].
     * Note that this method can be called from different threads.
     */
    suspend fun submitNewEdge(edge: IfdsEdge)
}

/**
 * [AbstractIfdsUnitRunner] contains proper implementation of [launchIn] method and [job] property.
 * Inheritors should only implement [submitNewEdge] and a suspendable [run] method.
 * The latter is the main method of runner, that should do all its work.
 */
abstract class AbstractIfdsUnitRunner(
    final override val unit: UnitType,
) : IfdsUnitRunner {
    /**
     * The main method of the runner, which will be called by [launchIn]
     */
    protected abstract suspend fun run()

    private var _job: Job? = null
    final override val job: Job? get() = _job

    final override fun launchIn(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.LAZY) {
        run()
    }.also {
        _job = it
        it.start()
    }
}

/**
 * Produces a runner for any given unit.
 */
interface IfdsUnitRunnerFactory {
    /**
     * Produces a runner for given [unit], using given [startMethods] as entry points.
     * All start methods should belong to the [unit].
     * Note that this method DOES NOT START runner's job.
     *
     * @param graph provides supergraph for application (including methods, belonging to other units)
     *
     * @param manager [IfdsUnitManager] instance that will manage the produced runner.
     *
     * @param unitResolver will be used to get units of methods observed during analysis.
     */
    fun newRunner(
        graph: JIRApplicationGraph,
        manager: IfdsUnitManager,
        unitResolver: UnitResolver,
        unit: UnitType,
        startMethods: List<JIRMethod>,
    ): IfdsUnitRunner
}
