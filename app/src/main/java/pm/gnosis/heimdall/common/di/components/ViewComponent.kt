package pm.gnosis.heimdall.common.di.components

import dagger.Component
import pm.gnosis.heimdall.common.di.ForView
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.ui.account.AccountActivity
import pm.gnosis.heimdall.ui.addressbook.add.AddressBookAddEntryActivity
import pm.gnosis.heimdall.ui.addressbook.detail.AddressBookEntryDetailsActivity
import pm.gnosis.heimdall.ui.addressbook.list.AddressBookActivity
import pm.gnosis.heimdall.ui.authenticate.AuthenticateActivity
import pm.gnosis.heimdall.ui.dialogs.share.ShareSafeAddressDialog
import pm.gnosis.heimdall.ui.dialogs.share.SimpleAddressShareDialog
import pm.gnosis.heimdall.ui.dialogs.transaction.CreateTokenTransactionProgressDialog
import pm.gnosis.heimdall.ui.onboarding.GenerateMnemonicActivity
import pm.gnosis.heimdall.ui.onboarding.RestoreAccountActivity
import pm.gnosis.heimdall.ui.safe.add.AddExistingSafeFragment
import pm.gnosis.heimdall.ui.safe.add.DeployNewSafeFragment
import pm.gnosis.heimdall.ui.safe.details.SafeDetailsActivity
import pm.gnosis.heimdall.ui.safe.details.info.SafeSettingsFragment
import pm.gnosis.heimdall.ui.safe.details.transactions.SafeTransactionsFragment
import pm.gnosis.heimdall.ui.safe.overview.SafesOverviewActivity
import pm.gnosis.heimdall.ui.security.SecurityActivity
import pm.gnosis.heimdall.ui.settings.network.NetworkSettingsActivity
import pm.gnosis.heimdall.ui.settings.tokens.TokenManagementActivity
import pm.gnosis.heimdall.ui.splash.SplashActivity
import pm.gnosis.heimdall.ui.tokens.add.AddTokenActivity
import pm.gnosis.heimdall.ui.tokens.balances.TokenBalancesFragment
import pm.gnosis.heimdall.ui.tokens.info.TokenInfoActivity
import pm.gnosis.heimdall.ui.transactions.CreateTransactionActivity
import pm.gnosis.heimdall.ui.transactions.ViewTransactionActivity
import pm.gnosis.heimdall.ui.transactions.details.AssetTransferTransactionDetailsFragment
import pm.gnosis.heimdall.ui.transactions.details.GenericTransactionDetailsFragment

@ForView
@Component(
        dependencies = [ApplicationComponent::class],
        modules = [ViewModule::class]
)
interface ViewComponent {
    // Fragments

    fun inject(fragment: AddExistingSafeFragment)
    fun inject(fragment: AssetTransferTransactionDetailsFragment)
    fun inject(fragment: DeployNewSafeFragment)
    fun inject(fragment: GenericTransactionDetailsFragment)
    fun inject(fragment: SafeSettingsFragment)
    fun inject(fragment: SafeTransactionsFragment)
    fun inject(fragment: TokenBalancesFragment)

    // Activities

    fun inject(activity: AccountActivity)
    fun inject(activity: AddressBookActivity)
    fun inject(activity: AddressBookAddEntryActivity)
    fun inject(activity: AddressBookEntryDetailsActivity)
    fun inject(activity: AddTokenActivity)
    fun inject(activity: AuthenticateActivity)
    fun inject(activity: CreateTransactionActivity)
    fun inject(activity: GenerateMnemonicActivity)
    fun inject(activity: NetworkSettingsActivity)
    fun inject(activity: RestoreAccountActivity)
    fun inject(activity: SafeDetailsActivity)
    fun inject(activity: SafesOverviewActivity)
    fun inject(activity: SecurityActivity)
    fun inject(activity: SplashActivity)
    fun inject(activity: TokenManagementActivity)
    fun inject(activity: TokenInfoActivity)
    fun inject(activity: ViewTransactionActivity)

    // Dialogs

    fun inject(dialog: CreateTokenTransactionProgressDialog)
    fun inject(dialog: ShareSafeAddressDialog)
    fun inject(dialog: SimpleAddressShareDialog)
}
