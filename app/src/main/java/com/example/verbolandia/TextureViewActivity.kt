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

        com.example.verbolandia.ui.AppBarHelper.configure(
            binding = binding.appBar,
            left = com.example.verbolandia.ui.LeftSlot.Back { finish() },
            eyebrow = getString(R.string.texture_eyebrow),
            title = getString(R.string.texture_title)
        )

        binding.myTextureView.surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        binding.tvTextureStatus.text = getString(R.string.texture_status_ready, width, height)
        Toast.makeText(this, getString(R.string.texture_toast_ready), Toast.LENGTH_SHORT).show()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        binding.tvTextureStatus.text = getString(R.string.texture_status_resized, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        binding.tvTextureStatus.text = getString(R.string.texture_status_destroyed)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}
