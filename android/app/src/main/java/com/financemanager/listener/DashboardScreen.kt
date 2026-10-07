package com.financemanager.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.financemanager.listener.data.AppDatabase
import com.financemanager.listener.data.LocalTransactionEntity
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.service.YapeNotificationListenerService
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.ui.theme.*
import com.financemanager.listener.worker.TransactionSyncWorker
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var paired by remember { mutableStateOf(PairingPreferences.isPaired(context)) }
    var email by remember { mutableStateOf(PairingPreferences.getPairedEmail(context)) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var pairingVisible by rememberSaveable { mutableStateOf(false) }
    var unpairVisible by rememberSaveable { mutableStateOf(false) }
    var reviewUnassigned by rememberSaveable { mutableStateOf(false) }
    var entryToAssign by remember { mutableStateOf<LocalTransactionEntity?>(null) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(PairingPreferences.session(context)) }
    val database = remember { AppDatabase.getDatabase(context) }
    val dao = database.transactionDao()
    val transactionFlow = remember(session?.ownerKey, reviewUnassigned) {
        if (reviewUnassigned) dao.unassignedTransactions() else dao.getRecentTransactionsFlow(session?.ownerKey)
    }
    val transactions = key(session?.ownerKey, reviewUnassigned) {
        val rows by transactionFlow.collectAsState(emptyList())
        rows
    }
    val pendingFlow = remember(session?.ownerKey) { dao.pendingCount(session?.ownerKey) }
    val pendingCount by pendingFlow.collectAsState(0)
    val unassignedFlow = remember { dao.unassignedCount() }
    val unassignedCount by unassignedFlow.collectAsState(0)
    val capture by CaptureStatus.state.collectAsState()
    val connectionLabel = when {
        !permissionGranted -> "Permiso deshabilitado"
        capture.connected -> "Servicio conectado"
        else -> "Esperando conexión"
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = hasNotificationPermission(context)
                paired = PairingPreferences.isPaired(context)
                email = PairingPreferences.getPairedEmail(context)
                session = PairingPreferences.session(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = settingsVisible) { settingsVisible = false }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = BrandBlue, shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text("W", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FinTrack", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tu dinero en movimiento", fontSize = 12.sp, color = Muted)
                }
                TextButton(onClick = { settingsVisible = !settingsVisible }) {
                    Text(if (settingsVisible) "Volver" else "Ajustes")
                }
            }
        }
        if (settingsVisible) {
            item { Text("Ajustes", style = MaterialTheme.typography.headlineSmall) }
            item {
                AppPanel {
                    SectionLabel("CUENTA")
                    Text(if (paired) "Cuenta vinculada" else "Vincula tu cuenta", fontWeight = FontWeight.SemiBold)
                    Text(email ?: "Escanea el QR de FinTrack para sincronizar tus movimientos.", color = Muted)
                    if (paired) {
                        OutlinedButton(onClick = { unpairVisible = true }) { Text("Desvincular dispositivo") }
                    } else {
                        Button(onClick = { pairingVisible = true }) { Text("Vincular cuenta") }
                    }
                }
            }
            item {
                AppPanel {
                    SectionLabel("NOTIFICACIONES")
                    Text(if (permissionGranted) "Permiso habilitado" else "Permiso pendiente", fontWeight = FontWeight.SemiBold)
                    Text(connectionLabel, color = Muted)
                    Text("Última conexión: ${formatDiagnosticTime(capture.lastConnection)}", color = Muted)
                    Text("Última notificación de Yape: ${formatDiagnosticTime(capture.lastNotification)}", color = Muted)
                    Text("Último guardado: ${formatDiagnosticTime(capture.lastSaved)}", color = Muted)
                    capture.lastError?.let { Text(it, color = Negative) }
                    Text(capture.syncMessage, color = Muted)
                    OutlinedButton(onClick = { YapeNotificationListenerService.reviewActive() },
                        enabled = permissionGranted && capture.connected) { Text("Revisar notificaciones activas") }
                    OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Revisar permiso") }
                }
            }
            item {
                AppPanel {
                    SectionLabel("ACERCA DE")
                    Text("FinTrack", fontWeight = FontWeight.SemiBold)
                    Text("Versión ${BuildConfig.VERSION_NAME} · Compilación ${BuildConfig.VERSION_CODE}", color = Muted)
                    Text("Los movimientos se guardan en este teléfono antes de sincronizarse.", color = Muted)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("TU TELÉFONO")
                    Text("Captura de movimientos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Tus notificaciones de Yape, en un solo lugar.", fontSize = 13.sp, color = Muted)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
                    Column {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(8.dp).background(if (permissionGranted && capture.connected) Positive else Negative, RoundedCornerShape(4.dp)))
                                Text(connectionLabel, fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (permissionGranted) "${pendingCount} movimientos pendientes de esta cuenta" else "Permite que FinTrack lea las notificaciones de Yape.", color = Muted, fontSize = 12.sp)
                            capture.lastError?.let { Text(it, color = Negative, fontSize = 12.sp) }
                            Text(capture.syncMessage, color = Muted, fontSize = 12.sp)
                            HorizontalDivider(color = Line)
                            Text("Último guardado: ${formatDiagnosticTime(capture.lastSaved)}", color = Muted, fontSize = 12.sp)
                            if (!permissionGranted) {
                                Button(onClick = { openNotificationSettings(context) }) { Text("Habilitar permiso") }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(4.dp).background(BrandBlue))
                    }
                }
            }
            if (!paired) {
                item {
                    AppPanel {
                        Text("Conecta tu cuenta", fontWeight = FontWeight.SemiBold)
                        Text("Vincula este teléfono para enviar tus movimientos a la web.", color = Muted, fontSize = 13.sp)
                        Button(onClick = { pairingVisible = true }) { Text("Vincular cuenta") }
                    }
                }
            }
            item {
                if (unassignedCount > 0) {
                    AppPanel {
                        Text("$unassignedCount movimientos sin cuenta comprobada", fontWeight = FontWeight.SemiBold)
                        Text("Se conservan en este teléfono y no se enviarán automáticamente. Incluye registros antiguos o capturados sin vinculación.", color = Muted)
                        TextButton(onClick = { reviewUnassigned = !reviewUnassigned }) {
                            Text(if (reviewUnassigned) "Ver cuenta vinculada" else "Revisar registros locales")
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Movimientos", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("Últimos ${transactions.size} guardados en este teléfono", fontSize = 12.sp, color = Muted)
                    }
                    TextButton(onClick = {
                        TransactionSyncWorker.enqueue(context)
                        Toast.makeText(context, "Sincronización encolada", Toast.LENGTH_SHORT).show()
                    }, enabled = session != null && pendingCount > 0) { Text("Sincronizar") }
                }
            }
            if (transactions.isEmpty()) {
                item {
                    AppPanel {
                        Text("Aún no hay movimientos", fontWeight = FontWeight.SemiBold)
                        Text("Cuando se capture una notificación de Yape, aparecerá aquí.", color = Muted)
                    }
                }
            }
            transactions.forEachIndexed { index, transaction ->
                val day = formatTransactionDate(transaction.transactionDate, "yyyy-MM-dd")
                val previousDay = transactions.getOrNull(index - 1)?.let { formatTransactionDate(it.transactionDate, "yyyy-MM-dd") }
                if (day != previousDay) {
                    item(key = "date-${transaction.transactionHash}") { SectionLabel(formatTransactionDate(transaction.transactionDate, "d 'de' MMMM 'de' yyyy").uppercase(Locale.forLanguageTag("es-PE"))) }
                }
                item(key = transaction.transactionHash) {
                    TransactionRow(transaction)
                    if (reviewUnassigned && !transaction.isSynced && session != null) {
                        TextButton(onClick = { entryToAssign = transaction }) { Text("Asignar a mi cuenta") }
                    }
                }
            }
        }
        item {
            HorizontalDivider(color = Line)
            Text("FinTrack · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", modifier = Modifier.fillMaxWidth().padding(top = 14.dp), color = Muted, fontSize = 12.sp)
        }
    }

    entryToAssign?.let { entry ->
        AlertDialog(onDismissRequest = { entryToAssign = null },
            title = { Text("Confirmar propietario") },
            text = { Text("Confirma que el movimiento de S/ ${entry.amount} de ${entry.contactName} pertenece a $email. Se enviará a esa cuenta conservando su identificador.") },
            confirmButton = {
                TextButton(onClick = {
                    val expected = session
                    entryToAssign = null
                    scope.launch {
                        try {
                            val assigned = withContext(Dispatchers.IO) {
                                if (expected != null && PairingPreferences.session(context) == expected)
                                    dao.assignReviewedEntry(entry.id, expected.ownerKey) else 0
                            }
                            if (assigned == 1) TransactionSyncWorker.enqueue(context)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            CaptureStatus.error("No se pudo asignar el movimiento. Sigue guardado sin cuenta.")
                        }
                    }
                }) { Text("Confirmar y sincronizar") }
            }, dismissButton = { TextButton(onClick = { entryToAssign = null }) { Text("Cancelar") } })
    }
    if (pairingVisible) {
        PairingDialog(onDismiss = { pairingVisible = false }, onSuccess = {
            email = it
            paired = true
            session = PairingPreferences.session(context)
            reviewUnassigned = false
            pairingVisible = false
        })
    }
    if (unpairVisible) {
        AlertDialog(
            onDismissRequest = { unpairVisible = false },
            title = { Text("Desvincular dispositivo") },
            text = { Text("Se dejarán de sincronizar los movimientos con $email hasta que vuelvas a vincular este teléfono.") },
            confirmButton = {
                TextButton(onClick = {
                    PairingPreferences.clearPairing(context)
                    TransactionSyncWorker.cancel(context)
                    ApiClient.resetService()
                    session = null
                    paired = false
                    email = null
                    unpairVisible = false
                }) { Text("Desvincular", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { unpairVisible = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AppPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, color = Muted)
}

@Composable
private fun TransactionRow(transaction: LocalTransactionEntity) {
    val income = transaction.flowType == "INCOME"
    val amount = transaction.amount.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: transaction.amount
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = if (income) SoftGreen else Negative.copy(alpha = 0.08f), shape = RoundedCornerShape(10.dp)) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(if (income) "↙" else "↗", color = if (income) Positive else Negative, fontSize = 20.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(transaction.contactName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${transaction.channel} · ${formatTransactionDate(transaction.transactionDate, "HH:mm")}", color = Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${if (income) "+" else "−"} S/ $amount", color = if (income) Positive else Negative,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
                Text(if (transaction.isSynced) "Sincronizado" else if (transaction.ownerKey == null) "Requiere revisión" else "Pendiente", color = Muted, fontSize = 11.sp)
            }
        }
        HorizontalDivider(color = Line)
    }
}

private fun formatTransactionDate(value: String, pattern: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-PE")))
}.getOrDefault(value.replace('T', ' ').substringBefore('.'))

private fun formatDiagnosticTime(value: Long?): String = value?.let {
    java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM HH:mm:ss", Locale.forLanguageTag("es-PE")))
} ?: "Sin registro en esta ejecución"

private fun hasNotificationPermission(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, YapeNotificationListenerService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}
