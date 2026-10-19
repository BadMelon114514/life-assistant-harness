package com.badmelon.farming.android

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "life_assistant"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    var baseUrl: String
        get() = sp.getString("base_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
        set(value) {
            val clean = value.trim().trimEnd('/')
            sp.edit().putString("base_url", clean).apply()
        }

    var session: String
        get() {
            val existing = sp.getString("session", null)
            if (!existing.isNullOrBlank()) return existing
            val created = "android_" + System.currentTimeMillis().toString(36)
            sp.edit().putString("session", created).apply()
            return created
        }
        set(value) {
            sp.edit().putString("session", value).apply()
        }

    var themeMode: String
        get() = sp.getString("theme_mode", "system") ?: "system"
        set(value) {
            sp.edit().putString("theme_mode", value).apply()
        }

    fun newSession(): String {
        val created = "android_" + System.currentTimeMillis().toString(36)
        session = created
        return created
    }
}
