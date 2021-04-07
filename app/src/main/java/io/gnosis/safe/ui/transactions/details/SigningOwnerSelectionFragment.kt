package io.gnosis.safe.ui.transactions.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentSigningOwnerSelectionBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.owner.list.LocalOwners
import io.gnosis.safe.ui.settings.owner.list.OwnerListAdapter
import io.gnosis.safe.ui.settings.owner.list.OwnerListState
import io.gnosis.safe.ui.settings.owner.list.OwnerListViewModel
import io.gnosis.safe.utils.showConfirmDialog
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber
import javax.inject.Inject
import io.gnosis.safe.ui.settings.owner.list.OwnerListAdapter.OwnerListener as OwnerListener1

class SigningOwnerSelectionFragment : BaseViewBindingFragment<FragmentSigningOwnerSelectionBinding>(), OwnerListener1 {

    override fun screenId(): ScreenId? = ScreenId.OWNER_LIST
    private val navArgs by navArgs<SigningOwnerSelectionFragmentArgs>()
    private val missingSigners by lazy { navArgs.missingSigners }
    // TODO: add navarg for rejection or confirmation

    lateinit var adapter: OwnerListAdapter

    @Inject
    lateinit var viewModel: OwnerListViewModel


    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSigningOwnerSelectionBinding =
        FragmentSigningOwnerSelectionBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadOwners(missingSigners?.toList())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = OwnerListAdapter(this, true)

        with(binding) {
            backButton.setOnClickListener {
                findNavController().navigateUp()
            }
            val dividerItemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
            dividerItemDecoration.setDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.divider)!!)
            owners.addItemDecoration(dividerItemDecoration)
            owners.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            owners.adapter = adapter
        }

        viewModel.state.observe(viewLifecycleOwner, Observer { state ->
            when (state) {
                is OwnerListState -> {
                    state.viewAction?.let { action ->
                        when (action) {
                            is BaseStateViewModel.ViewAction.Loading -> {
                                binding.progress.visible(true)
                            }
                            is LocalOwners -> {
                                binding.progress.visible(false)
                                if (action.owners.isEmpty()) {
                                    showEmptyState()
                                } else {
                                    adapter.updateData(action.owners)
                                    showList()
                                }
                            }
                            is BaseStateViewModel.ViewAction.ShowError -> {
                                binding.progress.visible(false)
                            }
                            is ConfirmConfirmation -> {
                                showConfirmDialog(
                                    requireContext(),
                                    message = R.string.confirm_transaction_dialog_message,
                                    confirm = R.string.confirm,
                                    confirmColor = R.color.primary
                                ) {
                                    findNavController().popBackStack(R.id.signingOwnerSelectionFragment, true)
                                    findNavController().currentBackStackEntry?.savedStateHandle?.set(
                                        SafeOverviewBaseFragment.OWNER_SELECTED_RESULT,
                                        action.owner.asEthereumAddressString()
                                    )
                                }

                            }
                            is BaseStateViewModel.ViewAction.NavigateTo -> {
                                findNavController().navigate(action.navDirections)
                            }
                            else -> {
                                Timber.e("Unexpected action: $action")
                            }
                        }
                    }
                }

            }
        })
//        updateUi()
    }

    override fun onOwnerRemove(owner: Solidity.Address, position: Int) {
        // ignored
    }

    override fun onOwnerEdit(owner: Solidity.Address) {
        //ignored
    }

    override fun onOwnerClick(owner: Solidity.Address) {
        viewModel.selectKeyForSigning(owner)
    }

    private fun showList() {
        with(binding) {
            owners.visible(true)
            emptyPlaceholder.visible(false)
        }
    }

    private fun showEmptyState() {
        with(binding) {
            owners.visible(false)
            emptyPlaceholder.visible(true)
        }
    }
}
