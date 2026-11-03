package com.example.verbolandia

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.verbolandia.databinding.ActivityVideoTutorialBinding

class VideoTutorialActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_VERB_TITLE = "extra_verb_title"
    }

    private lateinit var binding: ActivityVideoTutorialBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVideoTutorialBinding.inflate(layoutInflater)
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

        val verbTitle = intent.getStringExtra(EXTRA_VERB_TITLE) ?: "Verbos en Inglés"
        binding.tvVideoDescTitle.text = "Lección de Pronunciación: $verbTitle"

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
            ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

        val videoView: VideoView = findViewById(R.id.videoView)
        val myMediaController = MediaController(this)
        myMediaController.setAnchorView(videoView)

        val videoUri: Uri = Uri.parse(videoUrl)
        videoView.setMediaController(myMediaController)
        videoView.setVideoURI(videoUri)
        videoView.requestFocus()
        videoView.start()
    }
}
