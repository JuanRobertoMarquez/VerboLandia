package com.example.verbolandia.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.verbolandia.R
import com.example.verbolandia.databinding.IncludeAppBarBinding

/**
 * Define el tipo de elemento para el Slot Izquierdo (shrink-0):
 * - LOGO: Logo de la app para tabs raíz.
 * - BACK: Botón volver con flecha atrás.
 * - CLOSE: Botón cerrar con cruz (xmark).
 * - NONE: Sin elemento.
 */
sealed class LeftSlot {
    object Logo : LeftSlot()
    data class Back(val onClick: () -> Unit) : LeftSlot()
    data class Close(val onClick: () -> Unit) : LeftSlot()
    object None : LeftSlot()
}

/**
 * Define las acciones y badges para el Slot Derecho (flex items-center gap-2 shrink-0):
 * - Avatar: Avatar del usuario de 36dp (para pantallas raíz).
 * - Bookmark: Botón de guardar (para detalle).
 * - Audio: Botón de audio con relleno primario (para detalle).
 * - PointsBadge: Badge de gamificación con puntos/racha y rayo (para minijuegos/práctica).
 * - Search: Botón de búsqueda opcional.
 */
sealed class RightAction {
    data class Avatar(val onClick: (() -> Unit)? = null) : RightAction()
    data class Bookmark(val isBookmarked: Boolean = false, val onClick: () -> Unit) : RightAction()
    data class Audio(val onClick: () -> Unit) : RightAction()
    data class PointsBadge(val text: String) : RightAction()
    data class Search(val onClick: () -> Unit) : RightAction()
}

/**
 * Gestor del Encabezado (Header) Global de Verbolandia.
 * Proporciona la función `configure` para renderizar de forma modular y dinámica
 * los slots de cada pantalla, evitando hardcoding de elementos.
 */
object AppBarHelper {

