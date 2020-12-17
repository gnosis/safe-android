package io.gnosis.safe.ui.settings.app.fiat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import io.gnosis.safe.ScreenId
import io.gnosis.safe.databinding.FragmentAppFiatBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.fragment.BaseViewBindingFragment

class AppFiatFragment: BaseViewBindingFragment<FragmentAppFiatBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAppFiatBinding =
        FragmentAppFiatBinding.inflate(inflater,container, false)

    override fun screenId(): ScreenId = ScreenId.SETTINGS_APP_FIAT

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding){
            backButton.setOnClickListener { findNavController().navigateUp() }
        }
    }
}
