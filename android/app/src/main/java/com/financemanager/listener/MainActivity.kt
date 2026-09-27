package com.financemanager.listener

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.financemanager.listener.ui.theme.ListenServiceTheme
import com.financemanager.listener.worker.TransactionSyncWorker
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ListenServiceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isNotificationGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isPaired by remember { mutableStateOf(PairingPreferences.isPaired(context)) }
    var pairedEmail by remember { mutableStateOf(PairingPreferences.getPairedEmail(context)) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showUnpairConfirm by remember { mutableStateOf(false) }

    val db = remember { AppDatabase.getDatabase(context) }
    val transactions by db.transactionDao().getRecentTransactionsFlow().collectAsState(initial = emptyList())

    fun refreshState() {
        isNotificationGranted = isNotificationServiceEnabled(context)
        isPaired = PairingPreferences.isPaired(context)
        pairedEmail = PairingPreferences.getPairedEmail(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header
        Text(
            text = "Wallet Pulse",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Yape Financial Notification Listener",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Notification Permission Card
        PermissionCard(
            title = "Notification Listener",
            description = "Listens to incoming Yape payment notifications in background with 0% risk.",
            isGranted = isNotificationGranted,
            onGrantClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Device Pairing Status Card
        PairingCard(
            isPaired = isPaired,
            pairedEmail = pairedEmail,
            onPairClick = { showPairingDialog = true },
            onUnpairClick = { showUnpairConfirm = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Manual Sync Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Captured Transactions (${transactions.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            OutlinedButton(
                onClick = {
                    TransactionSyncWorker.enqueue(context)
                    Toast.makeText(context, "Sincronización encolada", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Sync Now")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transaction History List
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No transactions captured yet.\nWhen you receive a Yape, it will appear here automatically.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    TransactionItemCard(tx)
                }
            }
        }
    }

    // Pairing Modal Dialog
    if (showPairingDialog) {
        PairingDialog(
            onDismiss = { showPairingDialog = false },
            onSuccess = { email ->
                isPaired = true
                pairedEmail = email
                showPairingDialog = false
            }
        )
    }

    // Unpair Confirmation Dialog
    if (showUnpairConfirm) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirm = false },
            title = { Text("Desvincular Dispositivo") },
            text = { Text("¿Deseas desvincular este teléfono de tu cuenta ($pairedEmail)? Dejará de sincronizar hasta que vuelvas a escanear un código QR.") },
            confirmButton = {
                Button(
                    onClick = {
                        PairingPreferences.clearPairing(context)
                        ApiClient.resetService()
                        isPaired = false
                        pairedEmail = null
                        showUnpairConfirm = false
                        Toast.makeText(context, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desvincular")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnpairConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PairingCard(
    isPaired: Boolean,
    pairedEmail: String?,
    onPairClick: () -> Unit,
    onUnpairClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaired) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vinculación de Cuenta",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isPaired) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
                Text(
                    text = if (isPaired) "Vinculado" else "Pendiente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isPaired) Color(0xFF2E7D32) else Color(0xFFE65100)
                )
            }
            Text(
                text = if (isPaired) {
                    "Sincronizando con: ${pairedEmail ?: "Cuenta verificada"}"
                } else {
                    "Escanea el código QR desde la web para asignar tus transacciones a tu cuenta."
                },
                fontSize = 12.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isPaired) {
                OutlinedButton(
                    onClick = onUnpairClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Desvincular Dispositivo", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onPairClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Vincular Cuenta (QR / Código)", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PairingDialog(
    onDismiss: () -> Unit,
    onSuccess: (email: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualToken by remember { mutableStateOf("") }
    var manualServerUrl by remember { mutableStateOf(PairingPreferences.getServerUrl(context)) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vincular Dispositivo") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Abre WalletPulse en tu navegador web y toca 'Vincular Celular' para obtener tu código QR.",
                    fontSize = 13.sp,
                    color = Color.DarkGray
                )

                // Option 1: Scan QR
                Button(
                    onClick = {
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val raw = barcode.rawValue
                                if (!raw.isNullOrBlank()) {
                                    handleQrPayload(raw, context, scope,
                                        onStart = {
                                            isVerifying = true
                                            errorMessage = null
                                        },
                                        onFinish = { isVerifying = false },
                                        onSuccess = onSuccess,
                                        onError = { err -> errorMessage = err }
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                errorMessage = "Fallo al abrir escáner: ${e.message}"
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isVerifying
                ) {
                    Text("Escanear Código QR con Cámara")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "O ingresa los datos manualmente:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Token de Emparejamiento") },
                    placeholder = { Text("wp_dev_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualServerUrl,
                    onValueChange = { manualServerUrl = it },
                    label = { Text("URL del Servidor") },
                    placeholder = { Text("http://192.168.1.5:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                if (isVerifying) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verificando con el servidor...", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualToken.isBlank()) {
                        errorMessage = "Ingresa el token de emparejamiento"
                        return@Button
                    }
                    verifyAndSaveToken(
                        token = manualToken.trim(),
                        serverUrl = manualServerUrl.trim(),
                        context = context,
                        scope = scope,
                        onStart = {
                            isVerifying = true
                            errorMessage = null
                        },
                        onFinish = { isVerifying = false },
                        onSuccess = onSuccess,
                        onError = { err -> errorMessage = err }
                    )
                },
                enabled = !isVerifying && manualToken.isNotBlank()
            ) {
                Text("Conectar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isVerifying) {
                Text("Cancelar")
            }
        }
    )
}

private fun handleQrPayload(
    rawPayload: String,
    context: Context,
    scope: CoroutineScope,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSuccess: (email: String) -> Unit,
    onError: (String) -> Unit
) {
    try {
        var token = rawPayload.trim()
        var serverUrl: String? = null

        if (rawPayload.trim().startsWith("{")) {
            val json = JSONObject(rawPayload)
            val parsedToken = json.optString("token")
            if (parsedToken.isNotBlank()) token = parsedToken
            val parsedUrl = json.optString("serverUrl")
            if (parsedUrl.isNotBlank()) serverUrl = parsedUrl
        }

        verifyAndSaveToken(token, serverUrl, context, scope, onStart, onFinish, onSuccess, onError)
    } catch (e: Exception) {
        onError("Formato de QR no válido: ${e.message}")
    }
}

private fun verifyAndSaveToken(
    token: String,
    serverUrl: String?,
    context: Context,
    scope: CoroutineScope,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onSuccess: (email: String) -> Unit,
    onError: (String) -> Unit
) {
    onStart()
    scope.launch(Dispatchers.IO) {
        try {
            val targetUrl = if (!serverUrl.isNullOrBlank()) serverUrl else PairingPreferences.getServerUrl(context)
            val service = ApiClient.getService(targetUrl)
            val response = service.verifyPairing(token)

            withContext(Dispatchers.Main) {
                onFinish()
                if (response.isSuccessful && response.body()?.valid == true) {
                    val body = response.body()!!
                    val email = body.userEmail ?: "Usuario Verificado"
                    PairingPreferences.savePairing(context, token, targetUrl, email)
                    ApiClient.resetService()
                    Toast.makeText(context, "¡Dispositivo vinculado con éxito!", Toast.LENGTH_SHORT).show()
                    onSuccess(email)
                } else {
                    onError("Token inválido o rechazado por el servidor (${response.code()})")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onFinish()
                onError("No se pudo conectar al servidor: ${e.message}")
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    text = if (isGranted) "Active" else "Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            Text(
                text = description,
                fontSize = 12.sp,
                color = Color.DarkGray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (!isGranted) {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun TransactionItemCard(tx: LocalTransactionEntity) {
    val isIncome = tx.flowType == "INCOME"

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.contactName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = tx.transactionDate.replace("T", " "),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isIncome) "+ S/ " else "- S/ ") + tx.amount,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isIncome) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                Text(
                    text = if (tx.isSynced) "Synced" else "Pending Sync",
                    fontSize = 11.sp,
                    color = if (tx.isSynced) Color(0xFF388E3C) else Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    val cn = ComponentName(context, YapeNotificationListenerService::class.java)
    return flat != null && flat.contains(cn.flattenToString())
}