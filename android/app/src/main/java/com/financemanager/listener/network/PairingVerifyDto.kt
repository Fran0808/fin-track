package com.financemanager.listener.network

data class PairingVerifyDto(
    val valid: Boolean,
    val userId: Long? = null,
    val userEmail: String? = null,
    val fullName: String? = null,
    val message: String? = null
)
