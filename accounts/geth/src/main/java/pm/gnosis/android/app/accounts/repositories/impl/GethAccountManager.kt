package pm.gnosis.android.app.accounts.repositories.impl

import org.ethereum.geth.Account
import org.ethereum.geth.KeyStore
import pm.gnosis.android.app.android.utils.PreferencesManager
import pm.gnosis.android.app.authenticator.util.edit
import pm.gnosis.android.app.core.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GethAccountManager @Inject constructor(private val preferencesManager: PreferencesManager,
                                             private val keyStore: KeyStore) {

    fun getAccount(publicKey: String): Account? = getAccounts().find { it.address.hex == publicKey }

    fun getAccounts(): List<Account> {
        val accounts = ArrayList<Account>()
        for (i in 0 until keyStore.accounts.size()) {
            accounts += keyStore.accounts[i]
        }
        return accounts
    }

    fun setActiveAccount(publicKey: String): Boolean {
        getAccounts().find { it.address.hex == publicKey }?.let {
            preferencesManager.prefs.edit { putString(PreferencesManager.Companion.CURRENT_ACCOUNT_ADDRESS_KEY, it.address.hex) }
            return true
        }
        return false
    }

    fun getActiveAccount(): Account = getAccounts().first()

    fun getAccountPassphrase(): String {
        @Suppress("ConstantConditionIf")
        (return if (BuildConfig.PRIVATE_TEST_NET) {
            pm.gnosis.android.app.authenticator.util.test.TestRPC.accounts[getActiveAccount().address.hex]?.second!!
        } else {
            var passphrase = preferencesManager.prefs.getString(PreferencesManager.Companion.PASSPHRASE_KEY, "")
            if (passphrase.isEmpty()) {
                preferencesManager.prefs.edit {
                    passphrase = pm.gnosis.android.app.authenticator.util.generateRandomString()
                    putString(PreferencesManager.Companion.PASSPHRASE_KEY, passphrase)
                }
            }
            passphrase
        })
    }
}
