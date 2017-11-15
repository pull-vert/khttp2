package reactivity.experimental.common

/**
 * Common functions for [Multi] and [Solo]
 */
interface PublisherCommons<T> : WithCallbacks<T>, WithPublishOn, WithLambdas<T>, Publisher<T> {
    val initialScheduler: Scheduler
}