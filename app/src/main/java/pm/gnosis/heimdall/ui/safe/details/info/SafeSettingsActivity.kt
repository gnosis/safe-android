package pm.gnosis.heimdall.ui.safe.details.info

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.StringRes
import android.support.v7.app.AlertDialog
import com.jakewharton.rxbinding2.support.v4.widget.refreshes
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.layout_additional_owner_item.view.*
import kotlinx.android.synthetic.main.layout_safe_settings.*
import kotlinx.android.synthetic.main.layout_simple_spinner_item.view.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.GnosisSafeExtensionRepository
import pm.gnosis.heimdall.data.repositories.models.SafeInfo
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.addressbook.helpers.AddressInfoViewHolder
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.heimdall.ui.base.InflatingViewProvider
import pm.gnosis.heimdall.ui.safe.main.SafeMainActivity
import pm.gnosis.heimdall.utils.errorSnackbar
import pm.gnosis.heimdall.utils.setupToolbar
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.snackbar
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SafeSettingsActivity : BaseActivity() {

    @Inject
    lateinit var viewModel: SafeSettingsContract

    private val removeSafeClicks = PublishSubject.create<Unit>()

    private val viewProvider by lazy {
        InflatingViewProvider(
            layoutInflater,
            layout_safe_settings_owners_container,
            R.layout.layout_additional_owner_item
        )
    }

    override fun screenId() = ScreenId.SAFE_SETTINGS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()
        setContentView(R.layout.layout_safe_settings)
        setupToolbar(layout_safe_settings_toolbar)

        intent.extras.getString(EXTRA_SAFE_ADDRESS).asEthereumAddress()?.let {
            viewModel.setup(it)
        } ?: finish()
    }

    override fun onStart() {
        super.onStart()
        disposables += layout_safe_settings_swipe_refresh.refreshes()
            .map { true }
            .startWith(false)
            .flatMap {
                viewModel.loadSafeInfo(it)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { showLoading(true) }
                    .doOnComplete { showLoading(false) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(::updateInfo, ::handleError)

        disposables += layout_safe_settings_name_input.textChanges()
            .skipInitialValue()
            .debounce(500, TimeUnit.MILLISECONDS)
            // We need to map to string else distinctUntilChanged will not work
            .map { it.toString() }
            .distinctUntilChanged()
            .flatMapSingle {
                viewModel.updateSafeName(it)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult({}, { errorSnackbar(layout_safe_settings_name_input, it) })

        disposables += viewModel.loadSafeName()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe { layout_safe_settings_name_input.isEnabled = false }
            .doAfterTerminate { layout_safe_settings_name_input.isEnabled = true }
            .subscribeBy(onSuccess = {
                layout_safe_settings_toolbar.title = it
                layout_safe_settings_name_input.setText(it)
                layout_safe_settings_name_input.setSelection(it.length)
            }, onError = Timber::e)

        disposables += layout_safe_settings_delete_button.clicks()
            .subscribeBy(onNext = { showRemoveDialog() }, onError = Timber::e)

        disposables += removeSafeClicks
            .flatMapSingle { viewModel.deleteSafe() }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = { onSafeRemoved() }, onError = ::onSafeRemoveError)
    }

    private fun safeNameOrPlaceHolder(@StringRes placeholderRef: Int): String {
        val name = layout_safe_settings_name_input.text.toString()
        return if (name.isBlank()) getString(placeholderRef) else name
    }

    private fun onSafeRemoved() {
        toast(getString(R.string.safe_remove_success, safeNameOrPlaceHolder(R.string.safe)))
        startActivity(SafeMainActivity.createIntent(this))
    }

    private fun onSafeRemoveError(throwable: Throwable) {
        snackbar(layout_safe_settings_swipe_refresh, R.string.safe_remove_error)
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[this].component)
            .viewModule(ViewModule(this))
            .build().inject(this)
    }

    private fun showRemoveDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_safe_dialog_title)
            .setMessage(getString(R.string.remove_safe_dialog_description, safeNameOrPlaceHolder(R.string.this_safe)))
            .setPositiveButton(R.string.remove, { _, _ -> removeSafeClicks.onNext(Unit) })
            .setNegativeButton(R.string.cancel, { _, _ -> })
            .show()
    }

    private fun showLoading(loading: Boolean) {
        layout_safe_settings_swipe_refresh.isRefreshing = loading
    }

    private fun handleError(throwable: Throwable) {
        errorSnackbar(layout_safe_settings_coordinator, throwable)
    }

    private fun updateInfo(info: SafeInfo) {
        layout_safe_settings_confirmations.text =
                getString(R.string.safe_confirmations_text, info.requiredConfirmations.toString(), info.owners.size.toString())

        layout_safe_settings_owners_container.removeAllViews()
        info.owners.forEach(::addOwner)

        layout_safe_settings_add_owner_button.visible(false)

        layout_safe_settings_add_recovery_option_button.visible(false)
        layout_safe_settings_extensions_container.removeAllViews()
        disposables += viewModel.loadExtensionsInfo(info.extensions)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = {
                it.second.forEachIndexed(::addExtension)
            }, onError = {
                errorSnackbar(layout_safe_settings_add_recovery_option_button, it)
            })
    }

    private fun addOwner(address: Solidity.Address) {
        AddressInfoViewHolder(this, viewProvider).apply {
            bind(address)
            view.layout_additional_owner_delete_button.visible(false)
            layout_safe_settings_owners_container.addView(view)
        }
    }

    private fun addExtension(index: Int, extension: Pair<GnosisSafeExtensionRepository.Extension, Solidity.Address>) {
        val extensionView = layoutInflater.inflate(R.layout.layout_simple_spinner_item, layout_safe_settings_extensions_container, false)
        extensionView.layout_simple_spinner_item_name.setText(
            when (extension.first) {
                GnosisSafeExtensionRepository.Extension.SINGLE_ACCOUNT_RECOVERY -> R.string.single_account_recovery_extension
                GnosisSafeExtensionRepository.Extension.SOCIAL_RECOVERY -> R.string.social_recovery_extension
                GnosisSafeExtensionRepository.Extension.DAILY_LIMIT -> R.string.daily_limit_extension
                GnosisSafeExtensionRepository.Extension.UNKNOWN -> R.string.unknown_extension
            }
        )
        extensionView.layout_simple_spinner_item_address.text = extension.second.asEthereumAddressString()
        layout_safe_settings_extensions_container.addView(extensionView)

    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "argument.string.safe_address"

        fun createIntent(context: Context, address: String) =
            Intent(context, SafeSettingsActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, address)
            }
    }
}
