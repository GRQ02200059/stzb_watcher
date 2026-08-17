package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object PacketLogStore {
    private const val MAX_LINES = 200

    private val logs = mutableListOf<String>()
    private val listeners = CopyOnWriteArrayList<(List<String>) -> Unit>()
    private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(message: String) {
        logs.add("${sdf.format(Date())}  $message")
        while (logs.size > MAX_LINES) {
            logs.removeAt(0)
        }
        notifyListeners()
    }

    @Synchronized
    fun clear() {
        logs.clear()
        notifyListeners()
    }

    @Synchronized
    fun snapshot(): List<String> = logs.toList()

    fun addListener(listener: (List<String>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<String>) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    private fun notifyListeners() {
        val snapshot = logs.toList()
        listeners.forEach { it(snapshot) }
    }
}
