package com.example.verbolandia

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.verbolandia.databinding.ActivityMainBinding
import com.example.verbolandia.util.NotificationHelper
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                NotificationHelper.createNotificationChannel(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0,
            )
            insets
        }

        NotificationHelper.createNotificationChannel(this)
        checkAndRequestNotificationPermission()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        setupBackNavigation()
        com.example.verbolandia.util.AudioSpeaker.init(this)
        com.example.verbolandia.widget.VerbAppWidgetProvider.updateAllWidgets(this)
    }

    private var backPressedTime: Long = 0

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val navHostFragment = supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

                // 1. Si el buscador está enfocado o con texto en DictionaryFragment, cancelarlo primero
                if (currentFragment is DictionaryFragment && currentFragment.handleBackPressed()) {
                    return
                }

                val navController = navHostFragment?.navController
                val currentDestId = navController?.currentDestination?.id

                // 2. Si estamos en otra pestaña (Entrenamiento o Progreso), volver a la pestaña Diccionario
                if (currentDestId != null && currentDestId != R.id.nav_dictionary) {
                    navController.popBackStack(R.id.nav_dictionary, false)
                    binding.bottomNavigation.selectedItemId = R.id.nav_dictionary
                    return
                }

                // 3. Si estamos en la pantalla principal (Diccionario), requerir doble toque para salir
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < 2000) {
                    isEnabled = false
                    finish()
                } else {
                    backPressedTime = currentTime
                    Snackbar.make(
                        binding.root,
                        R.string.back_to_exit_warning,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val searchCard = findViewById<View>(R.id.cardSearch)
                val hitTarget = if (searchCard != null && isChildOf(v, searchCard)) searchCard else v
                val outRect = Rect()
                hitTarget.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isChildOf(child: View, parent: View): Boolean {
        var p = child.parent
        while (p != null) {
            if (p === parent) return true
            p = p.parent
        }
        return false
    }

    override fun onDestroy() {
        com.example.verbolandia.util.AudioSpeaker.shutdown()
        super.onDestroy()
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
