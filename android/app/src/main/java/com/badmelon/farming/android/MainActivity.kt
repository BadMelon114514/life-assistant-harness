package com.badmelon.farming.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var themeMode by remember { mutableStateOf(Prefs.themeMode) }
            LifeAssistantTheme(themeMode) {
                LifeAssistantApp(
                    themeMode = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        Prefs.themeMode = mode
                    }
                )
            }
        }
    }
}
