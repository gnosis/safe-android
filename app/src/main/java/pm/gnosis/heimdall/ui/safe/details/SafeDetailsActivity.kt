package pm.gnosis.heimdall.ui.safe.details

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.view.ViewPager
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.dialog_safe_add_input.view.*
import kotlinx.android.synthetic.main.dialog_show_qr_code.view.*
import kotlinx.android.synthetic.main.layout_safe_details.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.components.DaggerViewComponent
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.common.utils.snackbar
import pm.gnosis.heimdall.common.utils.subscribeForResult
import pm.gnosis.heimdall.common.utils.toast
import pm.gnosis.heimdall.data.repositories.TransactionType
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.reporting.ButtonId
import pm.gnosis.heimdall.reporting.Event
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.reporting.TabId
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.heimdall.ui.base.FactoryPagerAdapter
import pm.gnosis.heimdall.ui.dialogs.share.ShareSafeAddressDialog
import pm.gnosis.heimdall.ui.safe.details.info.SafeSettingsFragment
import pm.gnosis.heimdall.ui.safe.details.transactions.SafeTransactionsFragment
import pm.gnosis.heimdall.ui.tokens.balances.TokenBalancesFragment
import pm.gnosis.heimdall.ui.transactions.CreateTransactionActivity
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.hexAsBigIntegerOrNull
import pm.gnosis.utils.hexAsEthereumAddress
import timber.log.Timber
import javax.inject.Inject


class SafeDetailsActivity : BaseActivity() {

    override fun screenId() = ScreenId.SAFE_DETAILS

    @Inject
    lateinit var viewModel: SafeDetailsContract

    private val items = listOf(R.string.tab_title_assets, R.string.tab_title_transactions, R.string.tab_title_settings)
    private val generateQrCodeClicks = PublishSubject.create<String>()

    private lateinit var safeAddress: String
    private var safeName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inject()
        setContentView(R.layout.layout_safe_details)

        safeAddress = intent.getStringExtra(EXTRA_SAFE_ADDRESS)!!
        safeName = intent.getStringExtra(EXTRA_SAFE_NAME)
        updateTitle()
        viewModel.setup(safeAddress.hexAsEthereumAddress(), safeName)

        registerToolbar(layout_safe_details_toolbar)
        layout_safe_details_toolbar.inflateMenu(R.menu.safe_details_menu)
        layout_safe_details_toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.safe_details_menu_share -> ShareSafeAddressDialog.create(safeAddress).show(supportFragmentManager, null)
            }
            true
        }

        layout_safe_details_fab.setOnClickListener {
            eventTracker.submit(Event.ButtonClick(ButtonId.SAFE_DETAILS_CREATE_TRANSACTION))
            startActivity(CreateTransactionActivity.createIntent(this, safeAddress.hexAsBigIntegerOrNull(), TransactionType.TOKEN_TRANSFER, null))
        }
        layout_safe_details_viewpager.adapter = pagerAdapter()
        layout_safe_details_tabbar.setupWithViewPager(layout_safe_details_viewpager)
        layout_safe_details_viewpager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                layout_safe_details_appbar.setExpanded(true, true)
                positionToTabID(position)?.let { eventTracker.submit(Event.TabSelect(it)) }
            }
        })
    }

    private fun updateTitle() {
        if (!safeName.isNullOrBlank()) {
            layout_safe_details_toolbar.title = safeName
            layout_safe_details_toolbar.subtitle = safeAddress
        } else {
            layout_safe_details_toolbar.title = safeAddress
        }
    }

    override fun onStart() {
        super.onStart()
        disposables += viewModel.observeSafe()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    safeName = it.name
                    updateTitle()
                }, onError = Timber::e)

        disposables += generateQrCodeClicks
                .flatMapSingle {
                    viewModel.loadQrCode(it)
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnSuccess { onQrCodeLoading(true) }
                            .doAfterTerminate { onQrCodeLoading(false) }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeForResult(onNext = ::onQrCode, onError = ::onQrCodeError)
    }

    private fun onQrCode(qrCode: Bitmap) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_show_qr_code, null)
        dialogView.dialog_qr_code_image.setImageBitmap(qrCode)
        AlertDialog.Builder(this)
                .setView(dialogView)
                .show()
    }

    private fun onQrCodeError(throwable: Throwable) {
        Timber.e(throwable)
    }

    private fun onQrCodeLoading(isLoading: Boolean) {
        layout_safe_details_progress_bar.visibility = if (isLoading) View.VISIBLE else View.INVISIBLE
    }

    private fun positionToId(position: Int) = items.getOrElse(position, { -1 })

    private fun positionToTabID(position: Int) =
            when (positionToId(position)) {
                R.string.tab_title_settings -> {
                    TabId.SAFE_DETAILS_SETTINGS
                }
                R.string.tab_title_assets -> {
                    TabId.SAFE_DETAILS_ASSETS
                }
                R.string.tab_title_transactions -> {
                    TabId.SAFE_DETAILS_TRANSACTIONS
                }
                else -> null
            }

    private fun pagerAdapter() = FactoryPagerAdapter(supportFragmentManager, FactoryPagerAdapter.Factory(items.size, {
        when (positionToId(it)) {
            R.string.tab_title_settings -> {
                SafeSettingsFragment.createInstance(safeAddress.asEthereumAddressString())
            }
            R.string.tab_title_assets -> {
                TokenBalancesFragment.createInstance(safeAddress.asEthereumAddressString())
            }
            R.string.tab_title_transactions -> {
                SafeTransactionsFragment.createInstance(safeAddress.asEthereumAddressString())
            }
            else -> throw IllegalStateException("Unhandled tab position")
        }
    }, {
        getString(items[it])
    }))

    private fun inject() {
        DaggerViewComponent.builder()
                .applicationComponent(HeimdallApplication[this].component)
                .viewModule(ViewModule(this))
                .build()
                .inject(this)
    }

    companion object {
        private const val EXTRA_SAFE_NAME = "extra.string.safe_name"
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"

        fun createIntent(context: Context, safe: Safe): Intent {
            val intent = Intent(context, SafeDetailsActivity::class.java)
            intent.putExtra(EXTRA_SAFE_NAME, safe.name)
            intent.putExtra(EXTRA_SAFE_ADDRESS, safe.address.asEthereumAddressString())
            return intent
        }
    }
}
