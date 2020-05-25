package io.gnosis.safe.ui.safe.settings.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import io.gnosis.safe.R
import io.gnosis.safe.databinding.FragmentGetInTouchBinding
import io.gnosis.safe.di.components.ViewComponent
import io.gnosis.safe.ui.base.BaseFragment
import pm.gnosis.svalinn.common.utils.openUrl
import pm.gnosis.svalinn.common.utils.snackbar
import timber.log.Timber

class GetInTouchFragment : BaseFragment<FragmentGetInTouchBinding>() {

    override fun inject(component: ViewComponent) {
        component.inject(this)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentGetInTouchBinding =
        FragmentGetInTouchBinding.inflate(inflater, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as AppCompatActivity).setSupportActionBar(binding.getInTouchToolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)

        with(binding) {
            email.setOnClickListener {
                sendEmail()
            }
            discord.setOnClickListener {
                openDiscord()
            }
            twitter.setOnClickListener {
                openTwitter()
            }
            helpCenter.setOnClickListener {
                openHelpCenter()
            }
            featureSuggestion.setOnClickListener {
                openFeatureSuggestionPage()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigateUp()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openDiscord() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/${getString(R.string.id_discord)}")))
    }

    private fun openTwitter() {
        val intent = try {
            // get the Twitter app if possible
            requireContext().packageManager.getPackageInfo("com.twitter.android", 0)
            Intent(Intent.ACTION_VIEW, Uri.parse("twitter://user?screen_name=${getString(R.string.id_twitter)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: ActivityNotFoundException) {
            // no Twitter app, revert to browser
            Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/${getString(R.string.id_twitter)}"))
        }
        startActivity(intent)
    }

    private fun openHelpCenter() {
        requireContext().openUrl(getString(R.string.link_help_center))
    }

    private fun openFeatureSuggestionPage() {
        requireContext().openUrl(getString(R.string.link_feature_suggestion))
    }

    // Do we need telegram channel or is discord sufficient?
    private fun openTelegramChannel() {
        var intent = try {
            Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=${getString(R.string.id_telegram)}"))
        } catch (e: ActivityNotFoundException) { //App not found open in browser
            Intent(Intent.ACTION_VIEW, Uri.parse("http://www.telegram.me/${getString(R.string.id_telegram)}"))
        }
        startActivity(intent)
    }

    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            type = "text/html"
            data = Uri.parse("mailto:${getString(R.string.email_feedback)}")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject, getString(R.string.app_name)))
        }
        kotlin.runCatching {
            startActivity(intent)
        }
            .onFailure {
                Timber.e(it)
                snackbar(binding.root, getString(R.string.email_chooser_error), Snackbar.LENGTH_SHORT)
            }
    }
}
