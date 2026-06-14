package com.example.currencyconverter.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.lifecycle.lifecycleScope
import com.example.currencyconverter.R
import com.example.currencyconverter.databinding.ActivityCurrencyConverterBinding
import com.example.currencyconverter.repository.CurrencyRepository
import com.example.currencyconverter.utils.NumberFormatter
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CurrencyConverterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCurrencyConverterBinding
    private val repository = CurrencyRepository.getInstance()

    private val currencyItems = listOf(
        CurrencyItem("USD", "🇺🇸"),
        CurrencyItem("EUR", "🇪🇺"),
        CurrencyItem("RUB", "🇷🇺"),
        CurrencyItem("JPY", "🇯🇵"),
        CurrencyItem("GBP", "🇬🇧"),
        CurrencyItem("CNY", "🇨🇳")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCurrencyConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSpinners()
        setupActions()
        updateLastSyncText()
    }

    private fun setupToolbar() {
        binding.topAppBar.setOnMenuItemClickListener { item: MenuItem ->
            if (item.itemId == R.id.action_refresh) {
                refreshRates(forceSnackbar = true)
                true
            } else {
                false
            }
        }
    }

    private fun setupSpinners() {
        val adapter = CurrencySpinnerAdapter(this, currencyItems)
        binding.fromSpinner.adapter = adapter
        binding.toSpinner.adapter = adapter
        binding.fromSpinner.setSelection(0)
        binding.toSpinner.setSelection(1)
    }

    private fun setupActions() {
        binding.convertButton.setOnClickListener {
            if (isOnline()) {
                refreshRates(forceSnackbar = false, convertAfter = true)
            } else {
                convertUsingCache()
                showOfflineBanner()
            }
        }
    }

    private fun refreshRates(forceSnackbar: Boolean, convertAfter: Boolean = false) {
        val base = selectedFromCode()
        lifecycleScope.launch {
            val result = repository.refreshRates(base)
            result.onSuccess {
                hideOfflineBanner()
                updateLastSyncText()
                if (convertAfter) convertUsingCache()
                if (forceSnackbar) {
                    Snackbar.make(binding.root, "Rates updated", Snackbar.LENGTH_SHORT).show()
                }
            }.onFailure {
                showOfflineBanner()
                if (forceSnackbar) {
                    Snackbar.make(binding.root, "Update failed", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun convertUsingCache() {
        val amount = binding.amountEditText.text?.toString()?.toDoubleOrNull()
        if (amount == null) {
            binding.amountEditText.error = "Enter valid amount"
            return
        }

        val base = selectedFromCode()
        val target = selectedToCode()

        if (base == target) {
            updateResultText(amount)
            return
        }

        val rate = repository.getCachedRate(base, target)
        if (rate == null) {
            Snackbar.make(binding.root, "No cached data. Tap refresh online.", Snackbar.LENGTH_LONG).show()
            return
        }

        updateResultText(amount * rate)
    }

    private fun updateResultText(value: Double) {
        binding.resultTextView.text = NumberFormatter.formatAmount(value)
        animateResultHighlight()
    }

    private fun animateResultHighlight() {
        val startColor = Color.parseColor("#E8F5E9")
        val endColor = Color.WHITE
        val animator = ValueAnimator.ofArgb(startColor, endColor)
        animator.duration = 650
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener { animation ->
            binding.resultTextView.setBackgroundColor(animation.animatedValue as Int)
        }
        animator.doOnEnd {
            binding.resultTextView.setBackgroundResource(R.drawable.outlined_box_background)
        }
        animator.start()
    }

    private fun showOfflineBanner() {
        val lastSync = formatLastSync(repository.getLastSyncTimeMillis())
        binding.offlineBannerTextView.text = getString(R.string.offline_template, lastSync)
        binding.offlineBannerTextView.visibility = android.view.View.VISIBLE
    }

    private fun hideOfflineBanner() {
        binding.offlineBannerTextView.visibility = android.view.View.GONE
    }

    private fun updateLastSyncText() {
        val text = getString(
            R.string.last_sync,
            formatLastSync(repository.getLastSyncTimeMillis())
        )
        binding.lastSyncTextView.text = text
    }

    private fun formatLastSync(timestamp: Long): String {
        if (timestamp <= 0L) return "never"
        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun selectedFromCode(): String {
        return (binding.fromSpinner.selectedItem as CurrencyItem).code
    }

    private fun selectedToCode(): String {
        return (binding.toSpinner.selectedItem as CurrencyItem).code
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