    fun configure(
        binding: IncludeAppBarBinding,
        left: LeftSlot = LeftSlot.Logo,
        eyebrow: String = "VERBOLANDIA",
        title: String,
        rightActions: List<RightAction> = emptyList(),
        progress: Int? = null,
        isDarkTheme: Boolean = false
    ) {
        val context = binding.root.context

        // 0. Tema de fondo y divisor
        if (isDarkTheme) {
            binding.root.setBackgroundColor(Color.parseColor("#062213"))
            binding.tvEyebrow.setTextColor(ContextCompat.getColor(context, R.color.secondary_accent))
            binding.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
            binding.bottomDivider.setBackgroundColor(Color.parseColor("#1B432E"))
        } else {
            binding.root.setBackgroundColor(ContextCompat.getColor(context, R.color.background))
            binding.tvEyebrow.setTextColor(ContextCompat.getColor(context, R.color.primary))
            binding.tvTitle.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            binding.bottomDivider.setBackgroundColor(ContextCompat.getColor(context, R.color.outline_variant))
        }

        // 1. Barra de progreso superior
        if (progress != null) {
            binding.progressLine.visibility = View.VISIBLE
            binding.progressLine.progress = progress
        } else {
            binding.progressLine.visibility = View.GONE
        }

        // 2. Slot Izquierdo (shrink-0): Muestra solo un elemento
        when (left) {
            is LeftSlot.Logo -> {
                binding.slotLeftButton.visibility = View.VISIBLE
                binding.slotLeftButton.setImageResource(R.drawable.ic_dictionary)
                binding.slotLeftButton.setBackgroundResource(android.R.color.transparent)
                binding.slotLeftButton.setColorFilter(
                    if (isDarkTheme) Color.WHITE else ContextCompat.getColor(context, R.color.primary)
                )
                binding.slotLeftButton.isClickable = false
                binding.slotLeftButton.setOnClickListener(null)
            }
            is LeftSlot.Back -> {
                binding.slotLeftButton.visibility = View.VISIBLE
                binding.slotLeftButton.setImageResource(R.drawable.ic_arrow_back)
                binding.slotLeftButton.setBackgroundResource(R.drawable.bg_icon_button_soft)
                binding.slotLeftButton.setColorFilter(
                    if (isDarkTheme) Color.WHITE else ContextCompat.getColor(context, R.color.on_surface)
                )
                binding.slotLeftButton.isClickable = true
                binding.slotLeftButton.setOnClickListener { left.onClick() }
            }
            is LeftSlot.Close -> {
                binding.slotLeftButton.visibility = View.VISIBLE
                binding.slotLeftButton.setImageResource(R.drawable.ic_close)
                binding.slotLeftButton.setBackgroundResource(R.drawable.bg_icon_button_soft)
                binding.slotLeftButton.setColorFilter(
                    if (isDarkTheme) Color.WHITE else ContextCompat.getColor(context, R.color.on_surface)
                )
                binding.slotLeftButton.isClickable = true
                binding.slotLeftButton.setOnClickListener { left.onClick() }
            }
            is LeftSlot.None -> {
                binding.slotLeftButton.visibility = View.GONE
            }
        }

        // 3. Slot Central (flex-1 min-w-0): 2 líneas perfectamente alineadas
        binding.tvEyebrow.text = eyebrow.uppercase()
        binding.tvTitle.text = title

        // 4. Slot Derecho (flex items-center gap-2 shrink-0): Renderiza ÚNICAMENTE lo solicitado
        binding.slotRight.removeAllViews()

        if (rightActions.isEmpty()) {
            binding.slotRight.visibility = View.GONE
        } else {
            binding.slotRight.visibility = View.VISIBLE
            val density = context.resources.displayMetrics.density
            val dp36 = (36 * density).toInt()
            val dp8 = (8 * density).toInt()
            val marginEnd = (8 * density).toInt()

            rightActions.forEachIndexed { index, action ->
                val isLast = index == rightActions.size - 1
                when (action) {
                    is RightAction.Avatar -> {
                        val avatarView = ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                                if (!isLast) this.marginEnd = marginEnd
                            }
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageResource(R.drawable.ic_avatar_placeholder)
                            contentDescription = context.getString(R.string.cd_avatar)
                            if (action.onClick != null) {
                                isClickable = true
                                val typedValue = android.util.TypedValue()
                                if (context.theme.resolveAttribute(androidx.appcompat.R.attr.selectableItemBackgroundBorderless, typedValue, true)) {
                                    setBackgroundResource(typedValue.resourceId)
                                }
                                setOnClickListener { action.onClick.invoke() }
                            }
                        }
                        binding.slotRight.addView(avatarView)
                    }
                    is RightAction.Bookmark -> {
                        val bookmarkBtn = ImageButton(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                                if (!isLast) this.marginEnd = marginEnd
                            }
                            setBackgroundResource(R.drawable.bg_icon_button_soft)
                            setImageResource(R.drawable.ic_bookmark)
                            setPadding(dp8, dp8, dp8, dp8)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = context.getString(R.string.cd_bookmark)
                            setColorFilter(
                                if (action.isBookmarked) ContextCompat.getColor(context, R.color.primary)
                                else ContextCompat.getColor(context, R.color.on_surface_variant)
                            )
                            setOnClickListener { action.onClick() }
                        }
                        binding.slotRight.addView(bookmarkBtn)
                    }
                    is RightAction.Audio -> {
                        val audioBtn = ImageButton(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                                if (!isLast) this.marginEnd = marginEnd
                            }
                            setBackgroundResource(R.drawable.bg_icon_button_primary)
                            setImageResource(R.drawable.ic_volume)
                            setPadding(dp8, dp8, dp8, dp8)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = context.getString(R.string.cd_play_audio)
                            setColorFilter(ContextCompat.getColor(context, R.color.on_primary))
                            setOnClickListener { action.onClick() }
                        }
                        binding.slotRight.addView(audioBtn)
                    }
                    is RightAction.PointsBadge -> {
                        val badgeContainer = LinearLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (!isLast) this.marginEnd = marginEnd
                            }
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setBackgroundResource(R.drawable.bg_badge_small)
                            val padH = (12 * density).toInt()
                            val padV = (4 * density).toInt()
                            setPadding(padH, padV, padH, padV)

                            val iconView = ImageView(context).apply {
                                layoutParams = LinearLayout.LayoutParams((16 * density).toInt(), (16 * density).toInt())
                                setImageResource(R.drawable.ic_flash)
                                setColorFilter(ContextCompat.getColor(context, R.color.primary))
                                contentDescription = null
                            }
                            val textView = TextView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    marginStart = (4 * density).toInt()
                                }
                                text = action.text
                                setTextColor(ContextCompat.getColor(context, R.color.primary))
                                textSize = 12f
                                typeface = Typeface.DEFAULT_BOLD
                            }
                            addView(iconView)
                            addView(textView)
                        }
                        binding.slotRight.addView(badgeContainer)
                    }
                    is RightAction.Search -> {
                        val searchBtn = ImageButton(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp36, dp36).apply {
                                if (!isLast) this.marginEnd = marginEnd
                            }
                            setBackgroundResource(R.drawable.bg_icon_button_soft)
                            setImageResource(R.drawable.ic_search)
                            setPadding(dp8, dp8, dp8, dp8)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = context.getString(R.string.cd_search)
                            setColorFilter(ContextCompat.getColor(context, R.color.on_surface_variant))
                            setOnClickListener { action.onClick() }
                        }
                        binding.slotRight.addView(searchBtn)
                    }
                }
            }
        }
    }
}
