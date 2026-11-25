package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.AuthProvider
import com.example.verbolandia.data.VerbRepository
import com.example.verbolandia.databinding.ActivityProfileBinding
import com.example.verbolandia.model.UserProfile
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Back { finish() },
            eyebrow = getString(R.string.profile_eyebrow),
            title = getString(R.string.profile_title)
        )

        val user = AuthProvider.getRepository(this).currentUser()
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        bindProfile(user)
        loadLocalStats()

        binding.btnSyncNow.setOnClickListener { syncNow() }
        binding.btnLogout.setOnClickListener {
            AuthProvider.getRepository(this).logout()
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun bindProfile(user: UserProfile) {
        binding.tvProfileName.text = user.name
        binding.tvProfileEmail.text = user.email
        binding.tvProfileLocation.text = getString(R.string.profile_location_format, user.city, user.country)
        binding.tvProfilePhone.text = getString(R.string.profile_phone_format, user.phone)
        updateSyncTimestamp(user.lastSyncedAt)
    }

    private fun loadLocalStats() {
        lifecycleScope.launch {
            val summary = VerbRepository.getInstance(this@ProfileActivity).getProgressSummary()
            binding.tvSyncMetrics.text = getString(
                R.string.profile_metrics_format,
                summary.masteredVerbsCount,
                summary.todayCompletedCount
            )
        }
    }

    private fun syncNow() {
        binding.btnSyncNow.isEnabled = false
        lifecycleScope.launch {
            val summary = VerbRepository.getInstance(this@ProfileActivity).getProgressSummary()
            val result = AuthProvider.getRepository(this@ProfileActivity)
                .syncProgress(summary.masteredVerbsCount, summary.todayCompletedCount)
            binding.btnSyncNow.isEnabled = true

            result.onSuccess { updated ->
                updateSyncTimestamp(updated.lastSyncedAt)
                Snackbar.make(binding.root, R.string.profile_sync_success, Snackbar.LENGTH_SHORT).show()
            }.onFailure {
                Snackbar.make(binding.root, R.string.auth_error_generic, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSyncTimestamp(timestampMs: Long) {
        val formatted = if (timestampMs > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(timestampMs))
        } else {
            getString(R.string.profile_sync_never)
        }
        binding.tvLastSynced.text = getString(R.string.profile_last_synced_format, formatted)
    }
}
