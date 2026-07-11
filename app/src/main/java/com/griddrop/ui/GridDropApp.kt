package com.griddrop.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.griddrop.Role
import com.griddrop.hotspot.HotspotStatus
import com.griddrop.net.SharedFile
import com.griddrop.util.Diag
import com.griddrop.util.QrGenerator
import com.griddrop.viewmodel.MainViewModel
import com.griddrop.viewmodel.Step
import com.griddrop.viewmodel.UiState

@Composable
fun GridDropApp(viewModel: MainViewModel, onPickFiles: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    
    
    BackHandler(enabled = state.step != Step.CHOOSE_ROLE) {
        when (state.step) {
            Step.CONNECTING, Step.JOIN_WIFI, Step.ERROR -> viewModel.goHome()
            Step.OPEN_PAGE, Step.TRANSFER -> viewModel.back()
            Step.TROUBLESHOOT -> viewModel.closeTroubleshoot()
            Step.CHOOSE_ROLE -> {}
        }
    }
    
    BackHandler(enabled = state.showStats) { viewModel.closeStats() }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (state.step) {
                Step.CHOOSE_ROLE -> ChooseRoleScreen(viewModel)
                Step.CONNECTING -> ConnectingScreen()
                Step.JOIN_WIFI -> JoinWifiScreen(state, viewModel)
                Step.OPEN_PAGE -> OpenPageScreen(state, viewModel)
                Step.TRANSFER -> TransferScreen(state, viewModel, onPickFiles)
                Step.TROUBLESHOOT -> TroubleshootScreen(state, viewModel)
                Step.ERROR -> ErrorScreen(viewModel)
            }
            if (state.showStats) StatsOverlay(state, viewModel)
        }
    }

    if (state.confirmEnd) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissEndSession() },
            title = { Text("End the session?") },
            text = {
                Text(
                    "This turns off the GridDrop Wi-Fi. The iPhone will disconnect and reconnect " +
                        "to its normal internet Wi-Fi. Any transfer still in progress will stop.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.endSession() }) { Text("End & disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEndSession() }) { Text("Keep going") }
            },
        )
    }
}


