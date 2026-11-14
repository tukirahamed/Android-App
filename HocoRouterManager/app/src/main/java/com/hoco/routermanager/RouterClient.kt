package com.hoco.routermanager

import android.net.Network
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ApiException(val kind: Conn, message: String) : Exception(message)

/**
 * Router API client.
 *
 * ASSUMPTION (UNVERIFIED for the HOCO DQ11): the router speaks the "goform" dialect used by
 * ZTE-style pocket routers (/goform/goform_get_cmd_process and /goform/goform_set_cmd_process).
 * If the DQ11 differs, only this file needs to change. Use Settings > Diagnostics to find out.
 *
 * All sockets are bound to the Wi-Fi [network], so it works even when that Wi-Fi has no internet.
 */
class RouterClient(val network: Network?, val host: String) {

    private class MemCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()
        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            store.removeAll { old -> cookies.any { it.name == old.name } }
            store.addAll(cookies)
        }
        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .cookieJar(MemCookieJar())
        .apply { if (network != null) socketFactory(network.socketFactory) }
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Referer", "http://$host/index.html")
                    .header("Origin", "http://$host")
                    .build()
            )
        }
        .build()

    private fun root(): HttpUrl = HttpUrl.Builder().scheme("http").host(host).build()
    private fun url(path: String): HttpUrl.Builder =
        HttpUrl.Builder().scheme("http").host(host).addPathSegments(path)

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw e
    } catch (e: IOException) {
        throw ApiException(Conn.UNREACHABLE, e.message ?: "Network error")
    }

    private fun exec(req: Request): String = http.newCall(req).execute().use { r ->
        val body = r.body?.string().orEmpty()
        if (r.code == 401 || r.code == 403) throw ApiException(Conn.AUTH_REQUIRED, "HTTP ${r.code}")
        if (!r.isSuccessful) throw ApiException(Conn.UNSUPPORTED, "HTTP ${r.code}")
        body
    }

    private fun parse(body: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw ApiException(Conn.UNSUPPORTED, "Router did not return JSON (API dialect differs?)")
    }

    fun fetchHome(): Pair<Int, String> = guard {
        http.newCall(Request.Builder().url(root()).build()).execute().use { r ->
            r.code to r.body?.string().orEmpty()
        }
    }

    fun getCmd(cmds: List<String>, extra: Map<String, String> = emptyMap(), multi: Boolean = true): JSONObject = guard {
        val b = url("goform/goform_get_cmd_process")
            .addQueryParameter("isTest", "false")
            .addQueryParameter("cmd", cmds.joinToString(","))
        if (multi) b.addQueryParameter("multi_data", "1")
        extra.forEach { (k, v) -> b.addQueryParameter(k, v) }
        parse(exec(Request.Builder().url(b.build()).build()))
    }

    fun post(goformId: String, params: Map<String, String> = emptyMap()): JSONObject = guard {
        val fb = FormBody.Builder().add("goformId", goformId)
        params.forEach { (k, v) -> fb.add(k, v) }
        val req = Request.Builder().url(url("goform/goform_set_cmd_process").build()).post(fb.build()).build()
        parse(exec(req))
    }

    /** Runs a set-command and throws unless the router reports success. */
    fun act(goformId: String, params: Map<String, String> = emptyMap()) {
        val r = post(goformId, params)
        val res = r.optString("result")
        if (res != "success" && res != "0") throw ApiException(Conn.UNSUPPORTED, "Router replied: $r")
    }

    fun isLoggedIn(): Boolean {
        val v = getCmd(listOf("loginfo")).optString("loginfo", "")
        return v.isBlank() || v == "ok"
    }

    fun login(password: String): Boolean {
        val b64 = Base64.encodeToString(password.toByteArray(), Base64.NO_WRAP)
        for (candidate in listOf(b64, password)) {
            val res = post("LOGIN", mapOf("password" to candidate)).optString("result")
            if (res == "0" || res == "success") return true
        }
        return false
    }

    fun snapshot(): Snapshot {
        val j = getCmd(
            listOf(
                "battery_vol_percent", "battery_charging", "network_type", "network_provider",
                "signalbar", "rssi", "lte_rsrp", "lte_snr", "SSID1",
                "realtime_tx_thrpt", "realtime_rx_thrpt", "realtime_tx_bytes", "realtime_rx_bytes",
                "sta_count", "realtime_time"
            )
        )
        fun s(k: String): String? = j.optString(k, "").takeIf { it.isNotBlank() && it != "null" }
        return Snapshot(
            batteryPercent = s("battery_vol_percent")?.toIntOrNull(),
            charging = s("battery_charging")?.let { it == "1" },
            networkType = s("network_type"),
            operator = s("network_provider"),
            signalBars = s("signalbar")?.toIntOrNull(),
            rsrp = s("lte_rsrp"),
            sinr = s("lte_snr"),
            rssi = s("rssi"),
            ssid = s("SSID1"),
            downBps = s("realtime_rx_thrpt")?.toLongOrNull(),
            upBps = s("realtime_tx_thrpt")?.toLongOrNull(),
            totalRx = s("realtime_rx_bytes")?.toLongOrNull(),
            totalTx = s("realtime_tx_bytes")?.toLongOrNull(),
            clientCount = s("sta_count")?.toIntOrNull(),
            uptimeSec = s("realtime_time")?.toLongOrNull()
        )
    }

    fun clients(): List<Client>? {
        val arr = getCmd(listOf("station_list")).optJSONArray("station_list") ?: return null
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Client(o.optString("hostname"), o.optString("ip_addr"), o.optString("mac_addr"))
        }
    }

    fun listSms(): List<Sms> {
        val j = getCmd(
            listOf("sms_data_total"),
            mapOf("page" to "0", "data_per_page" to "500", "mem_store" to "1", "tags" to "10", "order_by" to "order by id desc"),
            multi = false
        )
        val arr = j.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Sms(
                id = o.optString("id"),
                number = o.optString("number"),
                body = decodeBody(o.optString("content")),
                unread = o.optString("tag") == "1",
                date = formatDate(o.optString("date"))
            )
        }
    }

    fun markRead(id: String) = act("SET_MSG_READ", mapOf("msg_id" to "$id;", "tag" to "0"))
    fun deleteSms(ids: List<String>) = act("DELETE_SMS", mapOf("msg_id" to ids.joinToString(";") + ";", "notCallback" to "true"))
    fun reboot() = act("REBOOT_DEVICE")

    fun sendSms(number: String, text: String) {
        val hex = text.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }
        val h = TimeZone.getDefault().rawOffset / 3600000
        val time = SimpleDateFormat("yy;MM;dd;HH;mm;ss;", Locale.US).format(Date()) + (if (h >= 0) "+" else "") + h
        act(
            "SEND_SMS",
            mapOf("notCallback" to "true", "Number" to number, "sms_time" to time, "MessageBody" to hex, "ID" to "-1", "encode_type" to "UNICODE")
        )
    }

    /** Diagnostic dump for sharing. Never includes SMS content, passwords or IMEI. */
    fun probe(): String {
        val sb = StringBuilder()
        sb.appendLine("host=$host")
        try {
            val (code, html) = fetchHome()
            sb.appendLine("GET / -> HTTP $code, ${html.length} chars")
            Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)?.let { sb.appendLine("title: ${it.groupValues[1].trim()}") }
            Regex("""src=["']([^"']+\.js[^"']*)["']""").findAll(html).forEach { sb.appendLine("script: ${it.groupValues[1]}") }
        } catch (e: Exception) {
            sb.appendLine("GET / failed: ${e.message}")
        }
        val cmds = listOf(
            "loginfo", "wa_inner_version", "hardware_version", "battery_vol_percent", "battery_charging",
            "network_type", "network_provider", "signalbar", "rssi", "lte_rsrp", "lte_snr", "SSID1",
            "sta_count", "realtime_rx_thrpt", "realtime_tx_thrpt", "realtime_rx_bytes", "realtime_tx_bytes",
            "realtime_time", "sms_unread_num", "modem_main_state"
        )
        for (c in cmds) {
            try {
                sb.appendLine("$c = ${getCmd(listOf(c), multi = false).optString(c, "(absent)")}")
            } catch (e: Exception) {
                sb.appendLine("$c -> ${e.message}")
            }
        }
        return sb.toString()
    }

    private fun decodeBody(s: String): String {
        if (s.length % 2 == 0 && s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            try {
                val bytes = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                return String(bytes, Charsets.UTF_16BE)
            } catch (e: Exception) {
            }
        }
        return s
    }

    private fun formatDate(raw: String): String {
        val p = raw.split(",")
        return if (p.size >= 6) "20${p[0]}-${p[1]}-${p[2]} ${p[3]}:${p[4]}" else raw
    }
}
