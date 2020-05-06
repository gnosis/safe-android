package io.gnosis.safe.ui.safe.terms

import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.svalinn.common.utils.edit
import javax.inject.Inject

class TermsChecker @Inject constructor(
    private val preferencesManager: PreferencesManager
) {
    fun setTermsAgreed(value: Boolean) {
        preferencesManager.prefs.edit {
            putBoolean(TERMS_AGREED, value)
        }
    }

    fun getTermsAgreed(): Boolean = preferencesManager.prefs.getBoolean(TERMS_AGREED, false)

    companion object {
        const val TERMS_AGREED = "prefs.boolean.terms_agreed"
    }
}
