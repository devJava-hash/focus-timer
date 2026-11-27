@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.focustimer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the screen on while studying.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF3F51B5))) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App(vm: TimerViewModel = viewModel()) {
    var tab by remember { mutableStateOf(0) }
    val titles = listOf("Timer", "Stats", "Subjects", "Block")
    Scaffold(
        bottomBar = {
            NavigationBar {
                titles.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(listOf("⏱", "📊", "📚", "🚫")[i]) },
                        label = { Text(t) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TimerScreen(vm)
                1 -> StatsScreen(vm)
                2 -> SubjectsScreen(vm)
                else -> BlockScreen()
            }
        }
    }
}

@Composable
fun TimerScreen(vm: TimerViewModel) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Focus", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            val progress = if (vm.totalSec == 0) 0f else vm.elapsedSec.toFloat() / vm.totalSec
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp
            )
            Text(
                "%02d:%02d".format(vm.remainingSec / 60, vm.remainingSec % 60),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light
            )
        }

        Text("Duration (minutes)", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45, 60).forEach { m ->
                FilterChip(
                    selected = vm.targetMin == m,
                    onClick = { vm.setTarget(m) },
                    enabled = vm.idle,
                    label = { Text("$m") }
                )
            }
        }

        Text("Subject", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.subjects) { s ->
                FilterChip(
                    selected = vm.selected == s,
                    onClick = { vm.selected = s },
                    enabled = vm.idle,
                    label = { Text(s) }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.running) {
                Button(onClick = { vm.pause() }) { Text("Pause") }
            } else {
                Button(onClick = { vm.start() }) { Text(if (vm.idle) "Start" else "Resume") }
            }
            if (!vm.idle) {
                OutlinedButton(onClick = { vm.stop() }) { Text("Finish") }
            }
        }
        Text(
            "Today: ${fmtDuration(todaySeconds(vm.sessions))}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatsScreen(vm: TimerViewModel) {
    val week = lastWeek(vm.sessions)
    val maxSec = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Stats", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Today", fmtDuration(todaySeconds(vm.sessions)), Modifier.weight(1f))
                StatCard("Streak", "${streak(vm.sessions)} days", Modifier.weight(1f))
                StatCard("Total", fmtDuration(vm.sessions.sumOf { it.seconds }), Modifier.weight(1f))
            }
        }
        item {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                week.forEach { (d, sec) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth()
                                .height((100 * sec / maxSec).dp.coerceAtLeast(2.dp))
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        )
                        Text(d.dayOfWeek.name.take(1), fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text("By subject", style = MaterialTheme.typography.titleMedium) }
        val subj = bySubject(vm.sessions)
        if (subj.isEmpty()) {
            item { Text("No sessions yet. Finish a timer to see stats.") }
        } else {
            items(subj) { (name, sec) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name)
                    Text(fmtDuration(sec), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
fun SubjectsScreen(vm: TimerViewModel) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Subjects", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("New subject") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.addSubject(text); text = "" }) { Text("Add") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.subjects) { s ->
                Card(Modifier.fillMaxWidth()) { Text(s, Modifier.padding(16.dp)) }
            }
        }
    }
}
