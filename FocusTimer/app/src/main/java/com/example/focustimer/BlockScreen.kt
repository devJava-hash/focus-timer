@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.focustimer

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isBlockerEnabled(ctx: Context): Boolean {
    val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return list.split(':').any { it.contains(ctx.packageName) && it.endsWith("AppBlockerService") }
}

fun fmtTime(m: Int): String {
    val h = m / 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m % 60, if (h < 12) "AM" else "PM")
}

private fun pickTime(ctx: Context, initial: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, initial / 60, initial % 60, false).show()
}

@Composable
fun BlockScreen() {
    val ctx = LocalContext.current
    val store = remember { BlockStore(ctx) }

    var blocked by remember { mutableStateOf(store.blockedApps()) }
    var onTimer by remember { mutableStateOf(store.blockDuringTimer) }
    var schedOn by remember { mutableStateOf(store.scheduleOn) }
    var start by remember { mutableStateOf(store.startMin) }
    var end by remember { mutableStateOf(store.endMin) }
    var days by remember { mutableStateOf(store.days) }
    var enabled by remember { mutableStateOf(isBlockerEnabled(ctx)) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // Re-check the accessibility setting when returning from the system settings screen.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) enabled = isBlockerEnabled(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                // Never offer this app or system Settings, so you can always undo the setup.
                .filter { it.first != ctx.packageName && it.first != "com.android.settings" }
                .sortedBy { it.second.lowercase() }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item { Text("Block apps", fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (enabled) {
                        Text("Blocking is turned on ✅", fontWeight = FontWeight.Medium)
                    } else {
                        Text("Step 1: allow blocking", fontWeight = FontWeight.Medium)
                        Text(
                            "Turn on \"Focus Timer\" in Accessibility settings. The app only reads which app is " +
                                "open so it can block it. It does not read your screen and has no internet access.",
                            fontSize = 13.sp
                        )
                        Text(
                            "If the switch is greyed out (Android 13+): open Settings → Apps → Focus Timer → " +
                                "⋮ menu → Allow restricted settings, then try again.",
                            fontSize = 13.sp
                        )
                        Button(onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Open Accessibility settings") }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("When to block", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("While the focus timer is running", Modifier.weight(1f))
                        Switch(checked = onTimer, onCheckedChange = { onTimer = it; store.blockDuringTimer = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("On a daily schedule", Modifier.weight(1f))
                        Switch(checked = schedOn, onCheckedChange = { schedOn = it; store.scheduleOn = it })
                    }
                    if (schedOn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { pickTime(ctx, start) { start = it; store.startMin = it } }) {
                                Text("From ${fmtTime(start)}")
                            }
                            OutlinedButton(onClick = { pickTime(ctx, end) { end = it; store.endMin = it } }) {
                                Text("To ${fmtTime(end)}")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                                FilterChip(
                                    selected = (days and (1 shl i)) != 0,
                                    onClick = { days = days xor (1 shl i); store.days = days },
                                    label = { Text(d) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Text("Apps to block (${blocked.size} selected)", fontWeight = FontWeight.Medium) }

        if (apps.isEmpty()) {
            item { Text("Loading apps…") }
        } else {
            items(apps, key = { it.first }) { (pkg, name) ->
                val toggle = {
                    store.setBlocked(pkg, pkg !in blocked)
                    blocked = store.blockedApps()
                }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = pkg in blocked, onCheckedChange = { toggle() })
                    Text(name)
                }
            }
        }
    }
}
