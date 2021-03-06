package reactivity.experimental

import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.channels.ClosedReceiveChannelException
import kotlinx.coroutines.experimental.launch
import reactivity.experimental.channel.SpScChannel
import reactivity.experimental.channel.Element
import kotlin.coroutines.experimental.CoroutineContext

private const val DEFAULT_CLOSE_MESSAGE = "SpScChannel was closed"

// -------------- Intermediate (transforming) operations

actual inline fun <E> Multi<E>.filter(crossinline predicate: (E) -> Boolean) = object : Multi<E> {
    suspend override fun consume(sink: Sink<E>) {
        var cause: Throwable? = null
        try {
            this@filter.consume(object : Sink<E> {
                suspend override fun send(item: E) {
                    if (predicate(item)) sink.send(item)
                }

                override fun close(cause: Throwable?) {
                    cause?.let { throw it }
                }
            })
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}

actual inline fun <E, F> Multi<E>.map(crossinline mapper: (E) -> F) = object : Multi<F> {
    suspend override fun consume(sink: Sink<F>) {
        var cause: Throwable? = null
        try {
            this@map.consume(object : Sink<E> {
                suspend override fun send(item: E) {
                    sink.send(mapper(item))
                }

                override fun close(cause: Throwable?) {
                    cause?.let { throw it }
                }
            })
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}

actual inline fun <E, R> Multi<E>.reduce(initial: R, crossinline operation: (acc: R, E) -> R)= object : Solo<R> {
    override suspend fun await(): R {
        var acc = initial
        this@reduce.consume(object : Sink<E> {
            override suspend fun send(item: E) {
                acc = operation(acc, item)
            }

            override fun close(cause: Throwable?) { cause?.let { throw it } }
        })
        return acc
    }
}

fun <E : Any> Multi<E>.async(context: CoroutineContext = DefaultDispatcher, buffer: Int = 0): Multi<E> {
    val channel = SpScChannel<E>(buffer)
    return object : Multi<E> {
        suspend override fun consume(sink: Sink<E>) {
            launch(context) {
                try {
                    while (true) {
                        sink.send(channel.receive())
                    }
                } catch (e: Throwable) {
                    if (e is ClosedReceiveChannelException) sink.close(null)
                    else sink.close(e)
                }
            }

            var cause: Throwable? = null
            try {
                this@async.consume(object : Sink<E> {
                    suspend override fun send(item: E) {
                        channel.send(Element(item))
                    }

                    override fun close(cause: Throwable?) {
                        cause?.let { throw it }
                    }
                })
            } catch (e: Throwable) {
                cause = e
            }
            val closeCause = cause ?: ClosedReceiveChannelException(DEFAULT_CLOSE_MESSAGE)
            channel.send(Element(closeCause = closeCause))
        }
    }
}
