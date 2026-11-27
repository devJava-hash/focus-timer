package com.example.focustimer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

/** Settings for the blocker, stored on the device only. */
class BlockStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun blockedApps(): Set<String> = prefs.getStringSet("blocked", emptySet())!!.toSet()

    fun setBlocked(pkg: String, on: Boolean) {
        val s = blockedApps().toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        prefs.edit().putStringSet("blocked", s).apply()
    }

    var blockDuringTimer: Boolean
        get() = prefs.getBoolean("blockTimer", true)
        set(v) { prefs.edit().putBoolean("blockTimer", v).apply() }

    var scheduleOn: Boolean
        get() = prefs.getBoolean("schedOn", false)
        set(v) { prefs.edit().putBoolean("schedOn", v).apply() }

    /** Minutes after midnight. */
    var startMin: Int
        get() = prefs.getInt("startMin", 9 * 60)
        set(v) { prefs.edit().putInt("startMin", v).apply() }

    var endMin: Int
        get() = prefs.getInt("endMin", 17 * 60)
        set(v) { prefs.edit().putInt("endMin", v).apply() }

    /** Bit 0 = Monday ... bit 6 = Sunday. Default Monday to Friday. */
    var days: Int
        get() = prefs.getInt("days", 31)
        set(v) { prefs.edit().putInt("days", v).apply() }

    /** The timer stores when it will end, so blocking expires by itself if the app is killed. */
    fun setTimerEnd(ms: Long) = prefs.edit().putLong("timerEnd", ms).apply()

    fun isBlockingNow(): Boolean {
        if (blockDuringTimer && System.currentTimeMillis() < prefs.getLong("timerEnd", 0L)) return true
        if (!scheduleOn || startMin == endMin) return false
        val now = LocalDateTime.now()
        val mins = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value - 1
        val yesterday = (today + 6) % 7
        fun has(d: Int) = (days and (1 shl d)) != 0
        return if (startMin < endMin) {
            has(today) && mins >= startMin && mins < endMin
        } else {
            // Overnight window, e.g. 10 PM to 6 AM.
            (has(today) && mins >= startMin) || (has(yesterday) && mins < endMin)
        }
    }
}

/**
 * Watches which app comes to the front. If it is a blocked app and blocking is active,
 * it sends the user to the home screen and shows the block screen.
 * It only reads the app's package name, never any screen content.
 */
class AppBlockerService : AccessibilityService() {
    private val store by lazy { BlockStore(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg !in store.blockedApps() || !store.isBlockingNow()) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("pkg", pkg)
        )
    }

    override fun onInterrupt() {}
}

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        val label = pkg?.let {
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }
                .getOrNull()
        } ?: "This app"
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🔒", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("$label is blocked", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text("Stay focused. You can use it again after your focus time.", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { goHome() }) { Text("Back to home") }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
