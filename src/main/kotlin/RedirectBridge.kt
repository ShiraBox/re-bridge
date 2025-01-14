import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.regex.Pattern


object RedirectBridge {
    private val client = OkHttpClient()
    private val headersJson: String = RedirectBridge::class.java.getResource("headers.json")!!.readText()
    private val payloadJson: String = RedirectBridge::class.java.getResource("payload.json")!!.readText()
    private val _cachedHeaders: HashMap<String, Map<String, String>> = hashMapOf()

    /**
     * Stores headers (cookies) for specific host
     */
    val cachedHeaders: HashMap<String, Map<String, String>> get() = _cachedHeaders

    fun execute(target: String): Response {
        val headers = JsonParser.parseString(headersJson).asJsonObject.asMap().mapValues { it.value.asString }
        val payloadObject = JsonParser.parseString(payloadJson).asJsonObject

        val hostName = URI(target).host

        val request = Request.Builder()
            .url(target)
            .apply {
                _cachedHeaders[hostName]?.forEach { (key, value) -> addHeader(key, value) }
                if (_cachedHeaders[hostName] == null) headers.forEach { (key, value) -> addHeader(key, value) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val originCookies = response.headers("Set-Cookie").map { it.split(";")[0] }
            val checkCookies = mutableListOf<String>()

            if (!response.isSuccessful) {
                val checkRequest = Request.Builder()
                    .url("https://check.ddos-guard.net/check.js")
                    .apply {
                        headers.forEach { (key, value) -> addHeader(key, value) }
                        addHeader("Referer", "https://$hostName")
                    }
                    .build()

                client.newCall(checkRequest).execute().use { checkResponse ->
                    if (!checkResponse.isSuccessful) throw IllegalArgumentException("An error occurred on check request stage: ${checkResponse.code}")
                    checkCookies.addAll(checkResponse.headers("Set-Cookie").map { it.split(";")[0] })

                    val checkRequestBody = checkResponse.body!!.string()

                    val pattern = Pattern.compile("'.*?'")
                    val matcher = pattern.matcher(checkRequestBody)

                    val data = ArrayList<String>()

                    while (matcher.find()) {
                        val quote = matcher.group()
                        val length = quote.length
                        data.add(quote.substring(1, length - 1))
                    }

                    val firstCheckUrl = "https://$hostName${data[0]}"
                    val secondCheckUrl = data[1]

                    val firstCheckRequest = Request.Builder()
                        .url(firstCheckUrl)
                        .apply {
                            headers.forEach { (key, value) -> addHeader(key, value) }
                        }
                        .build()
                    val secondCheckRequest = Request.Builder()
                        .url(secondCheckUrl)
                        .apply {
                            headers.forEach { (key, value) -> addHeader(key, value) }
                        }
                        .build()

                    client.newCall(firstCheckRequest).execute()
                    client.newCall(secondCheckRequest).execute()

                    val postRequest = Request.Builder()
                        .url("https://$hostName/.well-known/ddos-guard/mark/")
                        .post(payloadObject.toString().toRequestBody())
                        .apply {
                            headers.forEach { (key, value) -> addHeader(key, value) }
                            addHeader("Referer", target)
                            addHeader("Origin", "https://$hostName")
                            addHeader("Cookie", originCookies.joinToString(";") + checkCookies.joinToString(";"))
                        }
                        .build()

                    client.newCall(postRequest).execute().use { postResponse ->
                        if (!postResponse.isSuccessful) throw IllegalArgumentException("An error occurred on post request stage: ${postResponse.code}")

                        val endRequest = Request.Builder()
                            .url(target)
                            .apply {
                                headers.forEach { (key, value) -> addHeader(key, value) }
                                addHeader("Referer", target)
                                addHeader("Cookie", originCookies.joinToString(";") + checkCookies.joinToString(";"))
                            }
                            .build()
                        _cachedHeaders[hostName] = endRequest.headers.toMap()

                        return client.newCall(endRequest).execute()
                    }
                }
            } else {
                return response
            }
        }
    }
}
