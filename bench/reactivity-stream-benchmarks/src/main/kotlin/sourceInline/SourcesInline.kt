package sourceInline

import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlin.coroutines.experimental.CoroutineContext

// -------------- Interface definitions

interface SourceInline<out E> {
    suspend fun consume(sink: Sink<E>)
    companion object Factory
}

interface Sink<in E> {
    suspend fun send(item: E)
    fun close(cause: Throwable?)
}

// -------------- Factory (initial/producing) operations

fun SourceInline.Factory.range(start: Int, count: Int): SourceInline<Int> = object : SourceInline<Int> {
    suspend override fun consume(sink: Sink<Int>) {
        var cause: Throwable? = null
        try {
            for (i in start until (start + count)) {
                sink.send(i)
            }
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}

// -------------- Terminal (final/consuming) operations

suspend fun <E, R> SourceInline<E>.fold(initial: R, operation: suspend (acc: R, E) -> R): R {
    var acc = initial
    consume(object : Sink<E> {
        suspend override fun send(item: E) {
            acc = operation(acc, item)
        }
        override fun close(cause: Throwable?) { cause?.let { throw it } }
    })
    return acc
}

inline suspend fun <E, R> SourceInline<E>.fold2(initial: R, crossinline operation: (acc: R, E) -> R): R {
    var acc = initial
    consume(object : Sink<E> {
        suspend override fun send(item: E) {
            acc = operation(acc, item)
        }
        override fun close(cause: Throwable?) { cause?.let { throw it } }
    })
    return acc
}

inline suspend fun <E, R> SourceInline<E>.filterFold2(initial: R, crossinline predicate: (E) -> Boolean, crossinline operation: (acc: R, E) -> R): R {
    var acc = initial
    consume(object : Sink<E> {
        suspend override fun send(item: E) {
            if (predicate(item)) acc = operation(acc, item)
        }
        override fun close(cause: Throwable?) { cause?.let { throw it } }
    })
    return acc
}

// -------------- Intermediate (transforming) operations

fun <E> SourceInline<E>.filter(predicate: suspend (E) -> Boolean) = object : SourceInline<E> {
    suspend override fun consume(sink: Sink<E>) {
        var cause: Throwable? = null
        try {
            this@filter.consume(object : Sink<E> {
                suspend override fun send(item: E) {
                    if (predicate(item)) sink.send(item)
                }
                override fun close(cause: Throwable?) { cause?.let { throw it } }
            })
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}

inline fun <E> SourceInline<E>.filter2(crossinline predicate: (E) -> Boolean) = object : SourceInline<E> {
    suspend override fun consume(sink: Sink<E>) {
        var cause: Throwable? = null
        try {
            this@filter2.consume(object : Sink<E> {
                suspend override fun send(item: E) {
                    if (predicate(item)) sink.send(item)
                }
                override fun close(cause: Throwable?) { cause?.let { throw it } }
            })
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}

fun <E> SourceInline<E>.async(context: CoroutineContext = DefaultDispatcher, buffer: Int = 0): SourceInline<E> {
    val channel = Channel<E>(buffer)
    return object : SourceInline<E> {
    suspend override fun consume(sink: Sink<E>) {
        var cause: Throwable? = null
        try {
            channel.send(it)
            this@async.consume(object : Sink<E> {
                suspend override fun send(item: E) {

                }
            }
        } catch (e: Throwable) {
            cause = e
        }
        sink.close(cause)
    }
}