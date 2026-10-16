package com.financemanager.listener.data

object PairingAddressChange {
    fun verifiedSession(
        current: PairingSession?, expected: PairingSession, serverUrl: String,
        previousUserId: Long?, verifiedUserId: Long
    ): PairingSession {
        check(current == expected) { "Pairing changed during address verification" }
        require(verifiedUserId > 0 && (previousUserId == null || previousUserId == verifiedUserId)) {
            "The new address must verify the same paired user"
        }
        return expected.copy(serverUrl = PairingScope.normalizeServer(serverUrl))
    }
}
