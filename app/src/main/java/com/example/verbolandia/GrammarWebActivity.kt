package com.example.verbolandia

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.verbolandia.databinding.ActivityGrammarWebBinding

class GrammarWebActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_VERB_BASE = "extra_verb_base"
    }

    private lateinit var binding: ActivityGrammarWebBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityGrammarWebBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        com.example.verbolandia.ui.AppBarHelper.configure(
            binding = binding.appBar,
            left = com.example.verbolandia.ui.LeftSlot.Back { finish() },
            eyebrow = getString(R.string.web_grammar_eyebrow),
            title = getString(R.string.web_grammar_title)
        )

        val verbBase = intent.getStringExtra(EXTRA_VERB_BASE) ?: "break"
        val encodedVerb = try {
            java.net.URLEncoder.encode(verbBase, "UTF-8")
        } catch (e: Exception) {
            verbBase
        }
        val customUrl = intent.getStringExtra(EXTRA_URL)
            ?: "https://translate.google.com/?sl=en&tl=es&text=$encodedVerb&op=translate"

        val myBrowser: WebView = binding.myWeb
        myBrowser.webViewClient = WebViewClient()
        myBrowser.settings.javaScriptEnabled = true
        myBrowser.settings.domStorageEnabled = true
        myBrowser.loadUrl(customUrl)
    }
}
