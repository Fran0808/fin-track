package com.financemanager.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
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
import kotlinx.coroutines.delay
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
    var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    var session by remember { mutableStateOf(PairingPreferences.session(context)) }
    var email by remember { mutableStateOf(PairingPreferences.getPairedEmail(context)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var pairing by rememberSaveable { mutableStateOf(false) }
    var unpair by rememberSaveable { mutableStateOf(false) }
    var changeServer by rememberSaveable { mutableStateOf(false) }
    var diagnostics by rememberSaveable { mutableStateOf(false) }
    var inboxReview by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(MovementStatus.ALL.name) }
    var startDay by rememberSaveable { mutableStateOf<String?>(null) }
    var endDay by rememberSaveable { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocalTransactionEntity?>(null) }
    var assignment by remember { mutableStateOf<LocalTransactionEntity?>(null) }
    var confirmAllDialog by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val capture by CaptureStatus.state.collectAsState()
    val inboxFlow = remember(session?.ownerKey) { AppDatabase.getDatabase(context).captureInboxDao().counts(session?.ownerKey) }
    val inboxCounts by key(session?.ownerKey) { inboxFlow.collectAsState(com.financemanager.listener.data.InboxCounts(0, 0)) }
    val reviewFlow = remember(session?.ownerKey) { AppDatabase.getDatabase(context).captureInboxDao().review(session?.ownerKey) }
    val reviewCaptures by key(session?.ownerKey) { reviewFlow.collectAsState(emptyList()) }
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
                batteryUnrestricted = isBatteryUnrestricted(context)
                val updated = PairingPreferences.session(context)
                if (session?.ownerKey != updated?.ownerKey) { selected = null; assignment = null; confirmAllDialog = false }
                session = updated
                email = PairingPreferences.getPairedEmail(context)
                com.financemanager.listener.service.ListenerHealthCoordinator.foreground(context)
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            com.financemanager.listener.service.ListenerHealthCoordinator.foreground(context)
        }
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = tab != 0) { tab = 0 }
    fun openMovements(next: MovementStatus = MovementStatus.ALL) {
        statusName = next.name; query = ""; startDay = null; endDay = null; tab = 1
    }
    fun triggerPullRefresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            permission = hasNotificationPermission(context)
            batteryUnrestricted = isBatteryUnrestricted(context)
            com.financemanager.listener.service.ListenerHealthCoordinator.foreground(context)
            if (session != null && pendingCount > 0 && capture.syncPhase != SyncPhase.SENDING) {
                TransactionSyncWorker.enqueue(context, manual = true)
            }
            delay(500)
            refreshing = false
        }
    }

    val isReady = permission && capture.connected && session != null
    val needsAttention = !permission || capture.syncPhase == SyncPhase.AUTH_REQUIRED || inboxCounts.review > 0 || capture.lastError != null

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                listOf("Inicio", "Movimientos", "Ajustes").forEachIndexed { index, label ->
                    val isSelected = tab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { tab = index },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SoftBrand,
                            selectedIconColor = BrandBlue,
                            selectedTextColor = BrandBlue,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        ),
                        icon = { NavigationMark(index, isSelected, if (isSelected) BrandBlue else Muted) },
                        label = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium) }
                    )
                }
            }
        }
    ) { padding ->
        val listContent: @Composable () -> Unit = {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.fintrack_mark), contentDescription = null, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (tab == 0) "FinTrack" else if (tab == 1) "Movimientos" else "Ajustes",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink
                            )
                        }
                        if (tab == 0 && isReady && pendingCount == 0 && !needsAttention) {
                            StatusPill(text = "Activo", contentColor = Positive, containerColor = SoftGreen)
                        }
                    }
                }
                if (tab == 0) {
                    if (session == null || !permission) {
                        item {
                            AppPanel {
                                Text("Configura tu teléfono", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text("Completa estos 2 pasos para capturar y enviar tus movimientos automáticamente.", fontSize = 13.sp, color = Muted)
                                SetupStepRow(
                                    stepNumber = 1,
                                    title = "Conecta este teléfono con la web",
                                    completed = session != null,
                                    completedText = "Destino de envío configurado",
                                    actionLabel = "Escanear QR de la web",
                                    primaryAction = true,
                                    onAction = { pairing = true }
                                )
                                HorizontalDivider(color = Line)
                                SetupStepRow(
                                    stepNumber = 2,
                                    title = "Activa el acceso a notificaciones",
                                    completed = permission,
                                    completedText = "Acceso habilitado",
                                    actionLabel = "Activar acceso",
                                    primaryAction = false,
                                    onAction = { openNotificationSettings(context) }
                                )
                            }
                        }
                    }

                    // Unified status card
                    item {
                        val statusTitle: String
                        val statusColor: Color
                        val statusBg: Color
                        when {
                            !permission -> {
                                statusTitle = "Acceso desactivado"
                                statusColor = Negative
                                statusBg = SoftNegative
                            }
                            capture.syncPhase == SyncPhase.AUTH_REQUIRED -> {
                                statusTitle = "Reconexión requerida"
                                statusColor = Negative
                                statusBg = SoftNegative
                            }
                            inboxCounts.review > 0 -> {
                                statusTitle = "${inboxCounts.review} por revisar"
                                statusColor = Negative
                                statusBg = SoftNegative
                            }
                            !capture.connected -> {
                                statusTitle = "Esperando a Android"
                                statusColor = Warning
                                statusBg = SoftWarning
                            }
                            pendingCount > 0 -> {
                                statusTitle = "$pendingCount por enviar"
                                statusColor = BrandBlue
                                statusBg = SoftBrand
                            }
                            session == null -> {
                                statusTitle = "Solo local"
                                statusColor = Warning
                                statusBg = SoftWarning
                            }
                            else -> {
                                statusTitle = "Todo listo · Capturando"
                                statusColor = Positive
                                statusBg = SoftGreen
                            }
                        }

                        AppPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusPill(text = statusTitle, contentColor = statusColor, containerColor = statusBg)
                                TextButton(
                                    onClick = { tab = 2; diagnostics = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("Diagnóstico", fontSize = 13.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MetricTile(
                                    label = "Por enviar",
                                    value = if (session == null) "Sin vincular" else if (pendingCount == 0) "Al día" else "$pendingCount pendientes",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    label = "Último movimiento",
                                    value = formatDiagnosticTime(capture.lastSaved),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (permission && capture.connected && pendingCount == 0 && session != null && !needsAttention) {
                                Text(
                                    "Lista para recibir la próxima notificación. No necesitas mantener FinTrack abierta.",
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            } else if (session != null && capture.syncMessage.isNotBlank()) {
                                Text(capture.syncMessage, fontSize = 13.sp, color = Muted)
                            }

                            if (inboxCounts.pending > 0) {
                                Text("${inboxCounts.pending} capturas pendientes de procesar", fontSize = 13.sp, color = Muted)
                            }
                            if (inboxCounts.review > 0) {
                                Text(
                                    "${inboxCounts.review} capturas por revisar; requieren atención y no se enviarán como movimientos nuevos.",
                                    fontSize = 13.sp,
                                    color = Negative
                                )
                                OutlinedButton(
                                    onClick = { tab = 2; diagnostics = true; inboxReview = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Ver capturas por revisar")
                                }
                            }
                            capture.lastError?.let { Text(it, fontSize = 13.sp, color = Negative) }

                            if (permission && !capture.connected) {
                                OutlinedButton(
                                    onClick = { YapeNotificationListenerService.reconnect(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Intentar reconectar")
                                }
                            }
                            if (session != null && pendingCount > 0) {
                                if (capture.syncPhase == SyncPhase.AUTH_REQUIRED) {
                                    Button(onClick = { pairing = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Conectar de nuevo con la web")
                                    }
                                } else {
                                    Button(
                                        onClick = { TransactionSyncWorker.enqueue(context, manual = true) },
                                        enabled = capture.syncPhase != SyncPhase.SENDING,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(if (capture.syncPhase == SyncPhase.SENDING) "Enviando…" else "Enviar ahora ($pendingCount)")
                                    }
                                }
                            }
                        }
                    }

                    if (destinationCount > 0) {
                        item {
                            AppPanel {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(text = "$destinationCount sin destino", contentColor = Warning, containerColor = SoftWarning)
                                }
                                Text(
                                    "Están guardados en este teléfono. Confirma que son tuyos para enviarlos a tu cuenta web.",
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { openMovements(MovementStatus.UNASSIGNED) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Revisar")
                                    }
                                    if (session != null && destinationCount > 1) {
                                        Button(
                                            onClick = { confirmAllDialog = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Confirmar todos")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Últimos movimientos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                            TextButton(onClick = { openMovements() }) { Text("Ver todos") }
                        }
                    }

                    if (phoneMovements.isEmpty()) {
                        item {
                            AppPanel {
                                Text("No hay movimientos locales todavía.", fontWeight = FontWeight.Medium, color = Ink)
                                Text(
                                    "Aquí aparecen las notificaciones reconocidas en este teléfono. El historial de la web no se descarga.",
                                    fontSize = 13.sp,
                                    color = Muted
                                )
                            }
                        }
                    } else {
                        item {
                            val recent = phoneMovements.take(5)
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Line),
                                color = Color.White
                            ) {
                                Column {
                                    recent.forEachIndexed { idx, row ->
                                        TransactionRow(row, showDivider = idx < recent.lastIndex) { selected = row }
                                    }
                                }
                            }
                        }
                    }
                } else if (tab == 1) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (status == MovementStatus.UNASSIGNED)
                                    "Guardados aquí; necesitan confirmar el destino antes de enviarse."
                                else
                                    "Movimientos capturados en este teléfono. El historial de la web no se descarga.",
                                fontSize = 13.sp,
                                color = Muted
                            )
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Buscar nombre o importe") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MovementStatus.entries.forEach { next ->
                                    FilterChip(
                                        selected = status == next,
                                        onClick = { statusName = next.name },
                                        label = { Text(next.label) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { datePicker = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (startDay == null) "Elegir fechas" else dateRangeLabel(startDay!!, endDay ?: startDay!!))
                                }
                                if (startDay != null) {
                                    TextButton(onClick = { startDay = null; endDay = null }) { Text("Quitar") }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${filtered.size} movimientos", fontSize = 13.sp, color = Muted)
                                if (status == MovementStatus.UNASSIGNED && destinationCount > 1 && session != null) {
                                    TextButton(onClick = { confirmAllDialog = true }) {
                                        Text("Confirmar todos ($destinationCount)")
                                    }
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            AppPanel {
                                Text(
                                    if (status == MovementStatus.UNASSIGNED) "No hay movimientos que necesiten confirmar destino"
                                    else "No hay movimientos para esta selección",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Ink
                                )
                                Text("Puedes cambiar el estado, la búsqueda o las fechas.", fontSize = 13.sp, color = Muted)
                                TextButton(onClick = { openMovements() }) { Text("Quitar filtros") }
                            }
                        }
                    } else {
                        val groupedByDay = filtered.groupBy { it.transactionDate.substringBefore('T') }
                        groupedByDay.forEach { (day, dayRows) ->
                            item(key = "day-$day") {
                                Text(
                                    formatTransactionDate(dayRows.first().transactionDate, "d 'de' MMMM 'de' yyyy"),
                                    color = Muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            item(key = "group-$day") {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Line),
                                    color = Color.White
                                ) {
                                    Column {
                                        dayRows.forEachIndexed { idx, row ->
                                            TransactionRow(row, showDivider = idx < dayRows.lastIndex) { selected = row }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Settings tab
                    item {
                        AppPanel {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Conexión con la web", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                                StatusPill(
                                    text = if (session != null) "Conectado" else "Sin conectar",
                                    contentColor = if (session != null) Positive else Muted,
                                    containerColor = if (session != null) SoftGreen else Canvas
                                )
                            }
                            Text(email ?: if (session == null) "Sin destino de envío configurado" else "Destino de envío configurado", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            session?.let { Text("Servidor: ${it.serverUrl}", color = Muted, fontSize = 12.sp) }
                            Text(
                                "El QR o código de vinculación indica a qué usuario de la web se enviarán los movimientos. La captura en el teléfono funciona por separado.",
                                fontSize = 13.sp,
                                color = Muted
                            )
                            if (session == null) {
                                Button(onClick = { pairing = true }, modifier = Modifier.fillMaxWidth()) { Text("Conectar con QR") }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { changeServer = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Cambiar dirección del servidor")
                                    }
                                    OutlinedButton(
                                        onClick = { unpair = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Negative),
                                        border = BorderStroke(1.dp, Negative.copy(alpha = 0.35f))
                                    ) {
                                        Text("Desvincular teléfono")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        AppPanel {
                            Text("Permisos y confiabilidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Acceso a notificaciones", fontWeight = FontWeight.Medium, color = Ink, fontSize = 14.sp)
                                StatusPill(
                                    text = if (permission) "Habilitado" else "Desactivado",
                                    contentColor = if (permission) Positive else Negative,
                                    containerColor = if (permission) SoftGreen else SoftNegative
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { openNotificationSettings(context) }) {
                                    Text(if (permission) "Revisar acceso" else "Activar acceso")
                                }
                                if (permission && !capture.connected) {
                                    OutlinedButton(onClick = { YapeNotificationListenerService.reconnect(context) }) {
                                        Text("Reconectar")
                                    }
                                }
                            }

                            HorizontalDivider(color = Line)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Batería en segundo plano", fontWeight = FontWeight.Medium, color = Ink, fontSize = 14.sp)
                                StatusPill(
                                    text = if (batteryUnrestricted) "Sin restricciones" else "Optimizada",
                                    contentColor = if (batteryUnrestricted) Positive else Warning,
                                    containerColor = if (batteryUnrestricted) SoftGreen else SoftWarning
                                )
                            }
                            Text(
                                if (batteryUnrestricted)
                                    "Android permite que FinTrack capture notificaciones con la app cerrada."
                                else
                                    "El ahorro de batería puede pausar la captura en segundo plano. Configúrala sin restricciones.",
                                fontSize = 13.sp,
                                color = Muted
                            )
                            OutlinedButton(onClick = { openBatterySettings(context) }) {
                                Text(if (batteryUnrestricted) "Revisar batería" else "Quitar restricción de batería")
                            }
                        }
                    }

                    item {
                        AppPanel {
                            TextButton(
                                onClick = { diagnostics = !diagnostics },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (diagnostics) "Ocultar diagnóstico de captura" else "Ver diagnóstico de captura")
                            }
                            if (diagnostics) {
                                Text("Última conexión: ${formatDiagnosticTime(capture.lastConnection)}", color = Muted, fontSize = 13.sp)
                                Text("Última notificación recibida: ${formatDiagnosticTime(capture.lastNotification)}", color = Muted, fontSize = 13.sp)
                                Text("Última captura guardada: ${formatDiagnosticTime(capture.lastCaptured)}", color = Muted, fontSize = 13.sp)
                                Text("Último movimiento guardado: ${formatDiagnosticTime(capture.lastSaved)}", color = Muted, fontSize = 13.sp)
                                if (inboxCounts.pending > 0) Text("${inboxCounts.pending} capturas pendientes de procesar", color = Muted, fontSize = 13.sp)
                                if (inboxCounts.review > 0) Text("${inboxCounts.review} capturas por revisar; requieren atención y no se enviarán como movimientos nuevos.", color = Negative, fontSize = 13.sp)
                                Text(
                                    "Solo se procesan notificaciones de aplicaciones compatibles. Recibir una notificación no significa que se haya reconocido un pago; los avisos promocionales también pueden descartarse.",
                                    color = Muted,
                                    fontSize = 12.sp
                                )
                                OutlinedButton(
                                    onClick = { YapeNotificationListenerService.reviewActive() },
                                    enabled = permission && capture.connected
                                ) {
                                    Text("Revisar notificaciones aún visibles")
                                }
                                if (capture.history.isEmpty()) Text("Aún no hay actividad registrada.", color = Muted, fontSize = 13.sp)
                                capture.history.forEach { event ->
                                    HorizontalDivider(color = Line)
                                    Text(event.event.label, fontSize = 14.sp, color = Ink)
                                    Text(formatDiagnosticTime(event.time), color = Muted, fontSize = 12.sp)
                                }
                                Text("Se conservan los últimos 30 eventos, sin nombres, importes ni códigos de seguridad.", color = Muted, fontSize = 12.sp)
                                TextButton(onClick = { inboxReview = !inboxReview }) {
                                    Text(if (inboxReview) "Ocultar capturas por revisar" else "Ver capturas por revisar")
                                }
                                if (inboxReview) {
                                    Text("Evidencia local: no representa movimientos confirmados ni se envía automáticamente. Se muestran hasta 100 capturas.", color = Muted, fontSize = 12.sp)
                                    reviewCaptures.forEach { observation ->
                                        HorizontalDivider(color = Line)
                                        Text(formatDiagnosticTime(observation.receivedAt), fontSize = 12.sp, color = Muted)
                                        Text(
                                            when (observation.reason) {
                                                "REUSED_ACTIVE_SOURCE" -> "No se pudo diferenciar una actualización de otro pago."
                                                "LEGACY_AMBIGUITY" -> "Coincide con varios registros antiguos."
                                                "MULTIPLE_AMOUNTS", "CONFLICTING_DIRECTION" -> "El contenido financiero es ambiguo."
                                                "CONTENT_LIMIT" -> "El aviso excede el tamaño admitido."
                                                else -> "Formato pendiente de revisión."
                                            },
                                            fontSize = 13.sp
                                        )
                                        observation.title?.let { Text(it, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                                        val evidence = observation.bigText?.takeIf { it.isNotBlank() } ?: observation.text
                                            ?: runCatching { com.financemanager.listener.capture.CaptureEncoding.decode(observation.lines).joinToString("\n").takeIf { it.isNotBlank() } }.getOrNull()
                                        if (evidence != null) Text(evidence, fontSize = 12.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
                                        else Text("El contenido ya no está disponible por la política de conservación.", color = Muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "FinTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        if (tab == 0 || tab == 1) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { triggerPullRefresh() },
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                listContent()
            }
        } else {
            Box(Modifier.fillMaxSize().padding(padding)) {
                listContent()
            }
        }
    }

    if (changeServer) session?.let { current ->
        ServerAddressDialog(current, onDismiss = { changeServer = false }, onSaved = {
            session = PairingPreferences.session(context)
            changeServer = false
        })
    }

    if (datePicker) {
        val range = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDay?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() },
            initialSelectedEndDateMillis = endDay?.let { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(enabled = range.selectedStartDateMillis != null, onClick = {
                    startDay = range.selectedStartDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    endDay = (range.selectedEndDateMillis ?: range.selectedStartDateMillis)?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    datePicker = false
                }) { Text("Aplicar fechas") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancelar") } }
        ) {
            DateRangePicker(state = range, modifier = Modifier.heightIn(max = 480.dp), title = { Text("Selecciona un día o un rango", Modifier.padding(16.dp)) })
        }
    }

    selected?.let { original ->
        val row = (owned + unassigned).find { it.id == original.id }
        if (row == null) {
            LaunchedEffect(original.id) { selected = null }
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selected = null },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatAmount(row),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (row.flowType == "INCOME") Positive else Ink
                        )
                        MovementStatusBadge(row)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.contactName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(formatTransactionDate(row.transactionDate, "d 'de' MMMM yyyy, HH:mm"), fontSize = 13.sp, color = Muted)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Line),
                        color = Canvas
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DetailRow("Canal", formatChannelLabel(row.channel))
                            HorizontalDivider(color = Line)
                            DetailRow("Estado", movementLabel(row))
                            HorizontalDivider(color = Line)
                            DetailRow(
                                "Destino en la web",
                                when {
                                    row.ownerKey == null && row.isSynced -> "Registro antiguo"
                                    row.ownerKey == null -> "Necesita confirmación"
                                    else -> email ?: "Destino configurado"
                                }
                            )
                        }
                    }

                    Text(
                        when {
                            row.isSynced && row.ownerKey == null -> "Este registro antiguo figura como enviado. No se enviará de nuevo."
                            row.isSynced -> "La web confirmó la recepción de este movimiento."
                            else -> "El movimiento está guardado en este teléfono."
                        },
                        fontSize = 13.sp,
                        color = Muted
                    )

                    if (row.ownerKey == null && !row.isSynced) {
                        if (session != null) {
                            Button(
                                onClick = { assignment = row; selected = null },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Confirmar destino de envío")
                            }
                        } else {
                            Text("Conecta el teléfono con la web desde Ajustes para elegir el destino de envío.", fontSize = 13.sp, color = Muted)
                        }
                    }
                }
            }
        }
    }

    assignment?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!assigning) assignment = null },
            title = { Text("Confirmar destino de envío") },
            text = { Text("Confirma que este movimiento de S/ ${row.amount} de ${row.contactName} es tuyo y que quieres enviarlo a ${email ?: "el usuario conectado en la web"}.") },
            confirmButton = {
                TextButton(enabled = !assigning && session != null, onClick = {
                    val expected = session
                    assigning = true
                    scope.launch {
                        try {
                            val changed = withContext(Dispatchers.IO) {
                                if (expected != null && PairingPreferences.session(context) == expected) dao.assignReviewedEntry(row.id, expected.ownerKey) else 0
                            }
                            if (changed == 1) TransactionSyncWorker.enqueue(context)
                            assignment = null
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            CaptureStatus.error("No se pudo confirmar el destino. El movimiento sigue guardado en el teléfono.")
                        } finally {
                            assigning = false
                        }
                    }
                }) { Text(if (assigning) "Confirmando…" else "Confirmar y enviar") }
            },
            dismissButton = { TextButton(enabled = !assigning, onClick = { assignment = null }) { Text("Cancelar") } }
        )
    }

    if (confirmAllDialog) {
        AlertDialog(
            onDismissRequest = { if (!assigning) confirmAllDialog = false },
            title = { Text("Confirmar $destinationCount movimientos") },
            text = {
                Text("Confirma que los $destinationCount movimientos pendientes de destino en este teléfono son tuyos y deseas enviarlos a ${email ?: "el usuario conectado en la web"}.")
            },
            confirmButton = {
                TextButton(enabled = !assigning && session != null, onClick = {
                    val expected = session
                    assigning = true
                    scope.launch {
                        try {
                            val changed = withContext(Dispatchers.IO) {
                                if (expected != null && PairingPreferences.session(context) == expected) dao.assignAllReviewedEntries(expected.ownerKey) else 0
                            }
                            if (changed > 0) TransactionSyncWorker.enqueue(context, manual = true)
                            confirmAllDialog = false
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            CaptureStatus.error("No se pudieron confirmar los destinos. Los movimientos siguen guardados en el teléfono.")
                        } finally {
                            assigning = false
                        }
                    }
                }) { Text(if (assigning) "Confirmando…" else "Confirmar y enviar todos") }
            },
            dismissButton = { TextButton(enabled = !assigning, onClick = { confirmAllDialog = false }) { Text("Cancelar") } }
        )
    }

    if (pairing) {
        PairingDialog(onDismiss = { pairing = false }, onSuccess = {
            email = it; session = PairingPreferences.session(context); pairing = false
            selected = null; assignment = null; confirmAllDialog = false; openMovements(); tab = 0
        })
    }

    if (unpair) {
        AlertDialog(
            onDismissRequest = { unpair = false },
            title = { Text("Desvincular teléfono") },
            text = { Text("Se detendrá el envío a ${email ?: "el destino configurado en la web"}. Los movimientos guardados se conservarán en este teléfono. Si después vinculas otra dirección o cuenta, los movimientos del destino anterior pueden dejar de mostrarse. Para corregir la IP del mismo servidor, usa ‘Cambiar dirección del servidor’.") },
            confirmButton = {
                TextButton(onClick = {
                    PairingPreferences.clearPairing(context); TransactionSyncWorker.cancel(context); ApiClient.resetService()
                    CaptureStatus.sync("Conecta el teléfono con la web para enviar movimientos.")
                    session = null; email = null; unpair = false; selected = null; assignment = null; confirmAllDialog = false; statusName = MovementStatus.ALL.name
                }) { Text("Desvincular", color = Negative) }
            },
            dismissButton = { TextButton(onClick = { unpair = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun AppPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line), color = Color.White) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun StatusPill(text: String, contentColor: Color, containerColor: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = containerColor) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(contentColor))
            Text(text, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MovementStatusBadge(row: LocalTransactionEntity) {
    val (label, fg, bg) = when {
        row.isSynced -> Triple("Enviado", Positive, SoftGreen)
        row.ownerKey == null -> Triple("Confirmar destino", Warning, SoftWarning)
        else -> Triple("Por enviar", BrandBlue, SoftBrand)
    }
    StatusPill(text = label, contentColor = fg, containerColor = bg)
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line),
        color = Canvas
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SetupStepRow(
    stepNumber: Int,
    title: String,
    completed: Boolean,
    completedText: String,
    actionLabel: String,
    primaryAction: Boolean,
    onAction: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (completed) SoftGreen else SoftBrand),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (completed) "✓" else stepNumber.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (completed) Positive else BrandBlue
                )
            }
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                if (completed) Text(completedText, fontSize = 12.sp, color = Positive)
            }
        }
        if (!completed) {
            if (primaryAction) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            } else {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Muted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TransactionRow(row: LocalTransactionEntity, showDivider: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.contactName, fontWeight = FontWeight.Medium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${formatChannelLabel(row.channel)} · ${formatTransactionDate(row.transactionDate, "HH:mm")}", color = Muted, fontSize = 12.sp)
            }
            Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(formatAmount(row), color = if (row.flowType == "INCOME") Positive else Ink, fontWeight = FontWeight.SemiBold)
                MovementStatusBadge(row)
            }
        }
        if (showDivider) HorizontalDivider(color = Line)
    }
}

@Composable
private fun NavigationMark(index: Int, selected: Boolean, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(width = (if (selected) 2.1f else 1.8f) * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (index) {
            0 -> {
                val roof = Path().apply {
                    moveTo(3 * u, 10.5f * u)
                    lineTo(12 * u, 3 * u)
                    lineTo(21 * u, 10.5f * u)
                }
                val body = Path().apply {
                    moveTo(5.5f * u, 9.5f * u)
                    lineTo(5.5f * u, 20.5f * u)
                    lineTo(18.5f * u, 20.5f * u)
                    lineTo(18.5f * u, 9.5f * u)
                }
                if (selected) {
                    val filled = Path().apply {
                        moveTo(3.5f * u, 10.5f * u)
                        lineTo(12 * u, 3.5f * u)
                        lineTo(20.5f * u, 10.5f * u)
                        lineTo(18.5f * u, 10.5f * u)
                        lineTo(18.5f * u, 20.5f * u)
                        lineTo(5.5f * u, 20.5f * u)
                        lineTo(5.5f * u, 10.5f * u)
                        close()
                    }
                    drawPath(filled, color.copy(alpha = 0.16f), style = Fill)
                }
                drawPath(body, color, style = stroke)
                drawPath(roof, color, style = stroke)
            }
            1 -> {
                drawRoundRect(
                    color = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
                    topLeft = Offset(3 * u, 4 * u),
                    size = Size(18 * u, 16 * u),
                    cornerRadius = CornerRadius(3 * u, 3 * u)
                )
                listOf(7.5f, 12f, 16.5f).forEach { y ->
                    drawCircle(color, 1.3f * u, Offset(6 * u, y * u))
                    drawLine(color, Offset(10 * u, y * u), Offset(19 * u, y * u), stroke.width, StrokeCap.Round)
                }
            }
            else -> {
                listOf(6f, 12f, 18f).forEachIndexed { i, y ->
                    drawLine(color, Offset(3 * u, y * u), Offset(21 * u, y * u), stroke.width, StrokeCap.Round)
                    val center = Offset((if (i == 1) 15.5f else 8.5f) * u, y * u)
                    drawCircle(if (selected) color else Color.White, 2.8f * u, center)
                    drawCircle(color, 2.8f * u, center, style = stroke)
                }
            }
        }
    }
}

private fun movementLabel(row: LocalTransactionEntity) = if (row.isSynced) "Enviado" else if (row.ownerKey == null) "Necesita confirmar destino" else "Por enviar"
private fun formatAmount(row: LocalTransactionEntity): String {
    val amount = row.amount.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: row.amount
    val sign = when (row.flowType) {
        "INCOME" -> "+ "
        "EXPENSE" -> "− "
        else -> ""
    }
    return "${sign}S/ $amount"
}
private fun formatTransactionDate(value: String, pattern: String): String = runCatching {
    LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag("es-PE")))
}.getOrDefault(value.substringBefore('.').replace('T', ' '))
private fun formatDiagnosticTime(value: Long?): String = value?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-PE")))
} ?: "Sin registro"
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
private fun isBatteryUnrestricted(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
    }
}
