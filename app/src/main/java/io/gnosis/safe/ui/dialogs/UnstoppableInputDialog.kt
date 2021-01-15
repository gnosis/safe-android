package io.gnosis.safe.ui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.unstoppabledomains.exceptions.ns.NSExceptionCode
import com.unstoppabledomains.exceptions.ns.NamingServiceException
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.DialogUnstoppableInputBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.helpers.AddressHelper
import io.gnosis.safe.toError
import io.gnosis.safe.ui.base.fragment.BaseViewBindingDialogFragment
import io.gnosis.safe.utils.debounce
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.svalinn.common.utils.visible
import javax.inject.Inject

class UnstoppableInputDialog : BaseViewBindingDialogFragment<DialogUnstoppableInputBinding>() {

    @Inject
    lateinit var viewModel: UnstoppableInputViewModel

    @Inject
    lateinit var addressHelper: AddressHelper

    var callback: ((Solidity.Address) -> Unit)? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        setStyle(STYLE_NO_FRAME, R.style.DayNightFullscreenDialog)
        super.onCreate(savedInstanceState)
    }

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): DialogUnstoppableInputBinding =
            DialogUnstoppableInputBinding.inflate(inflater, container, false)

    override fun screenId(): ScreenId? = ScreenId.SAFE_ADD_ENS

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            backButton.setOnClickListener { dismiss() }
            confirmButton.setOnClickListener { onClick.offer(Unit) }
            dialogUnstoppableInputDomain.showKeyboardForView()
        }
    }

    override fun onStart() {
        super.onStart()
        processInput()
        lifecycleScope.launch {
            onClick.asFlow().collect {
                onNewAddress.valueOrNull?.let { propagateResult(it) }
            }
        }
    }

    private val onNewAddress = ConflatedBroadcastChannel<Solidity.Address?>()
    private val onClick = ConflatedBroadcastChannel<Unit>()

    private fun onUrlAvailable(string: String) {
        lifecycleScope.launch {
            runCatching { viewModel.processInput(string) }
                    .onSuccess { address ->
                        binding.dialogEnsInputProgress.visible(false)
                        binding.confirmButton.isEnabled = true
                        binding.successViews.visible(true)
                        binding.dialogUnstoppableDomainLayout.isErrorEnabled = false
                        onNewAddress.offer(address)
                        addressHelper.populateAddressInfo(
                                binding.dialogUnstoppableInputAddress,
                                binding.dialogEnsInputAddressImage,
                                address
                        )
                    }
                    .onFailure {
                        binding.dialogEnsInputProgress.visible(false)
                        binding.confirmButton.isEnabled = false
                        binding.successViews.visible(false)

                        val error = when(it.cause) {
                            is NamingServiceException -> it.cause!!.toError()
                            else -> it.toError();
                        }

                        binding.dialogUnstoppableDomainLayout.error =
                                error.message(
                                        requireContext(),
                                        R.string.error_description_ens_name
                                )

                        binding.dialogUnstoppableDomainLayout.isErrorEnabled = true

                        onNewAddress.offer(null)
                    }
        }
    }

    val onUrlChanged: (String) -> Job? = debounce(1000, lifecycleScope, this::onUrlAvailable)

    private fun processInput() {
        var job: Job? = null
        binding.dialogUnstoppableInputDomain.doOnTextChanged { text, _, _, _ ->
            binding.successViews.visible(false)
            binding.dialogUnstoppableDomainLayout.isErrorEnabled = false
            if (text.toString().isNotEmpty()) {
                binding.dialogEnsInputProgress.visible(true)
                job = onUrlChanged(text.toString())
            } else {
                binding.dialogEnsInputProgress.visible(false)
                job?.cancel("Empty Unstoppable domain")
                binding.confirmButton.isEnabled = false
            }
        }
    }

    private fun propagateResult(state: Solidity.Address) {
        callback?.invoke(state)
        onNewAddress.close()
        onClick.close()
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        callback = null
        super.onDismiss(dialog)
    }

    companion object {
        fun create() = UnstoppableInputDialog()
    }
}
