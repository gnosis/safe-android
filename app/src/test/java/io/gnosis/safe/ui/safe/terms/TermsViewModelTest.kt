package io.gnosis.safe.ui.safe.terms

import android.app.Application
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pm.gnosis.svalinn.common.PreferencesManager
import pm.gnosis.tests.utils.TestPreferences

class TermsViewModelTest {

    private lateinit var preferences: TestPreferences
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var termsChecker: TermsChecker
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        preferences = spyk()
        val application = mockk<Application>().apply {
            every { getSharedPreferences(any(), any()) } returns preferences
        }
        preferencesManager = PreferencesManager(application)
    }

    @Test
    fun `If terms already agreed, advance() method should be called and bottom sheet should not be shown`() {
        termsChecker = mockk(relaxed = true)
        every { termsChecker.getTermsAgreed() } returns true

        val viewModel = TermsViewModel(termsChecker)

        val callback: () -> Unit = mockk(relaxed = true)

        viewModel.checkTerms(callback)

        verify { callback.invoke() }
    }

    @Test
    fun `If terms not agreed, advance() method should not be called when agree button not hit`() {
        termsChecker = mockk(relaxed = true)
        every { termsChecker.getTermsAgreed() } returns false

        val viewModel = TermsViewModel(termsChecker)
        val callback: () -> Unit = mockk(relaxed = true)

        viewModel.checkTerms(callback)

        verify(exactly = 0) { callback.invoke() }
    }

    @Test
    fun `Test if advance is called when user clicks agree`() {
        termsChecker = TermsChecker(preferencesManager)
        assertFalse(termsChecker.getTermsAgreed())

        val viewModel = TermsViewModel(termsChecker)
        val callback: () -> Unit = mockk(relaxed = true)

        viewModel.checkTerms(callback)
        viewModel.onAgreeClicked()

        verify { callback.invoke() }
        assertTrue(termsChecker.getTermsAgreed())
    }
}
