package dev.drewr.fmswitch.data

import dev.drewr.fmswitch.model.catalogue.BadStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.net.HttpURLConnection
import java.net.URL

class HttpFetch(
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(url: String): ByteArray =
        runInterruptible(ioDispatcher) {
            val connection = (URL(url).openConnection() as HttpURLConnection)
            try {
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.instanceFollowRedirects = true
                val status = connection.responseCode
                if (status !in HTTP_OK..HTTP_REDIRECT) throw BadStatus(status)
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
        const val HTTP_OK = 200
        const val HTTP_REDIRECT = 299
    }
}
