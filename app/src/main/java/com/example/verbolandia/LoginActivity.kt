package com.example.verbolandia

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.verbolandia.data.AuthException
import com.example.verbolandia.data.AuthProvider
import com.example.verbolandia.data.AuthValidator
import com.example.verbolandia.databinding.ActivityLoginBinding
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppBarHelper.configure(
            binding = binding.appBar,
            left = LeftSlot.Back { finish() },
            eyebrow = getString(R.string.auth_eyebrow),
            title = getString(R.string.auth_login_title)
        )

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnGoToSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        binding.tilEmail.error = null
        binding.tilPassword.error = null

        val validationError = AuthValidator.validateLogin(email, password)
        if (validationError != null) {
            when (validationError) {
                AuthValidator.ErrorType.INVALID_EMAIL ->
                    binding.tilEmail.error = getString(R.string.auth_error_invalid_email)
                AuthValidator.ErrorType.SHORT_PASSWORD ->
                    binding.tilPassword.error = getString(R.string.auth_error_short_password)
                else -> Unit
            }
            return
        }

        setLoading(true)
        val repository = AuthProvider.getRepository(this)
        lifecycleScope.launch {
            val result = repository.login(email, password)
            setLoading(false)
            result.onSuccess {
                startActivity(Intent(this@LoginActivity, ProfileActivity::class.java))
                finish()
            }.onFailure { error ->
                val messageRes = if (error is AuthException &&
                    error.code == AuthException.ErrorCode.INVALID_CREDENTIALS
                ) {
                    R.string.auth_error_invalid_credentials
                } else {
                    R.string.auth_error_generic
                }
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressAuth.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoToSignup.isEnabled = !isLoading
    }
}
