package com.hoco.routermanager

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Settings and credentials. The password is only ever stored in Keystore-backed encrypted prefs. */
class Store(ctx: Context) {
    val secure: Boolean
    private val prefs: SharedPreferences
    private var memPw = ""

    init {
        var p: SharedPreferences? = null
        try {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            p = EncryptedSharedPreferences.create(
                ctx, "secure_prefs", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
        }
        secure = p != null
        prefs = p ?: ctx.getSharedPreferences("plain_prefs_no_password", Context.MODE_PRIVATE)
    }

    var password: String
        get() = if (secure) prefs.getString("pw", "") ?: "" else memPw
        set(v) { if (secure) prefs.edit().putString("pw", v).apply() else memPw = v }

    var manualIp: String
        get() = prefs.getString("ip", "") ?: ""
        set(v) = prefs.edit().putString("ip", v).apply()

    var intervalSec: Int
        get() = prefs.getInt("interval", 5)
        set(v) = prefs.edit().putInt("interval", v).apply()

    var theme: Int
        get() = prefs.getInt("theme", 0)
        set(v) = prefs.edit().putInt("theme", v).apply()

    var monitor: Boolean
        get() = prefs.getBoolean("monitor", false)
        set(v) = prefs.edit().putBoolean("monitor", v).apply()

    var notifySms: Boolean
        get() = prefs.getBoolean("n_sms", true)
        set(v) = prefs.edit().putBoolean("n_sms", v).apply()

    var notifyBattery: Boolean
        get() = prefs.getBoolean("n_batt", true)
        set(v) = prefs.edit().putBoolean("n_batt", v).apply()

    var lowBattery: Int
        get() = prefs.getInt("low_batt", 20)
        set(v) = prefs.edit().putInt("low_batt", v).apply()

    var hidePreview: Boolean
        get() = prefs.getBoolean("hide_preview", true)
        set(v) = prefs.edit().putBoolean("hide_preview", v).apply()

    var battAlerted: Boolean
        get() = prefs.getBoolean("batt_alerted", false)
        set(v) = prefs.edit().putBoolean("batt_alerted", v).apply()

    /** Only SMS ids are remembered (to detect new ones), never content. */
    var seenIds: Set<String>?
        get() = prefs.getStringSet("seen_ids", null)
        set(v) = prefs.edit().putStringSet("seen_ids", v).apply()
}
