package reactivity.experimental.common

import kotlinx.coroutines.experimental.channels.LinkedListChannel
import kotlinx.coroutines.experimental.channels.SubscriptionReceiveChannel
import kotlin.jvm.JvmField

/**
 * This is the interface declaring the publishOn functions
 * for executing the subscriber in other context than the publisher
 * will be implemented in both [Multi] and [SoloPublisher]
 */
interface WithPublishOn {
    fun publishOn(delayError: Boolean): WithPublishOn
    fun publishOn(scheduler: Scheduler, delayError: Boolean): WithPublishOn
}

internal class PublisherPublishOn<T> internal constructor(val delayError: Boolean, val prefetch: Int) :
        LinkedListChannel<T>(), SubscriptionReceiveChannel<T>, Subscriber<T> {

    @Volatile
    @JvmField
    var subscription: Subscription? = null

    override fun afterClose(cause: Throwable?) {
        println("PublisherPublishOn afterClose")
        subscription?.cancel()
    }

    // Subscriber functions
    override fun onSubscribe(s: Subscription) {
        println("PublisherPublishOn onSubscribe")
        if (validateSubscription(subscription, s)) {
            subscription = s
            initialRequest()
        }
    }

    private fun initialRequest() {
        println("PublisherPublishOn initialRequest " + prefetch)
        // In this function we need that the subscription is not null, so use of !!
        if (prefetch == Int.MAX_VALUE) {
            subscription!!.request(Long.MAX_VALUE)
        } else {
            subscription!!.request(prefetch.toLong())
        }
    }

    override fun onNext(t: T) {
        println("PublisherPublishOn onNext " + t)
        offer(t)
    }

    override fun onError(t: Throwable) {
        println("PublisherPublishOn onError" + t)
        close(cause = t)
    }

    override fun onComplete() {
        println("PublisherPublishOn onComplete")
        close(cause = null)
    }

    // Subscription overrides
    override fun close() {
        close(cause = null)
    }
}