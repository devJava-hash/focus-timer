package com.example.focustimer

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Session(val subject: String, val start: Long, val seconds: Int)

fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** Tiny SharedPreferences-backed storage for subjects and finished sessions. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun subjects(): List<String> =
        prefs.getString("subjects", "Maths,Physics,Reading")!!.split(",").filter { it.isNotBlank() }

    fun addSubject(raw: String) {
        val name = raw.trim().replace(",", " ").replace("|", " ")
        if (name.isEmpty()) return
        val list = subjects()
        if (name !in list) prefs.edit().putString("subjects", (list + name).joinToString(",")).apply()
    }

    fun sessions(): List<Session> =
        prefs.getString("sessions", "")!!.lines().mapNotNull { line ->
            val p = line.split("|")
            if (p.size == 3) runCatching { Session(p[0], p[1].toLong(), p[2].toInt()) }.getOrNull() else null
        }

    fun addSession(s: Session) {
        val cur = prefs.getString("sessions", "")!!
        prefs.edit().putString("sessions", cur + "${s.subject}|${s.start}|${s.seconds}\n").apply()
    }
}

fun todaySeconds(list: List<Session>): Int {
    val today = LocalDate.now()
    return list.filter { dayOf(it.start) == today }.sumOf { it.seconds }
}

/** Consecutive days with at least one session, ending today (or yesterday if nothing yet today). */
fun streak(list: List<Session>): Int {
    val days = list.map { dayOf(it.start) }.toSet()
    var d = LocalDate.now()
    if (d !in days) d = d.minusDays(1)
    var n = 0
    while (d in days) { n++; d = d.minusDays(1) }
    return n
}

/** Seconds studied for each of the last 7 days, oldest first. */
fun lastWeek(list: List<Session>): List<Pair<LocalDate, Int>> {
    val today = LocalDate.now()
    return (6 downTo 0).map { off ->
        val d = today.minusDays(off.toLong())
        d to list.filter { dayOf(it.start) == d }.sumOf { it.seconds }
    }
}

fun bySubject(list: List<Session>): List<Pair<String, Int>> =
    list.groupBy { it.subject }.map { it.key to it.value.sumOf { s -> s.seconds } }.sortedByDescending { it.second }

fun fmtDuration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
