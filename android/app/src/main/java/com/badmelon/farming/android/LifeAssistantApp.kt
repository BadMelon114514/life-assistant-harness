package com.badmelon.farming.android

import android.app.Application

class LifeAssistantApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
