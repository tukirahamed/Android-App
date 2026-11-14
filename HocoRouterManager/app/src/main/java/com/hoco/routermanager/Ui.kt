@file:OptIn(ExperimentalMaterial3Api::class)

package com.hoco.routermanager

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TABS = listOf("Home", "Devices", "SMS", "Settings")
private val GLYPHS = listOf("\u2302", "\u2630", "\u2709", "\u2699")

@Composable
fun App(vm: MainViewModel, onExit: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val ui = vm.ui
    LaunchedEffect(ui.message) {
        ui.message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(GLYPHS[i], fontSize = 20.sp) }, label = { Text(t) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> DevicesScreen(vm)
                2 -> SmsScreen(vm)
                else -> SettingsScreen(vm, onExit)
            }
        }
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
fun Info(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "Unavailable")
    }
}

private fun connLabel(c: Conn) = when (c) {
    Conn.SEARCHING -> "Searching for router\u2026"
    Conn.NO_WIFI -> "Not on Wi-Fi"
    Conn.UNREACHABLE -> "Router unreachable"
    Conn.AUTH_REQUIRED -> "Login required"
    Conn.UNSUPPORTED -> "Unsupported by firmware / unknown API"
    Conn.CONNECTED -> "Connected"
}

fun fmtRate(bps: Long?): String? = when {
    bps == null -> null
    bps >= 1_048_576 -> "%.2f MB/s".format(bps / 1_048_576.0)
    bps >= 1024 -> "%.1f KB/s".format(bps / 1024.0)
    else -> "$bps B/s"
}

fun fmtBytes(b: Long?): String? = when {
    b == null -> null
    b >= 1_073_741_824L -> "%.2f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576L -> "%.1f MB".format(b / 1_048_576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

private fun fmtUptime(s: Long?): String? = s?.let { "${it / 3600}h ${(it % 3600) / 60}m" }

private fun signalQuality(s: Snapshot): String? {
    if (s.signalBars == 0) return "No signal"
    val r = s.rsrp?.toDoubleOrNull() ?: return null
    return when {
        r >= -85 -> "Excellent"
        r >= -100 -> "Good"
        r >= -110 -> "Fair"
        else -> "Weak"
    }
}

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ui = vm.ui
    val s = ui.snapshot
    var confirmReboot by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("HOCO Router Manager") {
            Text(connLabel(ui.conn), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (ui.detail.isNotBlank()) Text(ui.detail)
            Info("Wi-Fi", ui.ssid)
            Info("Router address", ui.host)
            OutlinedButton(onClick = { vm.refreshNow() }) { Text("Refresh") }
        }
        if (ui.conn == Conn.AUTH_REQUIRED) PasswordCard(vm)
        if (s != null) {
            Section("Battery") {
                Text(s.batteryPercent?.let { "$it%" } ?: "Unavailable", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                s.batteryPercent?.let {
                    LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth().height(10.dp))
                }
                Info("State", s.charging?.let { if (it) "Charging" else "Discharging" })
                val low = s.batteryPercent
                if (low != null && low <= vm.store.lowBattery && s.charging != true) {
                    Text("Low battery", color = MaterialTheme.colorScheme.error)
                }
            }
            Section("Cellular") {
                Info("Operator", s.operator)
                Info("Network type", s.networkType)
                Info("Signal bars", s.signalBars?.toString())
                Info("Quality", signalQuality(s))
                Info("RSRP", s.rsrp)
                Info("SINR", s.sinr)
                Info("RSSI", s.rssi)
            }
            Section("Speed (router reported)") {
                Info("Download", fmtRate(s.downBps))
                Info("Upload", fmtRate(s.upBps))
                Info("Total downloaded", fmtBytes(s.totalRx))
                Info("Total uploaded", fmtBytes(s.totalTx))
                if (ui.history.size > 1) Sparkline(ui.history.map { it.first })
            }
            Section("Router") {
                Info("Connected devices", s.clientCount?.toString())
                Info("Uptime", fmtUptime(s.uptimeSec))
                OutlinedButton(onClick = { confirmReboot = true }) { Text("Reboot router") }
            }
        }
    }
    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            title = { Text("Reboot router?") },
            text = { Text("Everyone connected will be disconnected for a minute or so.") },
            confirmButton = { TextButton(onClick = { confirmReboot = false; vm.reboot() }) { Text("Reboot") } },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun Sparkline(values: List<Long>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(60.dp)) {
        val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val p = Offset(i * step, size.height - (v / max) * size.height)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, color, style = Stroke(width = 3f))
    }
}

