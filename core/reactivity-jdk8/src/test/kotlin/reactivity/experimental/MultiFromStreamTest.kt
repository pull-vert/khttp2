package reactivity.experimental

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.reactive.consumeEach
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import reactivity.experimental.jdk8.toMulti
import java.util.stream.IntStream
import kotlin.test.assertTrue

class MultiFromStreamTest {
    @Test
    fun `multi from Stream inline subscription`() = runBlocking {
        var finally = false
        val source = IntStream.of(1, 2 ,7, 12).toMulti() // a list of Ints
                .doOnSubscribe { println("OnSubscribe") } // provide some insight
                .doFinally { finally = true; println("Finally") } // ... into what's going on
        // iterate over the source fully
        source.consumeEach { println(it) }
        delay(100)
        assertTrue(finally)
    }
}