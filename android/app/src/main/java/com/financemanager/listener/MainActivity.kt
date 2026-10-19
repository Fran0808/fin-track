package com.financemanager.listener

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.ui.theme.FinTrackTheme
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE)
        )
        setContent {
            FinTrackTheme {
                DashboardScreen()
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
    var manualVisible by remember { mutableStateOf(false) }

    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        title = { Text("Conectar con la web") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Abre FinTrack en la web y toca ‘Vinculación móvil’. Escanea el QR para configurar a dónde se enviarán los movimientos de este teléfono.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                errorMessage = "No se pudo abrir el escáner. Inténtalo otra vez o usa la configuración manual."
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isVerifying
                ) {
                    Text("Escanear QR")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                TextButton(onClick = { manualVisible = !manualVisible }, enabled = !isVerifying) {
                    Text(if (manualVisible) "Ocultar configuración manual" else "Configuración manual")
                }

                if (manualVisible) {
                OutlinedTextField(
                    value = manualToken,
                    onValueChange = { manualToken = it },
                    label = { Text("Código de vinculación") },
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("wp_dev_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = manualServerUrl,
                    onValueChange = { manualServerUrl = it },
                    label = { Text("Dirección del servidor") },
                    placeholder = { Text("http://192.168.1.50:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                }

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
            if (manualVisible) {
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

            if (response.isSuccessful && response.body()?.valid == true && response.body()?.userId != null) {
                // Upgrade only rows already scoped to this verified credential, never unowned rows.
                val credentialOwner = com.financemanager.listener.data.PairingScope.ownerKey(targetUrl, token, null)
                val verifiedOwner = com.financemanager.listener.data.PairingScope.ownerKey(targetUrl, token, response.body()!!.userId)
                val database = com.financemanager.listener.data.AppDatabase.getDatabase(context)
                database.runInTransaction {
                    database.transactionDao().upgradeVerifiedOwner(credentialOwner, verifiedOwner)
                    database.captureInboxDao().upgradeVerifiedOwner(credentialOwner, verifiedOwner)
                }
            }

            withContext(Dispatchers.Main) {
                onFinish()
                if (response.isSuccessful && response.body()?.valid == true && response.body()?.userId != null) {
                    val body = response.body()!!
                    val email = body.userEmail ?: "Usuario Verificado"
                    PairingPreferences.savePairing(context, token, targetUrl, email, requireNotNull(body.userId))
                    ApiClient.resetService()
                    com.financemanager.listener.capture.CaptureRuntime.get(context).resume()
                    Toast.makeText(context, "Teléfono conectado con la web", Toast.LENGTH_SHORT).show()
                    onSuccess(email)
                } else {
                    onError("Token inválido o rechazado por el servidor (${response.code()})")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onFinish()
                onError("No se pudo conectar. Comprueba que el teléfono pueda acceder a la dirección del servidor e inténtalo otra vez.")
            }
        }
    }
}
