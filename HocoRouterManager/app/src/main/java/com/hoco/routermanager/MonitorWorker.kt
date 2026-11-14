package com.hoco.routermanager

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic background check (Android allows at most every 15 minutes). Only runs when the phone
 * is on the router's Wi-Fi; otherwise it exits immediately without doing anything.
 */
class MonitorWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val store = Store(ctx)
        if (!store.monitor) return@withContext Result.success()
        val wifi = Discovery.findWifi(ctx) ?: return@withContext Result.success()
        val host = store.manualIp.trim().ifBlank { wifi.gateway } ?: return@withContext Result.success()
        val client = RouterClient(wifi.network, host)
        try {
            if (!client.isLoggedIn()) {
                if (store.password.isBlank() || !client.login(store.password)) return@withContext Result.success()
            }
            Notifier.ensureChannels(ctx)

            if (store.notifyBattery) {
                val snap = client.snapshot()
                val pct = snap.batteryPercent
                if (pct != null) {
                    val low = pct <= store.lowBattery && snap.charging != true
                    if (low && !store.battAlerted) {
                        Notifier.post(ctx, Notifier.CH_BATT, 2, "Router battery low", "HOCO router is at $pct%", true)
                        store.battAlerted = true
                    } else if (pct > store.lowBattery + 5 || snap.charging == true) {
                        store.battAlerted = false
                    }
                }
            }

            if (store.notifySms) {
                val unread = client.listSms().filter { it.unread }
                val previous = store.seenIds
                val ids = unread.map { it.id }.toSet()
                if (previous != null) {
                    val fresh = unread.filter { it.id !in previous }
                    if (fresh.isNotEmpty()) {
                        val title = if (fresh.size == 1) "New SMS from ${fresh[0].number}" else "${fresh.size} new SMS on router"
                        val text = if (fresh.size == 1 && !store.hidePreview) fresh[0].body else "Open the app to read"
                        Notifier.post(ctx, Notifier.CH_SMS, 1, title, text, !store.hidePreview)
                    }
                }
                store.seenIds = ids
            }
        } catch (e: ApiException) {
            // Router unreachable or unsupported: stay quiet.
        }
        Result.success()
    }
}
