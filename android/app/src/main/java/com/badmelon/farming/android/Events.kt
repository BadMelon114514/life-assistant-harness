package com.badmelon.farming.android

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object Events {
    val monitorTick = MutableStateFlow(0)
    val notifications = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val backendDown = MutableStateFlow(false)
}
