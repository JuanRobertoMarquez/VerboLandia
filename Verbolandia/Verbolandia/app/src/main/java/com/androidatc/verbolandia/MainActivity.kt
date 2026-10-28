package com.androidatc.verbolandia

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.androidatc.verbolandia.databinding.ActivityMainBinding

/**
 * Pantalla principal "Explorador Verbal" (Diccionario) de Verbolandia.
 */
class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding
    private lateinit var verbAdapter: VerbAdapter

    // Conjunto inicial de datos de los 5 verbos del diseño
    private val sampleVerbs = listOf(
        Verb(
            id = 1,
            base = "break",
            phonetic = "/breɪk/",
            spanishMeaning = "Romper, quebrar",
            pastSimple = "broke",
            pastParticiple = "broken",
            level = "B1",
            isIrregular = true,
            masteryPercentage = 85
        ),
        Verb(
            id = 2,
            base = "choose",
            phonetic = "/tʃuːz/",
            spanishMeaning = "Elegir, escoger",
            pastSimple = "chose",
            pastParticiple = "chosen",
            level = "A2",
            isIrregular = true,
            masteryPercentage = 40,
            isFrequentReview = true
        ),
        Verb(
            id = 3,
            base = "develop",
            phonetic = "/dɪˈvel.əp/",
            spanishMeaning = "Desarrollar, evolucionar",
            pastSimple = "developed",
            pastParticiple = "developed",
            level = "B2",
            isIrregular = false,
            masteryPercentage = 100
        ),
        Verb(
            id = 4,
            base = "speak",
            phonetic = "/spiːk/",
            spanishMeaning = "Hablar, conversar formalmente",
            pastSimple = "spoke",
            pastParticiple = "spoken",
            level = "A1",
            isIrregular = true,
            masteryPercentage = 78
        ),
        Verb(
            id = 5,
            base = "achieve",
            phonetic = "/əˈtʃiːv/",
            spanishMeaning = "Lograr, conseguir, alcanzar",
            pastSimple = "achieved",
            pastParticiple = "achieved",
            level = "B1",
            isIrregular = false,
            masteryPercentage = 60
        )
    )

    private var currentFilteredList = sampleVerbs.toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inflado con ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajuste de barras de sistema (Edge-to-Edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.nestedScrollView.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )
            binding.bottomNavigation.setPadding(
                0,
                0,
                0,
                systemBars.bottom
            )
            insets
        }

        setupRecyclerView()
        setupBottomNavigation()
        setupSearchAndFilters()
        setupVerbOfDayAudio()

        // Carga inicial
        updateVerbList(sampleVerbs)
    }

    private fun setupRecyclerView() {
        verbAdapter = VerbAdapter(
            onVerbClick = { verb ->
                Toast.makeText(
                    this,
                    "Detalle de: ${verb.base} (${verb.pastSimple} / ${verb.pastParticiple})",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onAudioClick = { verb ->
                Toast.makeText(
                    this,
                    "🔊 Pronunciación de '${verb.base}' (${verb.phonetic})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        binding.rvVerbs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = verbAdapter
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_dictionary

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dictionary -> true
                R.id.nav_verb -> {
                    Toast.makeText(this, "Sección: Verbo", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_practice -> {
                    Toast.makeText(this, "Sección: Práctica", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_progress -> {
                    Toast.makeText(this, "Sección: Progreso", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearchAndFilters() {
        // Búsqueda en tiempo real
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterVerbs(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Selección de Filtros Rápidos (Todos, Irregulares, Regulares)
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val query = binding.etSearch.text.toString()
            filterVerbs(query)
        }
    }

    private fun setupVerbOfDayAudio() {
        binding.btnAudioVerbOfDay.setOnClickListener {
            Toast.makeText(
                this,
                "🔊 Pronunciación del Verbo del Día: 'freeze' (/friːz/)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun filterVerbs(query: String) {
        val selectedChipId = binding.chipGroupFilters.checkedChipId

        val filtered = sampleVerbs.filter { verb ->
            val matchesQuery = verb.base.contains(query, ignoreCase = true) ||
                    verb.spanishMeaning.contains(query, ignoreCase = true) ||
                    verb.pastSimple.contains(query, ignoreCase = true)

            val matchesType = when (selectedChipId) {
                R.id.chipIrregular -> verb.isIrregular
                R.id.chipRegular -> !verb.isIrregular
                else -> true
            }

            matchesQuery && matchesType
        }

        updateVerbList(filtered)
    }

    private fun updateVerbList(list: List<Verb>) {
        currentFilteredList = list
        verbAdapter.submitList(list)
        binding.tvVisibleCount.text = getString(R.string.visible_results, list.size)
    }
}