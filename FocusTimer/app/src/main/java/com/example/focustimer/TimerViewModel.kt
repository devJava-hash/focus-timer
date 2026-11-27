package com.example.focustimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    var subjects by mutableStateOf(store.subjects())
        private set
    var sessions by mutableStateOf(store.sessions())
        private set
    var selected by mutableStateOf(subjects.firstOrNull() ?: "General")
    var targetMin by mutableStateOf(25)
        private set
    var remainingSec by mutableStateOf(25 * 60)
        private set
    var running by mutableStateOf(false)
        private set

    private var endAt = 0L
    private var startedAt = 0L
    private var job: Job? = null

    val totalSec get() = targetMin * 60
    val elapsedSec get() = totalSec - remainingSec
    val idle get() = !running && remainingSec == totalSec

    fun setTarget(min: Int) {
        if (!idle) return
        targetMin = min
        remainingSec = min * 60
    }

    fun start() {
        if (running) return
        if (idle) startedAt = System.currentTimeMillis()
        endAt = System.currentTimeMillis() + remainingSec * 1000L
        running = true
        job = viewModelScope.launch {
            while (running) {
                val left = ((endAt - System.currentTimeMillis() + 999) / 1000).toInt()
                remainingSec = left.coerceAtLeast(0)
                if (left <= 0) { finish(); break }
                delay(250)
            }
        }
    }

    fun pause() {
        running = false
        job?.cancel()
    }

    /** Ends the session early. Sessions shorter than one minute are discarded. */
    fun stop() {
        pause()
        finish()
    }

    private fun finish() {
        running = false
        job?.cancel()
        val done = elapsedSec
        if (done >= 60) {
            store.addSession(Session(selected, startedAt, done))
            sessions = store.sessions()
        }
        remainingSec = totalSec
    }

    fun addSubject(name: String) {
        store.addSubject(name)
        subjects = store.subjects()
        if (selected !in subjects) selected = subjects.firstOrNull() ?: "General"
    }
}
