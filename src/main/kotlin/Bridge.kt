import okhttp3.Request
import java.net.URI

/**
 * Smartly injects proper [DDoS-Guard](https://ddos-guard.net/) headers and cookies to the request
 *
 * @param   url     target url
 */
fun Request.Builder.reBridge(url: String): Request.Builder {
    val hostName = URI(url).host
    val cachedHeaders = RedirectBridge.cachedHeaders[hostName]

    if (cachedHeaders != null) {
        cachedHeaders.forEach { (key, value) -> addHeader(key, value) }
    } else {
        val reference = RedirectBridge.execute(url)
        reference.request.headers.forEach { (key, value) -> addHeader(key, value) }
    }

    return this
}