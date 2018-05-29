package pm.gnosis.heimdall.ui.security.unlock

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.editorActions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_unlock_dialog.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.ui.dialogs.base.BaseDialog
import pm.gnosis.heimdall.utils.disableAccessibility
import pm.gnosis.heimdall.utils.errorToast
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.svalinn.common.utils.vibrate
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.svalinn.security.*
import timber.log.Timber
import javax.inject.Inject


class UnlockDialog : BaseDialog() {

    @Inject
    lateinit var encryptionManager: EncryptionManager

    @Inject
    lateinit var viewModel: UnlockContract

    private var fingerPrintDisposable: Disposable? = null

    private var allowFingerprint = true

    override fun onCreate(savedInstanceState: Bundle?) {
        setStyle(DialogFragment.STYLE_NO_FRAME, 0)
        super.onCreate(savedInstanceState)

        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[context!!].component)
            .viewModule(ViewModule(context!!))
            .build().inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.layout_unlock_dialog, container, false)

    override fun onResume() {
        super.onResume()
        dialog.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onStart() {
        super.onStart()

        disposables += layout_unlock_dialog_alpha_background.clicks()
            .subscribeBy(onNext = {
                dismiss()
            })

        enableFingerprint()
        disposables += viewModel.checkState(true)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onState, onError = ::onStateCheckError)

        disposables += layout_unlock_dialog_switch_to_password.clicks()
            .subscribeBy(onNext = {
                togglePasswordInput(true)
                fingerPrintDisposable?.dispose()
            }, onError = Timber::e)

        layout_unlock_dialog_password_input.disableAccessibility()

        disposables += layout_unlock_dialog_password_input.editorActions()
            .filter { it == EditorInfo.IME_ACTION_DONE || it == EditorInfo.IME_NULL }
            .flatMap { viewModel.unlock(layout_unlock_dialog_password_input.text.toString()) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onState, onError = ::onStateCheckError)

        layout_unlock_dialog_password_input.keyPreImeListener = { keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                togglePasswordInput(false)
                enableFingerprint()
                true
            } else false
        }
    }

    private fun enableFingerprint() {
        // We try to enable fingerprint even so it is not allow, dismiss
        if (!allowFingerprint) {
            dismiss()
            return
        }
        fingerPrintDisposable = encryptionManager.isFingerPrintSet()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                allowFingerprint = it
                togglePasswordInput(!it)
            }
            .flatMapObservable {
                if (it) viewModel.observeFingerprint() else Observable.empty()
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onFingerprintResult, onError = ::onFingerprintError)
    }

    private fun togglePasswordInput(visible: Boolean) {
        layout_unlock_dialog_fingerprint.visible(!visible)
        layout_unlock_dialog_password_input.visible(visible)
        layout_unlock_dialog_progress.visible(false)
        if (visible) layout_unlock_dialog_password_input.showKeyboardForView()
        else (context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(
            layout_unlock_dialog_password_input.windowToken,
            0
        )
    }

    private fun onFingerprintResult(result: FingerprintUnlockResult) {
        layout_unlock_dialog_error.visibility = View.INVISIBLE
        when (result) {
            is FingerprintUnlockSuccessful -> onState(UnlockContract.State.UNLOCKED)
            is FingerprintUnlockFailed -> {
                context?.vibrate(200)
                layout_unlock_dialog_error.visibility = View.VISIBLE
                layout_unlock_dialog_error.text = getString(R.string.fingerprint_not_recognized)
            }
            is FingerprintUnlockHelp -> {
                result.message?.let {
                    layout_unlock_dialog_error.visibility = View.VISIBLE
                    layout_unlock_dialog_error.text = it
                }
            }
        }
    }

    private fun onFingerprintError(throwable: Throwable) {
        allowFingerprint = false
        context?.vibrate(200)
        togglePasswordInput(true)
        onStateCheckError(throwable)
    }

    private fun onState(state: UnlockContract.State) {
        when (state) {
            UnlockContract.State.UNINITIALIZED -> {
                dismiss()
            }
            UnlockContract.State.UNLOCKED -> {
                (context as? UnlockCallback)?.onUnlockSuccess()
                dismiss()
            }
            UnlockContract.State.LOCKED -> {
            }
        }
    }

    private fun onStateCheckError(throwable: Throwable) {
        context?.errorToast(throwable)
    }

    override fun onStop() {
        dismissAllowingStateLoss()
        super.onStop()
    }

    interface UnlockCallback {
        fun onUnlockSuccess()
    }
}
