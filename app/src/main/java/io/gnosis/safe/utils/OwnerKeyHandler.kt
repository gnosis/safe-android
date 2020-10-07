package io.gnosis.safe.utils

import androidx.core.content.edit
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.utils.*
import java.math.BigInteger

interface PrivateKeyHandler {
    fun storeKey(key: BigInteger)
    fun retrieveKey(): BigInteger
}

interface OwnerAddressHandler {
    fun storeOwnerAddress(address: Solidity.Address)
    fun retrieveOwnerAddress(): Solidity.Address?
}

class OwnerKeyHandler(
        private val encryptionManager: EncryptionManager,
        private val preferencesManager: PreferencesManager
) : PrivateKeyHandler, OwnerAddressHandler {
    override fun storeKey(key: BigInteger) {
        isInitialized()
        val result = encryptionManager.unlockWithPassword(HARDCODED_PASSWORD.toByteArray())

        val keyCryptoData = encryptionManager.encrypt(key.toByteArray())

        preferencesManager.prefs.edit { putString(PREF_KEY_ENCRYPTED_OWNER_KEY_VALUE, keyCryptoData.data.toHexString()) }
        preferencesManager.prefs.edit { putString(PREF_KEY_ENCRYPTED_OWNER_KEY_IV, keyCryptoData.iv.toHexString()) }
    }

    private fun isInitialized() {
        if (!encryptionManager.initialized()) {
            val result = encryptionManager.setupPassword(HARDCODED_PASSWORD.toByteArray())
            if (!result) {
                // TODO this needs to be handled better
                throw RuntimeException("EncryptionManger init failed")
            }
        }
    }

    override fun retrieveKey(): BigInteger {
        isInitialized()

        encryptionManager.unlockWithPassword(HARDCODED_PASSWORD.toByteArray())
        val encrypted = preferencesManager.prefs.getString(PREF_KEY_ENCRYPTED_OWNER_KEY_VALUE, "")!!.hexToByteArray()
        val iv = preferencesManager.prefs.getString(PREF_KEY_ENCRYPTED_OWNER_KEY_IV, "")!!.hexToByteArray()

        val decrypted = encryptionManager.decrypt(EncryptionManager.CryptoData(encrypted, iv))

        return decrypted.asBigInteger()
    }

    override fun storeOwnerAddress(address: Solidity.Address) {
        preferencesManager.prefs.edit { putString(PREF_KEY_ENCRYPTED_OWNER_ADDRESS, address.asEthereumAddressString()) }
    }

    override fun retrieveOwnerAddress(): Solidity.Address? = preferencesManager.prefs.getString(PREF_KEY_ENCRYPTED_OWNER_ADDRESS, null)?.asEthereumAddress()

    companion object {
        const val PREF_KEY_ENCRYPTED_OWNER_ADDRESS = "owner_key_handler.string.owner.address"
        const val PREF_KEY_ENCRYPTED_OWNER_KEY_VALUE = "encryption_manager.string.encrypted.value"
        const val PREF_KEY_ENCRYPTED_OWNER_KEY_IV = "encryption_manager.string.encrypted.iv"

        // EncryptionManager needs a password to encrypt the owner key (This can be updated to a user provided password in the future)
        const val HARDCODED_PASSWORD = "Hardcoded Password"
    }
}