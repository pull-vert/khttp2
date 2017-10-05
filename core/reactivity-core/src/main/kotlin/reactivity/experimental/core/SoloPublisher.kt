package reactivity.experimental.core

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.ProducerScope
import kotlinx.coroutines.experimental.newCoroutineContext
import kotlinx.coroutines.experimental.reactive.awaitSingle
import org.reactivestreams.Publisher
import org.reactivestreams.Subscription
import reactivity.experimental.core.internal.coroutines.consumeUnique
import java.io.Closeable
import kotlin.coroutines.experimental.startCoroutine

/**
 * Creates cold reactive [SoloPublisher] that runs a given [block] in a coroutine.
 * Every time the returned publisher is subscribed, it starts a new coroutine in the specified [scheduler].
 * Coroutine emits items with `produce`. Unsubscribing cancels running coroutine.
 *
 * Invocation of `produce` is suspended appropriately when subscribers apply back-pressure and to ensure that
 * `onNext` is not invoked concurrently.
 *
 * | **Coroutine action**                         | **Signal to subscriber**
 * | -------------------------------------------- | ------------------------
 * | `value in end of coroutine is not null`      | `onNext`
 * | Normal completion or `close` without cause   | `onComplete`
 * | Failure with exception or `close` with cause | `onError`
 */
// TODO provide a custom ProducerScope impl that checks send is only called once, and throws exception otherwise !
// TODO after that remove SoloCoroutine
internal fun <T> soloPublisher(
        scheduler: Scheduler,
        block: suspend ProducerScope<T>.() -> Unit
): DefaultSoloPublisher<T> = SoloPublisherImpl(Publisher { subscriber ->
    val newContext = newCoroutineContext(scheduler.context)
    val coroutine = SoloCoroutine(newContext, subscriber)
    coroutine.initParentJob(scheduler.context[Job])
    subscriber.onSubscribe(coroutine) // do it first (before starting coroutine), to avoid unnecessary suspensions
    block.startCoroutine(coroutine, coroutine)
}, scheduler)

///**
// * Singleton builder for [Solo], a single (or empty) value Reactive Stream [Publisher]
// *
// * @author Frédéric Montariol
// */
//object SoloBuilder : SoloPublisherBuilder() {
//    @JvmStatic
//    fun <T> fromValue(value: T): SoloPublisher<T> = Companion.fromValue(value)
//
//    @JvmStatic
//    fun <T> fromValue(scheduler: Scheduler, value: T): SoloPublisher<T> = Companion.fromValue(scheduler, value)
//}

/**
 * Builder for [SoloPublisherImpl], a single (or empty) value Reactive Stream [Publisher]
 *
 * @author Frédéric Montariol
 */
abstract class SoloPublisherBuilder {
    // protected Static factory methods to create a SoloPublisherImpl
    protected companion object {
        fun <T> fromValue(value: T) = fromValue(DEFAULT_SCHEDULER, value)

        fun <T> fromValue(scheduler: Scheduler, value: T) = soloPublisher(scheduler) {
            send(value)
        }
    }
}

/**
 * Subscribes to this [SoloPublisher] and performs the specified action for the unique received element.
 */
inline suspend fun <T> SoloPublisher<T>.consumeUnique(action: (T) -> Unit) {
    action.invoke(awaitSingle())
}

/**
 * 2 in 1 Type that can be used to [await] elements from the
 * open producer and to [close] it to unsubscribe.
 */
interface DeferredCloseable<out T> : Deferred<T>, Closeable {
    /**
     * Closes this deferred.
     */
    override fun close()
}

/**
 * Single (or empty) value Reactive Stream [Publisher]
 *
 * @author Frédéric Montariol
 */
interface SoloPublisher<T> : PublisherCommons<T> {
    // functions from WithCallbacks
    override fun doOnSubscribe(onSubscribe: (Subscription) -> Unit): SoloPublisher<T>

    override fun doOnNext(onNext: (T) -> Unit): SoloPublisher<T>
    override fun doOnError(onError: (Throwable) -> Unit): SoloPublisher<T>
    override fun doOnComplete(onComplete: () -> Unit): SoloPublisher<T>
    override fun doOnCancel(onCancel: () -> Unit): SoloPublisher<T>
    override fun doOnRequest(onRequest: (Long) -> Unit): SoloPublisher<T>
    override fun doFinally(finally: () -> Unit): SoloPublisher<T>

    // function from WithPublishOn
    /**
     * Returns a [Solo][reactivity.experimental.Solo] that is published with [DEFAULT_SCHEDULER] and the [delayError] option
     *
     * @param delayError if error should be delayed
     */
    override fun publishOn(delayError: Boolean): SoloPublisher<T>

    /**
     * Returns a [Solo][reactivity.experimental.Solo] that is published with the provided [scheduler] and the [delayError] option
     *
     * @param scheduler the scheduler containing the coroutine context to execute this coroutine in
     * @param delayError if error should be delayed
     */
    override fun publishOn(scheduler: Scheduler, delayError: Boolean): SoloPublisher<T>

    // Operators specific to SoloPublisher
}

class SoloPublisherImpl<T>(override val delegate: Publisher<T>,
                                   override val initialScheduler: Scheduler)
    : DefaultSoloPublisher<T>, Publisher<T> by delegate

interface DefaultSoloPublisher<T> : SoloPublisher<T> {

    val delegate: Publisher<T>
    override val initialScheduler: Scheduler

    override fun doOnSubscribe(onSubscribe: (Subscription) -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onSubscribeBlock = onSubscribe
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onSubscribeBlock = onSubscribe
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doOnNext(onNext: (T) -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onNextBlock = onNext
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onNextBlock = onNext
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doOnError(onError: (Throwable) -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onErrorBlock = onError
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onErrorBlock = onError
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doOnComplete(onComplete: () -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onCompleteBlock = onComplete
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onCompleteBlock = onComplete
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doOnCancel(onCancel: () -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onCancelBlock = onCancel
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onCancelBlock = onCancel
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doOnRequest(onRequest: (Long) -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).onRequestBlock = onRequest
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.onRequestBlock = onRequest
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun doFinally(finally: () -> Unit): DefaultSoloPublisher<T> {
        if (delegate is PublisherWithCallbacks) {
            (delegate as PublisherWithCallbacks<T>).finallyBlock = finally
            return this
        }
        // otherwise creates a new PublisherWithCallbacks
        val publisherCallbacks = PublisherWithCallbacks(this)
        publisherCallbacks.finallyBlock = finally
        return SoloPublisherImpl(publisherCallbacks, initialScheduler)
    }

    override fun publishOn(delayError: Boolean) = publishOn(initialScheduler, delayError)

    override fun publishOn(scheduler: Scheduler, delayError: Boolean) = soloPublisher(scheduler) {
        val completableConsumer = SoloPublishOn<T>(delayError)
        this@DefaultSoloPublisher.subscribe(completableConsumer)
        completableConsumer.consumeUnique {
            send(it)
        }
    }
}