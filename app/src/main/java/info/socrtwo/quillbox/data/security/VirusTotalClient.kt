package info.socrtwo.quillbox.data.security

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a single VirusTotal URL lookup. */
data class VtUrlResult(
    val verdict: VtVerdict,
    val malicious: Int = 0,
    val suspicious: Int = 0
)

/**
 * Minimal VirusTotal API v3 client. Looks up a URL's existing report by its VT id and,
 * if none exists, submits it for analysis and polls briefly. Requires the user's own
 * API key (the free tier is rate-limited to a few requests per minute).
 */
@Singleton
class VirusTotalClient @Inject constructor() {

    suspend fun scanUrl(apiKey: String, url: String): VtUrlResult = withContext(Dispatchers.IO) {
        runCatching {
            val id = Base64.encodeToString(
                url.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            // 1) Try the existing report for this URL.
            val existing = get("$BASE/urls/$id", apiKey)
            if (existing.code == 200) {
                return@runCatching parseStats(existing.body)
            }
            if (existing.code != 404) {
                return@runCatching VtUrlResult(VtVerdict.ERROR)
            }
            // 2) Not seen before — submit it, then poll the analysis.
            val submit = postForm("$BASE/urls", apiKey, "url=" + URLEncoder.encode(url, "UTF-8"))
            if (submit.code !in 200..299) return@runCatching VtUrlResult(VtVerdict.ERROR)
            val analysisId = JSONObject(submit.body).getJSONObject("data").getString("id")
            repeat(5) {
                delay(3000)
                val analysis = get("$BASE/analyses/$analysisId", apiKey)
                if (analysis.code == 200) {
                    val attrs = JSONObject(analysis.body).getJSONObject("data").getJSONObject("attributes")
                    if (attrs.optString("status") == "completed") {
                        val stats = attrs.getJSONObject("stats")
                        return@runCatching toResult(
                            stats.optInt("malicious"), stats.optInt("suspicious")
                        )
                    }
                }
            }
            VtUrlResult(VtVerdict.NOT_SCANNED) // timed out waiting for analysis
        }.getOrElse { VtUrlResult(VtVerdict.ERROR) }
    }

    private fun parseStats(body: String): VtUrlResult {
        val stats = JSONObject(body).getJSONObject("data")
            .getJSONObject("attributes")
            .getJSONObject("last_analysis_stats")
        return toResult(stats.optInt("malicious"), stats.optInt("suspicious"))
    }

    private fun toResult(malicious: Int, suspicious: Int): VtUrlResult = when {
        malicious > 0 -> VtUrlResult(VtVerdict.MALICIOUS, malicious, suspicious)
        suspicious > 0 -> VtUrlResult(VtVerdict.SUSPICIOUS, malicious, suspicious)
        else -> VtUrlResult(VtVerdict.CLEAN)
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun get(urlStr: String, apiKey: String): HttpResponse =
        request(urlStr, "GET", apiKey, null)

    private fun postForm(urlStr: String, apiKey: String, form: String): HttpResponse =
        request(urlStr, "POST", apiKey, form)

    private fun request(urlStr: String, method: String, apiKey: String, body: String?): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("x-apikey", apiKey)
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15000
            readTimeout = 20000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://www.virustotal.com/api/v3"
    }
}
