package com.financemanager.listener

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.financemanager.listener.data.PairingPreferences
import com.financemanager.listener.data.PairingScope
import com.financemanager.listener.data.PairingSession
import com.financemanager.listener.network.ApiClient
import com.financemanager.listener.service.CaptureStatus
import com.financemanager.listener.worker.SyncCoordinator
import com.financemanager.listener.worker.syncProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerAddressDialog(session: PairingSession, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(session.serverUrl) }
    var verifying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = { if (!verifying) onDismiss() },
        title = { Text("Cambiar dirección del servidor") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Usa esta opción si cambió la dirección de tu mismo servidor. Verificaremos la vinculación actual y conservaremos tus movimientos y pagos pendientes.")
                OutlinedTextField(address, { address = it }, label = { Text("Dirección del servidor") },
                    placeholder = { Text("http://192.168.1.50:8080") }, singleLine = true,
                    enabled = !verifying, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (verifying) Text("Verificando la conexión y tu vinculación…")
            }
        },
        confirmButton = {
            TextButton(enabled = !verifying && address.isNotBlank(), onClick = {
                val target = runCatching { PairingScope.normalizeServer(address) }.getOrNull()
                if (target == null) {
                    error = "Ingresa una dirección válida que empiece por http:// o https://."
                    return@TextButton
                }
                verifying = true
                error = null
                scope.launch {
                    try {
                        val failure = withContext(Dispatchers.IO) {
                            val response = ApiClient.getService(target).verifyPairing(session.token)
                            val body = response.body()
                            if (!response.isSuccessful || body?.valid != true || body.userId == null) {
                                "El servidor no confirmó tu vinculación (HTTP ${response.code()}). Revisa la dirección y usa el mismo servidor."
                            } else {
                                SyncCoordinator.serialized {
                                    PairingPreferences.updateVerifiedServer(context, session, target, body.userId)
                                }
                                ApiClient.resetService()
                                null
                            }
                        }
                        if (failure != null) error = failure
                        else {
                            CaptureStatus.sync("Dirección actualizada. Pulsa ‘Enviar ahora’ para enviar los movimientos pendientes.")
                            onSaved()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IllegalStateException) {
                        error = "La vinculación cambió durante la verificación. Cierra este diálogo y vuelve a intentarlo."
                    } catch (_: IllegalArgumentException) {
                        error = "La dirección debe corresponder al mismo servidor y usuario de la vinculación actual."
                    } catch (failure: Exception) {
                        error = syncProblem(failure).message
                    } finally {
                        verifying = false
                    }
                }
            }) { Text("Verificar y guardar") }
        },
        dismissButton = { TextButton(enabled = !verifying, onClick = onDismiss) { Text("Cancelar") } })
}
