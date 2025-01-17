import okhttp3.Request
import java.net.URI

/**
 * Smartly injects proper [DDoS-Guard](https://ddos-guard.net/) headers and cookies to the request
 *
 * @param   url     target url
 */
fun Request.Builder.ddosGuardBridge(url: String): Request.Builder {
    val hostName = URI(url).host
    val cachedHeaders = DdosGuardBridge.cachedHeaders[hostName]

    if (cachedHeaders != null) {
        cachedHeaders.forEach { (key, value) -> addHeader(key, value) }
    } else {
        val reference = DdosGuardBridge.execute(url)
        reference.request.headers.forEach { (key, value) -> addHeader(key, value) }
    }

    return this
}