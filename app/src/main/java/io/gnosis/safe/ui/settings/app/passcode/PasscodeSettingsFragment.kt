package io.gnosis.safe.ui.settings.app.passcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import io.gnosis.data.models.Safe
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentSettingsAppPasscodeBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.SafeOverviewBaseFragment
import io.gnosis.safe.ui.settings.app.SettingsHandler
import io.gnosis.safe.ui.settings.app.passcode.PasscodeCommand.*
import pm.gnosis.svalinn.common.utils.visible
import javax.inject.Inject

class PasscodeSettingsFragment : SafeOverviewBaseFragment<FragmentSettingsAppPasscodeBinding>() {

    override fun screenId() = ScreenId.SETTINGS_APP_PASSCODE

    @Inject
    lateinit var settingsHandler: SettingsHandler

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSettingsAppPasscodeBinding =
        FragmentSettingsAppPasscodeBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding) {
            backButton.setOnClickListener {
                Navigation.findNavController(it).navigateUp()
            }

            usePasscode.settingSwitch.isChecked = settingsHandler.usePasscode
            usePasscode.settingSwitch.setOnClickListener {
                if (settingsHandler.usePasscode) {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(DISABLE)
                    )
                } else {
                    settingsHandler.requirePasscodeToOpen = true
                    settingsHandler.requirePasscodeForConfirmations = true
                    settingsHandler.useBiometrics = false
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToCreatePasscodeFragment(ownerImported = false)
                    )
                }
                changePasscode.visible(settingsHandler.usePasscode)
            }
            changePasscode.visible(settingsHandler.usePasscode)
            changePasscode.setOnClickListener {
                findNavController().navigate(
                    PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToChangePasscodeFragment()
                )
            }

            useBiometrics.visible(
                settingsHandler.usePasscode && BiometricManager.from(requireContext()).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
            )
            useBiometrics.settingSwitch.isChecked = settingsHandler.useBiometrics
            useBiometrics.settingSwitch.setOnClickListener {
                if (useBiometrics.settingSwitch.isChecked) {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(BIOMETRICS_ENABLE)
                    )
                } else {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(BIOMETRICS_DISABLE)
                    )
                }
            }

            usePasscodeFor.visible(settingsHandler.usePasscode)

            // REQUIRE TO OPEN APP
            requireToOpen.visible(settingsHandler.usePasscode)
            requireToOpen.settingSwitch.isChecked = settingsHandler.requirePasscodeToOpen
            requireToOpen.settingSwitch.setOnClickListener {
                // If both are disabled, disable passcode feature
                if (!requireForConfirmations.settingSwitch.isChecked && !requireToOpen.settingSwitch.isChecked) {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(DISABLE)
                    )
                } else if (requireToOpen.settingSwitch.isChecked) {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(APP_ENABLE)
                    )
                } else {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(APP_DISABLE)
                    )
                }
            }

            // REQUIRE FOR CONFIRMATIONS
            requireForConfirmations.visible(settingsHandler.usePasscode)
            requireForConfirmations.settingSwitch.isChecked = settingsHandler.requirePasscodeForConfirmations
            requireForConfirmations.settingSwitch.setOnClickListener {
                // If both are disabled, disable passcode feature
                if (!requireForConfirmations.settingSwitch.isChecked && !requireToOpen.settingSwitch.isChecked) {
                    findNavController().navigate(PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(DISABLE))
                } else if (requireForConfirmations.settingSwitch.isChecked) {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(CONFIRMATION_ENABLE)
                    )
                } else {
                    findNavController().navigate(
                        PasscodeSettingsFragmentDirections.actionPasscodeSettingsFragmentToConfigurePasscodeFragment(CONFIRMATION_DISABLE)
                    )
                }
            }
        }
    }

    override fun handleActiveSafe(safe: Safe?) {
        // ignored for now
    }

    override fun onResume() {
        super.onResume()
        with(binding) {
            usePasscode.settingSwitch.isChecked = settingsHandler.usePasscode
            requireForConfirmations.settingSwitch.isChecked = settingsHandler.requirePasscodeForConfirmations
            requireToOpen.settingSwitch.isChecked = settingsHandler.requirePasscodeToOpen
            useBiometrics.settingSwitch.isChecked = settingsHandler.useBiometrics
        }
    }
}
