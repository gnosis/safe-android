package io.gnosis.safe.ui.settings.app.passcode

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import io.gnosis.safe.R
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentPasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment
import io.gnosis.safe.ui.settings.app.SettingsHandler
import pm.gnosis.svalinn.common.utils.showKeyboardForView
import javax.inject.Inject

class CreatePasscodeFragment : BaseViewBindingFragment<FragmentPasscodeBinding>() {

    override fun screenId() = ScreenId.CREATE_PASSCODE
    private val navArgs by navArgs<CreatePasscodeFragmentArgs>()
    private val ownerImported by lazy { navArgs.ownerImported }

    @Inject
    lateinit var settingsHandler: SettingsHandler

    companion object {
        fun newInstance() = CreatePasscodeFragment()
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPasscodeBinding =
        FragmentPasscodeBinding.inflate(inflater, container, false)

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onResume() {
        super.onResume()
        binding.input.showKeyboardForView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding) {

            if (ownerImported) {
                status.visibility = View.VISIBLE
            } else {
                status.visibility = View.INVISIBLE
            }

            createPasscode.setText(R.string.settings_create_passcode_create_a_6_digit_passcode)
            backButton.setOnClickListener {
                findNavController().popBackStack(R.id.createPasscodeFragment, true)
            }

            val digits = listOf(digit1, digit2, digit3, digit4, digit5, digit6)
            input.showKeyboardForView()

            //Disable done button
            input.setOnEditorActionListener { _, actionId, _ ->
                actionId == EditorInfo.IME_ACTION_DONE
            }

            input.doOnTextChanged { text, _, _, _ ->
                text?.let {
                    if (input.text.length < 6) {
                        digits.forEach {
                            it.background = ContextCompat.getDrawable(requireContext(), R.color.surface_01)
                        }
                        (1..text.length).forEach { i ->
                            digits[i - 1].background = ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle_passcode_filled_20dp)
                        }
                    } else {
                        input.setText("") // So it is empty, when the user navigates back
                        findNavController().navigate(
                            CreatePasscodeFragmentDirections.actionCreatePasscodeFragmentToRepeatPasscodeFragment(
                                passcode = text.toString(),
                                ownerImported = ownerImported
                            )
                        )
                    }
                }
            }

            skipButton.setOnClickListener {
                input.hideSoftKeyboard()
                settingsHandler.usePasscode = false
                if (ownerImported) {
                    findNavController().popBackStack(R.id.ownerInfoFragment, true)
                } else {
                    findNavController().popBackStack(R.id.createPasscodeFragment, true)
                }
                findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.OWNER_IMPORT_RESULT, false)
                findNavController().currentBackStackEntry?.savedStateHandle?.set(SafeOverviewBaseFragment.PASSCODE_SET_RESULT, false)
            }
        }
    }
}

fun View.hideSoftKeyboard() {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(windowToken, 0)
}