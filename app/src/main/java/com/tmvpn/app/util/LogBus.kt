package com.tmvpn.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBus {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val ts = fmt.format(Date())
        val entry = "$ts [$tag] $message"
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun allText(): String = _logs.value.joinToString("\n")
}
