package io.gnosis.safe.ui.settings.owner

import io.gnosis.data.repositories.CredentialsRepository
import io.gnosis.safe.notifications.NotificationRepository
import io.gnosis.safe.Tracker
import io.gnosis.safe.ui.base.AppDispatchers
import io.gnosis.safe.ui.base.BaseStateViewModel
import pm.gnosis.model.Solidity
import javax.inject.Inject

class OwnerEditNameViewModel
@Inject constructor(
    private val credentialsRepository: CredentialsRepository,
    private val notificationRepository: NotificationRepository,
    private val tracker: Tracker,
    appDispatchers: AppDispatchers
) : BaseStateViewModel<OwnerNameState>(appDispatchers) {

    override fun initialState() = OwnerNameState(null, ViewAction.Loading(true))

    fun loadOwnerName(address: Solidity.Address) {
        safeLaunch {
            val owner = credentialsRepository.owner(address)
            updateState { OwnerNameState(owner?.name, ViewAction.None) }
        }
    }

    fun saveOwnerName(address: Solidity.Address, name: String) {
        safeLaunch {
            credentialsRepository.owner(address)?.let {
                credentialsRepository.saveOwner(it.copy(name = name))
                updateState { OwnerNameState(name, ViewAction.CloseScreen) }
            }
        }
    }
}

data class OwnerNameState(
    val name: String?,
    override var viewAction: BaseStateViewModel.ViewAction?
) : BaseStateViewModel.State
