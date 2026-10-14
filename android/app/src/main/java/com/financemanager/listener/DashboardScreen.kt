package com.financemanager.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.service.SyncPhase
import com.financemanager.listener.service.YapeNotificationListenerService
import com.financemanager.listener.ui.theme.*
import com.financemanager.listener.worker.TransactionSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { AppDatabase.getDatabase(context).transactionDao() }
    var permission by remember { mutableStateOf(hasNotificationPermission(context)) }
    var session by remember { mutableStateOf(PairingPreferences.session(context)) }
    var email by remember { mutableStateOf(PairingPreferences.getPairedEmail(context)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var unpair by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(MovementStatus.ALL.name) }
    var startDay by rememberSaveable { mutableStateOf<String?>(null) }
    var endDay by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocalTransactionEntity?>(null) }
    var assignment by remember { mutableStateOf<LocalTransactionEntity?>(null) }
    var assigning by remember { mutableStateOf(false) }
    val capture by CaptureStatus.state.collectAsState()
    val ownedFlow = remember(session?.ownerKey) { dao.getTransactionsFlow(session?.ownerKey) }
    val owned by key(session?.ownerKey) { ownedFlow.collectAsState(emptyList()) }
    val unassigned by remember { dao.unassignedTransactions() }.collectAsState(emptyList())
    val phoneMovements = remember(owned, unassigned) {
        (owned + unassigned).sortedWith(compareByDescending<LocalTransactionEntity> { it.transactionDate }.thenByDescending { it.id })
    }
    val pendingCount = owned.count { !it.isSynced }
    val destinationCount = unassigned.count { !it.isSynced }
    val status = MovementStatus.valueOf(statusName)
    val rows = phoneMovements
    val filtered = remember(rows, query, status, startDay, endDay) {
        filterMovements(rows, query, status, startDay?.let(LocalDate::parse), endDay?.let(LocalDate::parse))
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission = hasNotificationPermission(context)
                val updated = PairingPreferences.session(context)
                if (session?.ownerKey != updated?.ownerKey) { selected = null; assignment = null }
                session = updated
                email = PairingPreferences.getPairedEmail(context)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = tab != 0) { tab = 0 }
    fun openMovements(next: MovementStatus = MovementStatus.ALL) {
        statusName = next.name; query = ""; startDay = null; endDay = null; tab = 1
    }
    Scaffold(modifier = modifier, bottomBar = {
        NavigationBar(containerColor = Color.White) {
            listOf("Inicio", "Movimientos", "Ajustes").forEachIndexed { index, label ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index },
                    icon = { NavigationMark(index, if (tab == index) BrandBlue else Muted) }, label = { Text(label) })
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.fintrack_mark), contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (tab == 0) "FinTrack" else if (tab == 1) "Movimientos" else "Ajustes",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            if (tab == 0) {
                item { Text("Tus movimientos de Yape", style = MaterialTheme.typography.titleLarge)
                    Text("Se guardan en este teléfono antes de enviarse a la web.", color = Muted) }
                if (session == null || !permission) item {
                    AppPanel {
                        Text("Configura tu teléfono", fontWeight = FontWeight.SemiBold)
                        Text("1. Conecta este teléfono con la web", color = if (session == null) Ink else Positive)
                        if (session == null) Button(onClick = { pairing = true }) { Text("Escanear QR de la web") }
                        else Text("Destino de envío configurado", color = Positive)
                        Text("2. Activa el acceso a notificaciones", color = if (!permission) Ink else Positive)
                        if (!permission) OutlinedButton(onClick = { openNotificationSettings(context) }) { Text("Activar acceso") }
                        else Text("Acceso habilitado", color = Positive)
                    }
                }
                item {
                    AppPanel {
                        Text("Captura de Yape", fontWeight = FontWeight.SemiBold)
                        Text(when { !permission -> "Acceso a notificaciones desactivado"; capture.connected -> "Acceso a notificaciones conectado"; else -> "Esperando conexión con Android" },
                            color = if (permission && capture.connected) Positive else Ink)
                        Text("Último movimiento guardado: ${formatDiagnosticTime(capture.lastSaved)}", color = Muted)
                        if (permission && capture.connected) Text("Lista para recibir la próxima notificación. No necesitas mantener FinTrack abierta.", color = Muted)
                        if (permission && !capture.connected) OutlinedButton(onClick = { YapeNotificationListenerService.reconnect(context) }) { Text("Intentar reconectar") }
                        capture.lastError?.let { Text(it, color = Negative) }
                        TextButton(onClick = { tab = 2; diagnostics = true }) { Text("Ver diagnóstico") }
                    }
                }
                if (session != null) item {
                    AppPanel {
                        Text("Envío a la web", fontWeight = FontWeight.SemiBold)
                        Text(if (pendingCount == 0) "No hay movimientos por enviar al destino actual" else "$pendingCount movimientos por enviar", style = MaterialTheme.typography.titleMedium)
                        Text(capture.syncMessage, color = Muted)
                        if (pendingCount > 0) {
                            Text("Los movimientos siguen guardados si no hay conexión.", color = Muted)
                            if (capture.syncPhase == SyncPhase.AUTH_REQUIRED) Button(onClick = { pairing = true }) { Text("Conectar de nuevo con la web") }
                            else Button(onClick = { TransactionSyncWorker.enqueue(context) }, enabled = capture.syncPhase != SyncPhase.SENDING) {
                                Text(if (capture.syncPhase == SyncPhase.SENDING) "Enviando…" else "Enviar ahora")
                            }
                        }
                    }
                }
                if (destinationCount > 0) item {
                    AppPanel {
                        Text("$destinationCount movimientos necesitan confirmar destino", fontWeight = FontWeight.SemiBold)
                        Text("Están guardados en este teléfono. Confirma que son tuyos y a dónde enviarlos.", color = Muted)
                        TextButton(onClick = { openMovements(MovementStatus.UNASSIGNED) }) { Text("Revisar movimientos") }
                    }
                }
                item { Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Últimos movimientos del teléfono", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { openMovements() }) { Text("Ver todos") }
                } }
                if (phoneMovements.isEmpty()) item { AppPanel {
                    Text("No hay movimientos locales disponibles para mostrar.")
                    Text("Aquí aparecen las notificaciones reconocidas en este teléfono. El historial de la web no se descarga y los registros de otros destinos de envío no se incluyen.", color = Muted)
                } }
                items(phoneMovements.take(5).size, key = { phoneMovements[it].transactionHash }) { index -> TransactionRow(phoneMovements[index]) { selected = phoneMovements[index] } }
            } else if (tab == 1) {
                item {
                    Text("Movimientos del teléfono", fontWeight = FontWeight.SemiBold)
                    Text(if (status == MovementStatus.UNASSIGNED) "Guardados aquí; necesitan confirmar el destino antes de enviarse." else "Incluye los del destino actual y los que necesitan confirmar destino. El historial de la web no se descarga.", color = Muted)
                    OutlinedTextField(query, { query = it }, label = { Text("Buscar nombre o importe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MovementStatus.entries.forEach { next -> FilterChip(selected = status == next, onClick = { statusName = next.name }, label = { Text(next.label) }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(if (startDay == null) "Elegir fechas" else dateRangeLabel(startDay!!, endDay ?: startDay!!))
                        }
                        if (startDay != null) TextButton(onClick = { startDay = null; endDay = null }) { Text("Quitar") }
                    }
                    Text("${filtered.size} movimientos", color = Muted)
                }
                if (filtered.isEmpty()) item { AppPanel {
                    Text(if (status == MovementStatus.UNASSIGNED) "No hay movimientos que necesiten confirmar destino" else "No hay movimientos para esta selección", fontWeight = FontWeight.SemiBold)
                    Text("Puedes cambiar el estado, la búsqueda o las fechas.", color = Muted)
                    TextButton(onClick = { openMovements() }) { Text("Quitar filtros") }
                } }
                filtered.forEachIndexed { index, row ->
                    val day = row.transactionDate.substringBefore('T')
                    if (index == 0 || filtered[index - 1].transactionDate.substringBefore('T') != day) item(key = "day-$day") {
                        Text(formatTransactionDate(row.transactionDate, "d 'de' MMMM 'de' yyyy"), color = Muted, fontSize = 13.sp)
                    }
                    item(key = row.transactionHash) { TransactionRow(row) { selected = row } }
                }
            } else {
                item { AppPanel {
                    Text("Conexión con la web", fontWeight = FontWeight.SemiBold)
                    Text(email ?: if (session == null) "Sin destino de envío configurado" else "Destino de envío configurado", color = Muted)
                    Text("El QR o código de vinculación indica a qué usuario de la web se enviarán los movimientos. La captura en el teléfono funciona por separado.", color = Muted)
                    if (session == null) Button(onClick = { pairing = true }) { Text("Conectar con QR") }
                    else OutlinedButton(onClick = { unpair = true }) { Text("Desvincular teléfono") }
                } }
                item { AppPanel {
                    Text("Acceso a notificaciones", fontWeight = FontWeight.SemiBold)
                    Text(if (permission) "Habilitado" else "Desactivado", color = Muted)
                    OutlinedButton(onClick = { openNotificationSettings(context) }) { Text(if (permission) "Revisar acceso" else "Activar acceso") }
                    if (permission && !capture.connected) OutlinedButton(onClick = { YapeNotificationListenerService.reconnect(context) }) { Text("Intentar reconectar") }
                } }
                item { AppPanel {
                    TextButton(onClick = { diagnostics = !diagnostics }) { Text(if (diagnostics) "Ocultar diagnóstico" else "Ver diagnóstico de captura") }
                    if (diagnostics) {
                        Text("Última conexión: ${formatDiagnosticTime(capture.lastConnection)}", color = Muted)
                        Text("Última notificación recibida: ${formatDiagnosticTime(capture.lastNotification)}", color = Muted)
                        Text("Último movimiento guardado: ${formatDiagnosticTime(capture.lastSaved)}", color = Muted)
                        Text("Recibir una notificación no significa que se haya reconocido un pago. Los avisos promocionales también pueden descartarse.", color = Muted)
                        OutlinedButton(onClick = { YapeNotificationListenerService.reviewActive() }, enabled = permission && capture.connected) { Text("Revisar notificaciones aún visibles") }
                        if (capture.history.isEmpty()) Text("Aún no hay actividad registrada.", color = Muted)
                        capture.history.forEach { event ->
                            HorizontalDivider(color = Line)
                            Text(event.event.label, fontSize = 14.sp)
                            Text(formatDiagnosticTime(event.time), color = Muted, fontSize = 12.sp)
                        }
                        Text("Se conservan los últimos 30 eventos, sin nombres, importes ni códigos de seguridad.", color = Muted, fontSize = 12.sp)
                    }
                } }
                item { Text("FinTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Muted, fontSize = 12.sp) }
            }
        }
    }
    if (datePicker) {
        val range = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDay?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() },
            initialSelectedEndDateMillis = endDay?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() })
        DatePickerDialog(onDismissRequest = { datePicker = false }, confirmButton = {
            TextButton(enabled = range.selectedStartDateMillis != null, onClick = {
                startDay = range.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                endDay = (range.selectedEndDateMillis ?: range.selectedStartDateMillis)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                datePicker = false
            }) { Text("Aplicar fechas") }
        }, dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancelar") } }) {
            DateRangePicker(state = range, modifier = Modifier.heightIn(max = 480.dp), title = { Text("Selecciona un día o un rango", Modifier.padding(16.dp)) })
        }
    }
    selected?.let { original ->
        val row = (owned + unassigned).find { it.id == original.id }
        if (row == null) LaunchedEffect(original.id) { selected = null }
        else AlertDialog(onDismissRequest = { selected = null }, title = { Text("Detalle del movimiento") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatAmount(row), style = MaterialTheme.typography.headlineMedium, color = if (row.flowType == "INCOME") Positive else Ink)
                Text(row.contactName, fontWeight = FontWeight.SemiBold)
                Text(formatTransactionDate(row.transactionDate, "d MMM yyyy, HH:mm"), color = Muted)
                Text("Origen: ${row.channel}")
                Text("Estado: ${movementLabel(row)}")
                Text(when {
                    row.ownerKey == null && row.isSynced -> "Destino: no consta en este registro antiguo"
                    row.ownerKey == null -> "Destino: necesita confirmación"
                    else -> "Destino en la web: ${email ?: "destino configurado"}"
                })
                Text(when {
                    row.isSynced && row.ownerKey == null -> "Este registro antiguo figura como enviado. No se enviará de nuevo."
                    row.isSynced -> "La web confirmó la recepción de este movimiento."
                    else -> "El movimiento está guardado en este teléfono."
                }, color = Muted)
                if (row.ownerKey == null && !row.isSynced) {
                    if (session != null) Button(onClick = { assignment = row; selected = null }) { Text("Confirmar destino de envío") }
                    else Text("Conecta el teléfono con la web desde Ajustes para elegir el destino de envío.", color = Muted)
                }
            }
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } })
    }
    assignment?.let { row ->
        AlertDialog(onDismissRequest = { if (!assigning) assignment = null }, title = { Text("Confirmar destino de envío") },
            text = { Text("Confirma que este movimiento de S/ ${row.amount} de ${row.contactName} es tuyo y que quieres enviarlo a ${email ?: "el usuario conectado en la web"}.") },
            confirmButton = { TextButton(enabled = !assigning && session != null, onClick = {
                val expected = session
                assigning = true
                scope.launch {
                    try {
                        val changed = withContext(Dispatchers.IO) {
                            if (expected != null && PairingPreferences.session(context) == expected) dao.assignReviewedEntry(row.id, expected.ownerKey) else 0
                        }
                        if (changed == 1) TransactionSyncWorker.enqueue(context)
                        assignment = null
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { CaptureStatus.error("No se pudo confirmar el destino. El movimiento sigue guardado en el teléfono.") }
                    finally { assigning = false }
                }
            }) { Text(if (assigning) "Confirmando…" else "Confirmar y enviar") } },
            dismissButton = { TextButton(enabled = !assigning, onClick = { assignment = null }) { Text("Cancelar") } })
    }
    if (pairing) PairingDialog(onDismiss = { pairing = false }, onSuccess = {
        email = it; session = PairingPreferences.session(context); pairing = false
        selected = null; assignment = null; openMovements(); tab = 0
    })
    if (unpair) AlertDialog(onDismissRequest = { unpair = false }, title = { Text("Desvincular teléfono") },
        text = { Text("Se detendrá el envío a ${email ?: "el destino configurado en la web"}. Los movimientos guardados se conservarán en este teléfono.") },
        confirmButton = { TextButton(onClick = {
            PairingPreferences.clearPairing(context); TransactionSyncWorker.cancel(context); ApiClient.resetService()
            CaptureStatus.sync("Conecta el teléfono con la web para enviar movimientos.")
            session = null; email = null; unpair = false; selected = null; assignment = null; statusName = MovementStatus.ALL.name
        }) { Text("Desvincular", color = Negative) } }, dismissButton = { TextButton(onClick = { unpair = false }) { Text("Cancelar") } })
}

