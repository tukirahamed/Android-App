package com.hoco.routermanager

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    var ui by mutableStateOf(UiState())
        private set
    var theme by mutableIntStateOf(store.theme)
        private set

    private var client: RouterClient? = null
    private var job: Job? = null
    private var tick = 0
    private val lock = Mutex()

    init { start() }

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(store.intervalSec * 1000L)
            }
        }
    }

    fun stop() { job?.cancel() }

    fun refreshNow() {
        viewModelScope.launch { tick = 0; refresh() }
    }

    private suspend fun refresh() = lock.withLock {
        val next = withContext(Dispatchers.IO) { compute(ui) }
        ui = next
    }

    private fun describe(e: ApiException) = when (e.kind) {
        Conn.UNREACHABLE -> "Cannot reach the router (${e.message})."
        Conn.AUTH_REQUIRED -> "Router requires login (${e.message})."
        Conn.UNSUPPORTED -> "Router answered, but not in the expected format: ${e.message}. Run Settings > Diagnostics and share the result."
        else -> e.message ?: ""
    }

    private fun compute(prev: UiState): UiState {
        val ctx = getApplication<Application>()
        val ssid = Discovery.ssid(ctx)
        val wifi = Discovery.findWifi(ctx)
            ?: return prev.copy(
                conn = Conn.NO_WIFI, detail = "Phone is not connected to Wi-Fi.", ssid = null,
                snapshot = null, clients = null, sms = null
            )
        val host = store.manualIp.trim().ifBlank { wifi.gateway }
            ?: return prev.copy(conn = Conn.UNREACHABLE, detail = "Wi-Fi has no IPv4 gateway. Enter the router IP in Settings.", ssid = ssid)

        val existing = client
        val c = if (existing != null && existing.host == host && existing.network == wifi.network) {
            existing
        } else {
            RouterClient(wifi.network, host).also { client = it }
        }
        return try {
            if (!c.isLoggedIn()) {
                val pw = store.password
                if (pw.isBlank()) {
                    return prev.copy(conn = Conn.AUTH_REQUIRED, detail = "Enter the router password.", host = host, ssid = ssid)
                }
                if (!c.login(pw)) {
                    return prev.copy(conn = Conn.AUTH_REQUIRED, detail = "Router rejected the password.", host = host, ssid = ssid)
                }
            }
            val snap = c.snapshot()
            if (snap.isEmpty()) {
                return prev.copy(
                    conn = Conn.UNSUPPORTED, host = host, ssid = ssid, snapshot = null,
                    detail = "Router answered but reported none of the expected fields. Run Settings > Diagnostics."
                )
            }
            tick++
            var clients = prev.clients
            var sms = prev.sms
            var smsErr = prev.smsError
            if (tick % 3 == 1) {
                clients = try { c.clients() } catch (e: ApiException) { null }
                try { sms = c.listSms(); smsErr = null } catch (e: ApiException) { smsErr = e.message }
            }
            val d = snap.downBps
            val u = snap.upBps
            val hist = if (d != null && u != null) (prev.history + (d to u)).takeLast(60) else prev.history
            prev.copy(
                conn = Conn.CONNECTED, detail = "", host = host, ssid = snap.ssid ?: ssid,
                snapshot = snap, clients = clients, sms = sms, smsError = smsErr, history = hist
            )
        } catch (e: ApiException) {
            prev.copy(conn = e.kind, detail = describe(e), host = host, ssid = ssid)
        }
    }

    fun savePassword(pw: String) { store.password = pw; client = null; refreshNow() }
    fun saveManualIp(ip: String) { store.manualIp = ip; client = null; refreshNow() }
    fun setInterval(sec: Int) { store.intervalSec = sec; start() }
    fun setTheme(t: Int) { store.theme = t; theme = t }
    fun clearMessage() { ui = ui.copy(message = null) }

    fun setMonitor(on: Boolean) {
        store.monitor = on
        val wm = WorkManager.getInstance(getApplication())
        if (on) {
            wm.enqueueUniquePeriodicWork(
                "monitor", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES).build()
            )
        } else {
            wm.cancelUniqueWork("monitor")
        }
    }

    private fun action(label: String, block: (RouterClient) -> Unit) {
        viewModelScope.launch {
            val c = client
            val msg = if (c == null) "Not connected to the router." else withContext(Dispatchers.IO) {
                try { block(c); "$label: router accepted the request." } catch (e: ApiException) { "$label failed: ${e.message}" }
            }
            ui = ui.copy(message = msg)
            tick = 0
            refresh()
        }
    }

    fun reboot() = action("Reboot") { it.reboot() }
    fun markRead(id: String) = action("Mark read") { it.markRead(id) }
    fun deleteSms(ids: List<String>) = action("Delete") { it.deleteSms(ids) }
    fun sendSms(number: String, text: String) = action("Send SMS") { it.sendSms(number, text) }

    fun runDiagnostics() {
        viewModelScope.launch {
            val c = client
            val text = if (c == null) "Not connected to a router yet." else withContext(Dispatchers.IO) { c.probe() }
            ui = ui.copy(diag = text)
        }
    }
}
