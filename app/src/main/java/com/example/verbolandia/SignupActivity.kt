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
import com.example.verbolandia.databinding.ActivitySignupBinding
import com.example.verbolandia.ui.AppBarHelper
import com.example.verbolandia.ui.LeftSlot
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
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
            title = getString(R.string.auth_signup_title)
        )

        binding.btnCreateAccount.setOnClickListener { attemptSignup() }
        binding.btnBackToLogin.setOnClickListener { finish() }
    }

    private fun attemptSignup() {
        val name = binding.etName.text?.toString().orEmpty()
        val city = binding.etCity.text?.toString().orEmpty()
        val country = binding.etCountry.text?.toString().orEmpty()
        val phone = binding.etPhone.text?.toString().orEmpty()
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        clearErrors()

        val error = AuthValidator.validateSignup(name, city, country, phone, email, password)
        if (error != null) {
            showFieldError(error)
            return
        }

        setLoading(true)
        val repository = AuthProvider.getRepository(this)
        lifecycleScope.launch {
            val result = repository.signup(name, city, country, phone, email, password)
            setLoading(false)
            result.onSuccess {
                val intent = Intent(this@SignupActivity, ProfileActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }.onFailure { throwable ->
                val messageRes = if (throwable is AuthException &&
                    throwable.code == AuthException.ErrorCode.EMAIL_ALREADY_EXISTS
                ) {
                    R.string.auth_error_email_exists
                } else {
                    R.string.auth_error_generic
                }
                Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilCity.error = null
        binding.tilCountry.error = null
        binding.tilPhone.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
    }

    private fun showFieldError(error: AuthValidator.ErrorType) {
        when (error) {
            AuthValidator.ErrorType.EMPTY_NAME ->
                binding.tilName.error = getString(R.string.auth_error_empty_name)
            AuthValidator.ErrorType.EMPTY_CITY ->
                binding.tilCity.error = getString(R.string.auth_error_empty_city)
            AuthValidator.ErrorType.EMPTY_COUNTRY ->
                binding.tilCountry.error = getString(R.string.auth_error_empty_country)
            AuthValidator.ErrorType.INVALID_PHONE ->
                binding.tilPhone.error = getString(R.string.auth_error_invalid_phone)
            AuthValidator.ErrorType.INVALID_EMAIL ->
                binding.tilEmail.error = getString(R.string.auth_error_invalid_email)
            AuthValidator.ErrorType.SHORT_PASSWORD ->
                binding.tilPassword.error = getString(R.string.auth_error_short_password)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressSignup.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnCreateAccount.isEnabled = !isLoading
        binding.btnBackToLogin.isEnabled = !isLoading
    }
}
