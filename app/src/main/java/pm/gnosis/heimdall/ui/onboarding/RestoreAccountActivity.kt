package pm.gnosis.heimdall.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_restore_account.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.component.DaggerViewComponent
import pm.gnosis.heimdall.common.di.module.ViewModule
import pm.gnosis.heimdall.common.util.snackbar
import pm.gnosis.heimdall.common.util.startActivity
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.heimdall.ui.main.MainActivity
import pm.gnosis.mnemonic.*
import pm.gnosis.mnemonic.wordlist.BIP39_WORDLISTS
import timber.log.Timber
import javax.inject.Inject

class RestoreAccountActivity : BaseActivity() {
    @Inject lateinit var viewModel: RestoreAccountViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()
        setContentView(R.layout.layout_restore_account)
    }

    override fun onStart() {
        super.onStart()
        disposables += mnemonicValidatorDisposable()
        disposables += mnemonicChangesDisposable()
    }

    private fun mnemonicValidatorDisposable() =
            layout_restore_account_restore.clicks()
                    .map { layout_restore_account_mnemonic.text.toString() }
                    .flatMapSingle { viewModel.isValidMnemonic(it) }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onNext = this::onMnemonicValidation, onError = this::onMnemonicValidationError)

    private fun mnemonicChangesDisposable() =
            layout_restore_account_mnemonic.textChanges()
                    .skipInitialValue()
                    .map { it.toString() }
                    .subscribeBy(
                            onNext = { layout_restore_account_mnemonic_input_layout.error = null },
                            onError = Timber::e)

    private fun onMnemonicValidation(mnemonicResult: Bip39ValidationResult) {
        when (mnemonicResult) {
            is ValidMnemonic -> {
                layout_restore_account_mnemonic_input_layout.error = null
                disposables += saveAccountWithMnemonicDisposable(layout_restore_account_mnemonic.text.toString())
            }
            is InvalidEntropy -> {
                layout_restore_account_mnemonic_input_layout.error = getString(R.string.invalid_mnemonic)
            }
            is InvalidChecksum -> {
                layout_restore_account_mnemonic_input_layout.error = getString(R.string.invalid_mnemonic)
            }
            is MnemonicNotInWordlist -> {
                val wordLists = BIP39_WORDLISTS.keys.joinToString()
                layout_restore_account_mnemonic_input_layout.error = getString(R.string.invalid_mnemonic_supported_languages, wordLists)
            }
            is EmptyMnemonic -> {
                layout_restore_account_mnemonic_input_layout.error = getString(R.string.invalid_mnemonic_no_words)
            }
            is UnknownMnemonicError -> {
                layout_restore_account_mnemonic_input_layout.error = getString(R.string.invalid_mnemonic)
            }
        }
    }

    private fun onMnemonicValidationError(throwable: Throwable) {
        Timber.e(throwable)
    }

    private fun saveAccountWithMnemonicDisposable(mnemonic: String) =
            viewModel.saveAccountWithMnemonic(mnemonic)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { onSavingAccount(isSaving = true) }
                    .doOnTerminate { onSavingAccount(isSaving = false) }
                    .subscribeBy(onComplete = this::onAccountSaved, onError = this::onAccountSaveError)

    private fun onAccountSaved() {
        startActivity(MainActivity.createIntent(this), noHistory = false)
    }

    private fun onAccountSaveError(throwable: Throwable) {
        Timber.e(throwable)
        snackbar(layout_restore_account_coordinator, "Could not save account from mnemonic")
    }

    private fun onSavingAccount(isSaving: Boolean) {
        layout_restore_account_restore.isEnabled = isSaving
    }

    private fun inject() {
        DaggerViewComponent.builder()
                .applicationComponent(HeimdallApplication[this].component)
                .viewModule(ViewModule(this))
                .build().inject(this)
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, RestoreAccountActivity::class.java)
    }
}
