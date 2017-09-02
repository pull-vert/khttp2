package reactivity.experimental

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.asCoroutineDispatcher
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.newSingleThreadContext
import java.util.concurrent.Executor
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

abstract class Schedulers private constructor() {

    companion object {
        @JvmStatic fun emptyThreadContext(): Scheduler {
            return SchedulerImpl(EmptyCoroutineContext)
        }

        @JvmStatic fun commonPoolThreadContext(): Scheduler {
            return SchedulerImpl(CommonPool)
        }

        @JvmStatic fun singleThreadContext(): Scheduler {
            return SchedulerImpl(newSingleThreadContext("singleThread"))
        }

        @JvmStatic fun fixedThreadPoolContext(nThreads: Int): Scheduler {
            return SchedulerImpl(newFixedThreadPoolContext(nThreads, "fixedThread"))
        }

        @JvmStatic fun fromExecutor(exectutor: Executor): Scheduler {
            return SchedulerImpl(exectutor.asCoroutineDispatcher())
        }

        @JvmStatic fun fromCoroutineContext(context: CoroutineContext): Scheduler {
            return SchedulerImpl(context)
        }

        private class SchedulerImpl (override val context: CoroutineContext) : Scheduler
    }


}

interface Scheduler /*: Disposable*/ {
   val context: CoroutineContext
}