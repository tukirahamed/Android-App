package com.hoco.routermanager

enum class Conn { SEARCHING, NO_WIFI, UNREACHABLE, AUTH_REQUIRED, UNSUPPORTED, CONNECTED }

data class Sms(val id: String, val number: String, val body: String, val unread: Boolean, val date: String)

data class Client(val name: String, val ip: String, val mac: String)

/** Every field is null when the router did not report it. Nothing is ever invented. */
data class Snapshot(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val networkType: String?,
    val operator: String?,
    val signalBars: Int?,
    val rsrp: String?,
    val sinr: String?,
    val rssi: String?,
    val ssid: String?,
    val downBps: Long?,
    val upBps: Long?,
    val totalRx: Long?,
    val totalTx: Long?,
    val clientCount: Int?,
    val uptimeSec: Long?
) {
    fun isEmpty() = batteryPercent == null && charging == null && networkType == null &&
        operator == null && signalBars == null && rsrp == null && sinr == null && rssi == null &&
        ssid == null && downBps == null && upBps == null && totalRx == null && totalTx == null &&
        clientCount == null && uptimeSec == null
}

data class UiState(
    val conn: Conn = Conn.SEARCHING,
    val detail: String = "",
    val host: String? = null,
    val ssid: String? = null,
    val snapshot: Snapshot? = null,
    val clients: List<Client>? = null,
    val sms: List<Sms>? = null,
    val smsError: String? = null,
    val history: List<Pair<Long, Long>> = emptyList(),
    val diag: String = "",
    val message: String? = null
)