@Composable
private fun AppPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable
private fun TransactionRow(row: LocalTransactionEntity, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(row.contactName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${row.channel} · ${formatTransactionDate(row.transactionDate, "HH:mm")}", color = Muted, fontSize = 12.sp)
            }
            Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(formatAmount(row), color = if (row.flowType == "INCOME") Positive else Ink, fontWeight = FontWeight.SemiBold)
                Text(movementLabel(row), color = Muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(Modifier.padding(top = 14.dp), color = Line)
    }
}
@Composable
private fun NavigationMark(index: Int, color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val unit = size.width / 24
        if (index == 0) {
            val house = Path().apply { moveTo(3 * unit, 11 * unit); lineTo(12 * unit, 3 * unit); lineTo(21 * unit, 11 * unit); moveTo(6 * unit, 10 * unit); lineTo(6 * unit, 21 * unit); lineTo(18 * unit, 21 * unit); lineTo(18 * unit, 10 * unit) }
            drawPath(house, color, style = Stroke(1.8f * unit))
        } else if (index == 1) {
            listOf(6f, 12f, 18f).forEach { y -> drawLine(color, Offset(4 * unit, y * unit), Offset(20 * unit, y * unit), 1.8f * unit) }
        } else {
            listOf(6f, 12f, 18f).forEachIndexed { i, y ->
                drawLine(color, Offset(3 * unit, y * unit), Offset(21 * unit, y * unit), 1.8f * unit)
                val center = Offset((if (i == 1) 15 else 8) * unit, y * unit)
                drawCircle(Color.White, 3 * unit, center)
                drawCircle(color, 3 * unit, center, style = Stroke(1.8f * unit))
            }
        }
    }
}
private fun movementLabel(row: LocalTransactionEntity) = if (row.isSynced) "Enviado" else if (row.ownerKey == null) "Necesita confirmar destino" else "Por enviar"
private fun formatAmount(row: LocalTransactionEntity): String {
    val amount = row.amount.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: row.amount
    return "${if (row.flowType == "INCOME") "+" else "−"} S/ $amount"
}
private fun formatTransactionDate(value: String, pattern: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-PE")))
}.getOrDefault(value.substringBefore('.').replace('T', ' '))
private fun formatDiagnosticTime(value: Long?): String = value?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", Locale.forLanguageTag("es-PE")))
} ?: "Sin registro todavía"
private fun dateRangeLabel(start: String, end: String): String {
    val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-PE"))
    val first = LocalDate.parse(start).format(format)
    return if (start == end) first else "$first — ${LocalDate.parse(end).format(format)}"
}
private fun hasNotificationPermission(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val component = ComponentName(context, YapeNotificationListenerService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
}
private fun openNotificationSettings(context: Context) { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