@Composable
private fun ChooseRoleScreen(viewModel: MainViewModel) {
    ScreenColumn {
        Spacer(Modifier.height(24.dp))
        Text("GridDrop", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            "Share files with an iPhone —\nno app needed on the iPhone.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Text("What would you like to do?", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(20.dp))
        BigChoiceCard(
            icon = Icons.Filled.Download,
            title = "Send files",
            subtitle = "Pick files here — the iPhone downloads them",
        ) { viewModel.chooseRole(Role.SEND) }
        Spacer(Modifier.height(16.dp))
        BigChoiceCard(
            icon = Icons.Filled.CloudUpload,
            title = "Receive files",
            subtitle = "The iPhone sends files — they're saved to Downloads",
        ) { viewModel.chooseRole(Role.RECEIVE) }
        Spacer(Modifier.height(24.dp))
        StatsLink(viewModel)
    }
}

@Composable
private fun StatsLink(viewModel: MainViewModel) {
    TextButton(onClick = { viewModel.openStats() }) {
        Icon(Icons.Filled.Insights, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Stats for nerds", fontSize = 15.sp)
    }
}


@Composable
private fun ConnectingScreen() {
    ScreenColumn(center = true) {
        CircularProgressIndicator(Modifier.size(64.dp), strokeWidth = 6.dp)
        Spacer(Modifier.height(28.dp))
        Text("Setting up your private\nWi-Fi connection…", fontSize = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "This takes just a moment.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun JoinWifiScreen(state: UiState, viewModel: MainViewModel) {
    ScreenColumn {
        TopBackBar(label = "Choose") { viewModel.goHome() }
        StepHeader(current = 1, title = "Scan to join the Wi-Fi")
        Text(
            "On the iPhone, open the Camera and point it at this code. Tap the yellow “Join Network” bar that pops up.",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        val ssid = state.ssid
        if (ssid != null) {
            QrBlock(QrGenerator.wifiPayload(ssid, state.passphrase))
            Spacer(Modifier.height(16.dp))
            CredentialCard(ssid = ssid, pass = state.passphrase)
        }
        Spacer(Modifier.height(24.dp))
        BigPrimaryButton("Next", Icons.AutoMirrored.Filled.ArrowForward) { viewModel.next() }
        Spacer(Modifier.height(8.dp))
        TroubleshootLink(viewModel)
    }
}


@Composable
private fun OpenPageScreen(state: UiState, viewModel: MainViewModel) {
    ScreenColumn {
        TopBackBar { viewModel.back() }
        StepHeader(current = 2, title = "Scan to open the page")
        Text(
            "Still on the iPhone, scan this second code. Safari opens the GridDrop page automatically.",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        state.ipUrl?.let { url ->
            QrBlock(url)
            Spacer(Modifier.height(12.dp))
            Text(
                "or type this into Safari:",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(url, fontSize = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
        BigPrimaryButton("Next", Icons.AutoMirrored.Filled.ArrowForward) { viewModel.next() }
        Spacer(Modifier.height(4.dp))
        TroubleshootLink(viewModel)
    }
}


@Composable
private fun TransferScreen(state: UiState, viewModel: MainViewModel, onPickFiles: () -> Unit) {
    ScreenColumn {
        TopBackBar { viewModel.back() }
        StepHeader(current = 3, title = "Transfer your files")
        when (state.role) {
            Role.SEND -> {
                Text(
                    "Add the files you want to send. They appear on the iPhone's page, ready to download.",
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                BigPrimaryButton("Add files", Icons.Filled.Download, onClick = onPickFiles)
                Spacer(Modifier.height(16.dp))
                if (state.sharedFiles.isEmpty()) {
                    EmptyHint("No files added yet.")
                } else {
                    state.sharedFiles.forEach { f -> SendFileRow(f) { viewModel.removeShared(f.id) } }
                }
            }
            Role.RECEIVE -> {
                Text(
                    "On the iPhone's page, tap “Choose files”. Anything sent lands in this phone's Downloads.",
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                if (state.received.isEmpty()) {
                    EmptyHint("Waiting for the iPhone to send something…")
                } else {
                    Text("Saved to Downloads", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    state.received.forEach { r ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(r.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(r.location, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            null -> {}
        }
        Spacer(Modifier.height(28.dp))
        BigPrimaryButton("Done — disconnect iPhone", Icons.Filled.CheckCircle) { viewModel.requestEndSession() }
        Spacer(Modifier.height(4.dp))
        TroubleshootLink(viewModel)
        StatsLink(viewModel)
    }
}


@Composable
private fun TroubleshootScreen(state: UiState, viewModel: MainViewModel) {
    ScreenColumn {
        TopBackBar { viewModel.closeTroubleshoot() }
        Spacer(Modifier.height(12.dp))
        Icon(Icons.Filled.HelpOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("iPhone can't find the network?", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))

        if (viewModel.canForce5GHz) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quick fix for this phone", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Restart the network on the 5 GHz band, which more iPhones can see. " +
                            "You'll get fresh codes to scan.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    BigPrimaryButton("Switch to a more compatible network", Icons.Filled.Wifi) {
                        viewModel.tryCompatibleNetwork()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Text("Try these steps, in order:", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        InstructionStep(1, "On the iPhone, open Settings → Wi-Fi and make sure Wi-Fi is turned ON.")
        InstructionStep(2, "Look for a network with the exact name shown below and tap it.")
        InstructionStep(3, "If it asks for a password, type the password shown below.")
        InstructionStep(4, "Keep the two phones close together, then scan the codes again.")

        Spacer(Modifier.height(16.dp))
        val ssid = state.ssid
        if (ssid != null) CredentialCard(ssid = ssid, pass = state.passphrase)

        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Still stuck? Some phones create a 5 GHz network that very old iPhones can't see. " +
                    "As a last resort you can turn on this phone's normal Hotspot from Settings and set its " +
                    "band to 2.4 GHz — but for most iPhones the steps above are all you need.",
                fontSize = 14.sp,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        BigPrimaryButton("Back to setup", Icons.AutoMirrored.Filled.ArrowBack) { viewModel.closeTroubleshoot() }
    }
}


@Composable
private fun ErrorScreen(viewModel: MainViewModel) {
    ScreenColumn(center = true) {
        Text("Couldn't start the connection", fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Make sure Wi-Fi is turned on, then try again.",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        BigPrimaryButton("Try again", Icons.Filled.Wifi) { viewModel.retry() }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { viewModel.goHome() }) { Text("Start over", fontSize = 16.sp) }
    }
}


@Composable
private fun ScreenColumn(center: Boolean = false, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (center) Arrangement.Center else Arrangement.Top,
    ) { content() }
}

@Composable
private fun StepHeader(current: Int, title: String) {
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        (1..3).forEach { i ->
            Box(
                Modifier
                    .size(if (i == current) 14.dp else 10.dp)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape,
                    ),
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Step $current of 3", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun BigChoiceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TopBackBar(label: String = "Back", onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 16.sp)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BigPrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QrBlock(payload: String) {
    val image = remember(payload) { QrGenerator.encode(payload) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Image(image, contentDescription = null, modifier = Modifier.padding(16.dp).size(260.dp))
    }
}

@Composable
private fun CredentialCard(ssid: String, pass: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Network name", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ssid, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (!pass.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Password", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pass, fontSize = 22.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InstructionStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 17.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun TroubleshootLink(viewModel: MainViewModel) {
    TextButton(onClick = { viewModel.showTroubleshoot() }) {
        Icon(Icons.Filled.HelpOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("iPhone can't see the network?", fontSize = 15.sp)
    }
}


@Composable
private fun StatsOverlay(state: UiState, viewModel: MainViewModel) {
    val events by Diag.events.collectAsStateWithLifecycle()
    val sent by Diag.bytesSent.collectAsStateWithLifecycle()
    val received by Diag.bytesReceived.collectAsStateWithLifecycle()
    val bandMode by Diag.bandMode.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val info = (state.hotspot as? HotspotStatus.Running)?.info
    val statusText = when (val h = state.hotspot) {
        is HotspotStatus.Running -> "Running"
        HotspotStatus.Starting -> "Starting"
        HotspotStatus.Idle -> "Idle"
        HotspotStatus.Unsupported -> "Unsupported (needs Android 8+)"
        is HotspotStatus.Failed -> "Failed (reason ${h.reason})"
    }

    fun report(): String = buildString {
        appendLine("GridDrop stats")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Hotspot: $statusText")
        appendLine("Band mode: $bandMode")
        appendLine("Can force 5 GHz: ${if (viewModel.canForce5GHz) "yes" else "no"}")
        appendLine("Network name: ${state.ssid ?: "-"}")
        appendLine("Password: ${state.passphrase ?: "-"}")
        appendLine("Gateway IP: ${info?.gatewayIp ?: "-"}")
        appendLine("Role: ${state.role?.name ?: "-"}")
        appendLine("Page URL: ${state.ipUrl ?: "-"}")
        appendLine("Files shared: ${state.sharedFiles.size}")
        appendLine("Files received: ${state.received.size}")
        appendLine("Bytes sent: $sent")
        appendLine("Bytes received: $received")
        appendLine()
        appendLine("Event log:")
        events.forEach { appendLine(it) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Stats for nerds", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.closeStats() }) { Text("Close", fontSize = 16.sp) }
            }

            StatSection("Device")
            StatRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
            StatRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

            StatSection("Hotspot")
            StatRow("Status", statusText)
            StatRow("Band mode", bandMode)
            StatRow("Force 5 GHz", if (viewModel.canForce5GHz) "Available (Android 16+)" else "Not available")
            StatRow("Network name", state.ssid ?: "—")
            StatRow("Password", state.passphrase ?: "—")
            StatRow("Gateway IP", info?.gatewayIp ?: "—")

            StatSection("Session")
            StatRow("Role", state.role?.name ?: "—")
            StatRow("Page URL", state.ipUrl ?: "—")
            StatRow("Files shared", state.sharedFiles.size.toString())
            StatRow("Files received", state.received.size.toString())
            StatRow("Bytes sent", "${humanSize(sent)}  ($sent)")
            StatRow("Bytes received", "${humanSize(received)}  ($received)")

            StatSection("Event log")
            if (events.isEmpty()) {
                Text("No events yet.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                events.asReversed().take(80).forEach { line ->
                    Text(
                        line,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { clipboard.setText(AnnotatedString(report())) }, modifier = Modifier.weight(1f)) {
                    Text("Copy all")
                }
                TextButton(onClick = { Diag.clear() }) { Text("Clear log") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatSection(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp),
        )
        Text(value, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SendFileRow(f: SharedFile, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.displayName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(humanSize(f.size), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
