package com.financemanager.listener.worker

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class SyncFailure {
    CONNECTION, TIMEOUT, HOST, SECURE_CONNECTION, MISSING_RECEIPT, AUTHORIZATION, HTTP, INVALID_RECORD, LOCAL
}

data class SyncProblem(val failure: SyncFailure, val httpCode: Int? = null) {
    val message: String get() = when (failure) {
        SyncFailure.CONNECTION -> "No se pudo contactar al servidor. Comprueba que esté encendido y sea accesible desde el teléfono."
        SyncFailure.TIMEOUT -> "El servidor no respondió a tiempo. Comprueba la conexión e inténtalo otra vez."
        SyncFailure.HOST -> "No se encontró la dirección del servidor. Revisa la conexión y el destino configurado."
        SyncFailure.SECURE_CONNECTION -> "No se pudo establecer una conexión segura con el servidor."
        SyncFailure.MISSING_RECEIPT -> "El servidor respondió sin confirmar el movimiento. Sigue guardado como pendiente."
        SyncFailure.AUTHORIZATION -> "El servidor rechazó la autorización. Conecta de nuevo con la web."
        SyncFailure.HTTP -> "El servidor rechazó el envío (HTTP $httpCode). El movimiento sigue guardado."
        SyncFailure.INVALID_RECORD -> "No se pudo preparar un movimiento para enviarlo. Sigue guardado para revisión."
        SyncFailure.LOCAL -> "No se pudo preparar el envío desde el teléfono. Revisa el diagnóstico e inténtalo otra vez."
    }
}

fun syncProblem(error: Exception): SyncProblem = SyncProblem(when (error) {
    is SocketTimeoutException -> SyncFailure.TIMEOUT
    is UnknownHostException -> SyncFailure.HOST
    is SSLException -> SyncFailure.SECURE_CONNECTION
    is ConnectException, is IOException -> SyncFailure.CONNECTION
    else -> SyncFailure.LOCAL
})
