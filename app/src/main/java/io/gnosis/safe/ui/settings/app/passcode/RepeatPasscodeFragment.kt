package io.gnosis.safe.ui.settings.app.passcode

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentPasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseStateViewModel
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.app.SettingsHandler
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import pm.gnosis.svalinn.common.utils.visible
import javax.inject.Inject

class RepeatPasscodeFragment : BaseViewBindingFragment<FragmentPasscodeBinding>() {

    override fun screenId() = ScreenId.PASSCODE_CREATE_REPEAT
    private val navArgs by navArgs<RepeatPasscodeFragmentArgs>()
    private val passcodeArg by lazy { navArgs.passcode }
    private val ownerImported by lazy { navArgs.ownerImported }

    @Inject
    lateinit var viewModel: PasscodeViewModel

    @Inject
    lateinit var settingsHandler: SettingsHandler

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPasscodeBinding =
        FragmentPasscodeBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        binding.input.setRawInputType(InputType.TYPE_CLASS_NUMBER)
        binding.input.delayShowKeyboardForView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.state.observe(viewLifecycleOwner, Observer {
            when (val viewAction = it.viewAction) {
                is BaseStateViewModel.ViewAction.ShowError -> {
                    when (viewAction.error) {
                        is PasscodeViewModel.OwnerRemovalFailed -> {
                            binding.errorMessage.setText(R.string.settings_passcode_owner_removal_failed)
                            binding.errorMessage.visible(true)
                        }
                        is PasscodeViewModel.PasscodeSetupFailed -> {
                            binding.errorMessage.setText(R.string.settings_passcode_setup_failed)
                            binding.errorMessage.visible(true)
                        }
                    }
                }
                is PasscodeViewModel.PasscodeSetup -> {
                    if (ownerImported) {
                        findNavController().popBackStack(R.id.ownerHowToAddFragment, true)
                    } else {
                        findNavController().popBackStack(R.id.createPasscodeFragment, true)
                    }

                    binding.input.hideSoftKeyboard()
                    findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.OWNER_IMPORT_RESULT, false)
                    findNavController().currentBackStackEntry?.savedStateHandle?.set(
                        SafeOverviewBaseFragment.PASSCODE_SET_RESULT,
                        true
                    )
                }
            }
        })

        with(binding) {
            createPasscode.setText(R.string.settings_passcode_repeat_the_6_digit_passcode)

            backButton.setOnClickListener {
                findNavController().popBackStack(R.id.repeatPasscodeFragment, true)
            }

            status.visibility = View.INVISIBLE

            val digits = listOf(digit1, digit2, digit3, digit4, digit5, digit6)
            input.showKeyboardForView()

            //Disable done button
            input.setOnEditorActionListener { _, actionId, _ ->
                actionId == EditorInfo.IME_ACTION_DONE
            }
            input.doOnTextChanged(onSixDigitsHandler(digits, requireContext()) { digitsAsString ->
                if (passcodeArg == digitsAsString) {
                    viewModel.setupPasscode(digitsAsString)
                } else {
                    errorMessage.visible(true)
                    input.setText("")
                    digits.forEach {
                        it.background =
                            ContextCompat.getDrawable(requireContext(), R.color.surface_01)
                    }
                }
            })

            // Skip Button
            actionButton.setOnClickListener {
                skipPasscodeSetup()
            }
        }
    }

    private fun FragmentPasscodeBinding.skipPasscodeSetup() {
        input.hideSoftKeyboard()

        settingsHandler.usePasscode = false
        tracker.setPasscodeIsSet(false)
        tracker.logPasscodeSkipped()

        if (ownerImported) {
            findNavController().popBackStack(R.id.ownerHowToAddFragment, true)
        } else {
            findNavController().popBackStack(R.id.createPasscodeFragment, true)
        }
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.OWNER_IMPORT_RESULT, false)
        findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.PASSCODE_SET_RESULT, false)
    }
}