@Composable
fun PasswordCard(vm: MainViewModel) {
    var pw by remember { mutableStateOf("") }
    Section("Router login") {
        OutlinedTextField(
            value = pw, onValueChange = { pw = it },
            label = { Text("Router admin password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.savePassword(pw) }, enabled = pw.isNotBlank()) { Text("Save and connect") }
        if (!vm.store.secure) Text("Secure storage is unavailable on this phone, so the password is kept for this session only.")
    }
}

@Composable
fun DevicesScreen(vm: MainViewModel) {
    val list = vm.ui.clients
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connected devices", style = MaterialTheme.typography.titleLarge)
        if (list == null) {
            Text("Unavailable: the router has not reported a device list.")
        } else if (list.isEmpty()) {
            Text("No devices connected.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.name.ifBlank { "(unnamed device)" }, fontWeight = FontWeight.SemiBold)
                            Text("IP: ${c.ip.ifBlank { "unknown" }}")
                            Text("MAC: ${c.mac.ifBlank { "unknown" }}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmsScreen(vm: MainViewModel) {
    val ui = vm.ui
    val list = ui.sms
    val selected: SnapshotStateList<String> = remember { mutableStateListOf() }
    var composing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("SMS on router SIM", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.refreshNow() }) { Text("Refresh") }
            OutlinedButton(onClick = { composing = true }) { Text("Compose") }
            if (selected.isNotEmpty()) Button(onClick = { confirmDelete = true }) { Text("Delete (${selected.size})") }
        }
        if (ui.smsError != null) Text("SMS unavailable: ${ui.smsError}", color = MaterialTheme.colorScheme.error)
        if (list == null) {
            if (ui.smsError == null) Text("Not loaded yet.")
        } else {
            Text("${list.size} messages, ${list.count { it.unread }} unread")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { m ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                            Checkbox(
                                checked = m.id in selected,
                                onCheckedChange = { if (it) selected.add(m.id) else selected.remove(m.id) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(m.number, fontWeight = if (m.unread) FontWeight.Bold else FontWeight.Normal)
                                Text(m.date, style = MaterialTheme.typography.bodySmall)
                                Text(m.body)
                                if (m.unread) TextButton(onClick = { vm.markRead(m.id) }) { Text("Mark as read") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (composing) {
        var number by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { composing = false },
            title = { Text("Send SMS via router SIM") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(number, { number = it }, label = { Text("Recipient number") }, singleLine = true)
                    OutlinedTextField(body, { body = it }, label = { Text("Message") })
                }
            },
            confirmButton = {
                TextButton(
                    enabled = number.isNotBlank() && body.isNotBlank(),
                    onClick = { composing = false; vm.sendSms(number.trim(), body) }
                ) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { composing = false }) { Text("Cancel") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} message(s)?") },
            text = { Text("This deletes them from the router. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSms(selected.toList()); selected.clear() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val store = vm.store
    var ip by remember { mutableStateOf(store.manualIp) }
    var interval by remember { mutableIntStateOf(store.intervalSec) }
    var monitor by remember { mutableStateOf(store.monitor) }
    var nSms by remember { mutableStateOf(store.notifySms) }
    var nBatt by remember { mutableStateOf(store.notifyBattery) }
    var hide by remember { mutableStateOf(store.hidePreview) }
    var low by remember { mutableIntStateOf(store.lowBattery) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("Connection") {
            Text("Router IP is detected from the Wi-Fi gateway. Override only if detection fails.")
            OutlinedTextField(ip, { ip = it }, label = { Text("Manual router IP (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.saveManualIp(ip) }) { Text("Save IP") }
            Text("Refresh interval")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10, 30).forEach { sec ->
                    FilterChip(selected = interval == sec, onClick = { interval = sec; vm.setInterval(sec) }, label = { Text("${sec}s") })
                }
            }
            if (vm.ui.conn != Conn.AUTH_REQUIRED) PasswordCard(vm)
        }
        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, n ->
                    FilterChip(selected = vm.theme == i, onClick = { vm.setTheme(i) }, label = { Text(n) })
                }
            }
        }
        Section("Background alerts") {
            Text("Checks about every 15 minutes, only while the phone is on the router's Wi-Fi.")
            ToggleRow("Enable background monitoring", monitor) { monitor = it; vm.setMonitor(it) }
            ToggleRow("New SMS alerts", nSms) { nSms = it; store.notifySms = it }
            ToggleRow("Low battery alerts", nBatt) { nBatt = it; store.notifyBattery = it }
            ToggleRow("Hide SMS text in notifications", hide) { hide = it; store.hidePreview = it }
            Text("Low battery threshold")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20, 30).forEach { p ->
                    FilterChip(selected = low == p, onClick = { low = p; store.lowBattery = p }, label = { Text("$p%") })
                }
            }
        }
        Section("Diagnostics") {
            Text("Shows what your router actually returns. Contains no SMS text or passwords.")
            Button(onClick = { vm.runDiagnostics() }) { Text("Run diagnostics") }
            if (vm.ui.diag.isNotBlank()) {
                Text(vm.ui.diag, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, vm.ui.diag) }
                    ctx.startActivity(Intent.createChooser(i, "Share diagnostics"))
                }) { Text("Share") }
            }
        }
        Section("About") {
            Text("HOCO Router Manager 0.1.0. Not tested against a physical HOCO DQ11.")
            Button(onClick = onExit) { Text("Exit") }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
