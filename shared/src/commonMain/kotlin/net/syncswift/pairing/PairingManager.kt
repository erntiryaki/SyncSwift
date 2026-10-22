package net.syncswift.pairing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.syncswift.crypto.DeviceIdentityManager

object PairingManager {
    private val _activeSession = MutableStateFlow<PairingSession?>(null)
    val activeSession: StateFlow<PairingSession?> = _activeSession.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<PairedDevice>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDevice>> = _pairedDevices.asStateFlow()

    /**
     * Start a new pairing broadcast session with generated SAS
     */
    fun startInitiatorSession(deviceId: String, deviceName: String, osType: String): PairingSession {
        val identity = DeviceIdentityManager.getOrCreateIdentity(deviceName, osType)
        val pairingCode = SasGenerator.generatePairingCode()
        val salt = pairingCode.encodeToByteArray()
        val sas = SasGenerator.computeSas(identity.agreementKeyPair.publicKey, identity.signingKeyPair.publicKey, salt)

        val session = PairingSession(
            sessionId = "sess_${System.currentTimeMillis()}",
            initiatorDeviceId = deviceId,
            pairingCode = pairingCode,
            sasCode = sas,
            status = PairingStatus.PENDING,
            expiresAtMs = System.currentTimeMillis() + (5 * 60 * 1000)
        )
        _activeSession.value = session
        return session
    }

    /**
     * Confirm peer SAS matching
     */
    fun approveSession(sessionId: String, targetDeviceId: String): Boolean {
        val current = _activeSession.value ?: return false
        if (current.sessionId != sessionId) return false

        _activeSession.value = current.copy(
            targetDeviceId = targetDeviceId,
            status = PairingStatus.APPROVED
        )
        return true
    }

    /**
     * Reject or cancel pairing session
     */
    fun cancelSession() {
        val current = _activeSession.value ?: return
        _activeSession.value = current.copy(status = PairingStatus.CANCELLED)
    }

    /**
     * Add or update a verified paired device
     */
    fun addPairedDevice(device: PairedDevice) {
        val currentList = _pairedDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.deviceId == device.deviceId }
        if (existingIndex >= 0) {
            currentList[existingIndex] = device
        } else {
            currentList.add(device)
        }
        _pairedDevices.value = currentList
    }

    /**
     * Revoke trust from a paired device
     */
    fun revokeDevice(deviceId: String) {
        val currentList = _pairedDevices.value.toMutableList()
        currentList.removeAll { it.deviceId == deviceId }
        _pairedDevices.value = currentList
    }
}
