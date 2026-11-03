package com.example.verbolandia

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.verbolandia.databinding.ActivityTextureViewBinding

class TextureViewActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    private lateinit var binding: ActivityTextureViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTextureViewBinding.inflate(layoutInflater)
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

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.myTextureView.surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        binding.tvTextureStatus.text = "SurfaceTexture lista (${width}x${height}). Renderizando stream..."
        Toast.makeText(this, "TextureView lista para renderizado", Toast.LENGTH_SHORT).show()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        binding.tvTextureStatus.text = "Tamaño cambiado: ${width}x${height}"
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        binding.tvTextureStatus.text = "SurfaceTexture destruida"
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
