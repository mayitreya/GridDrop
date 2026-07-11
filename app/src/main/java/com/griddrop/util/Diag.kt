package com.griddrop.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object Diag {
    private const val TAG = "GridDropDiag"
    private const val MAX_EVENTS = 250
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events.asStateFlow()

    private val _bytesSent = MutableStateFlow(0L)
    val bytesSent: StateFlow<Long> = _bytesSent.asStateFlow()

    private val _bytesReceived = MutableStateFlow(0L)
    val bytesReceived: StateFlow<Long> = _bytesReceived.asStateFlow()

    private val _bandMode = MutableStateFlow("not started")
    val bandMode: StateFlow<String> = _bandMode.asStateFlow()

    fun log(msg: String) {
        Log.d(TAG, msg)
        val line = "${now()}  $msg"
        _events.update { list -> (list + line).let { if (it.size > MAX_EVENTS) it.takeLast(MAX_EVENTS) else it } }
    }

    fun addSent(bytes: Long) { if (bytes > 0) _bytesSent.update { it + bytes } }

    fun addReceived(bytes: Long) { if (bytes > 0) _bytesReceived.update { it + bytes } }

    fun setBandMode(mode: String) {
        _bandMode.value = mode
        log("Band mode: $mode")
    }

    fun clear() {
        _events.value = emptyList()
        _bytesSent.value = 0
        _bytesReceived.value = 0
    }

    private fun now(): String = synchronized(fmt) { fmt.format(Date()) }
}
