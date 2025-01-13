import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response


class RedirectBridge {
    private val client = OkHttpClient()
    private val headersJson: String = RedirectBridge::class.java.getResource("headers.json")!!.readText()
    private val payloadJson: String = RedirectBridge::class.java.getResource("payload.json")!!.readText()

    fun execute(target: String): Response {
        val headersObject = JsonParser.parseString(headersJson).asJsonObject
        val payloadObject = JsonParser.parseString(payloadJson).asJsonObject

        val headers = Headers.headersOf(*headersObject.asMap().map { "${it.key}: ${it.value}" }.toTypedArray())

        val request = Request.Builder()
            .url(target)
            .headers(headers)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val srvRequest = Request.Builder()
                    .url("$target/.well-known/ddos-guard/check?context=free_splash")
                    .headers(headers)
                    .build()
                client.newCall(srvRequest).execute()

                val checkRequest = Request.Builder()
                    .url("https://check.ddos-guard.net/check.js")
                    .headers(headers)
                    .build()

                client.newCall(checkRequest).execute().use { checkResponse ->
                    if (!checkResponse.isSuccessful) throw IllegalArgumentException("An error occurred on check request stage: ${checkResponse.code}")

                    val data = checkResponse.body!!.toString().split("'")

                    val firstCheckUrl = target + data[1]
                    val secondCheckUrl = data[2]

                    val firstCheckRequest = Request.Builder()
                        .url(firstCheckUrl)
                        .headers(headers)
                        .build()
                    val secondCheckRequest = Request.Builder()
                        .url(secondCheckUrl)
                        .headers(headers)
                        .build()

                    client.newCall(firstCheckRequest).execute()
                    client.newCall(secondCheckRequest).execute()

                    val postRequest = Request.Builder()
                        .url("$target/.well-known/ddos-guard/mark/")
                        .post(payloadObject.toString().toRequestBody())
                        .headers(headers)
                        .build()

                    client.newCall(postRequest).execute().use { postResponse ->
                        if (!postResponse.isSuccessful) throw IllegalArgumentException("An error occurred on post request stage: ${checkResponse.code}")
                        client.newCall(request).execute().use { endResponse ->
                            return endResponse
                        }
                    }
                }
            } else {
                return response
            }
        }
    }
}
