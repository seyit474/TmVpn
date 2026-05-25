package com.tmvpn.app.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LogBus {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun log(tag: String, message: String) {
        Log.d(tag, message)
        val entry = "[$tag] $message"
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
