# Reglas y Guía de Desarrollo para VerboLandia (AGENTS.md)

Este documento define la arquitectura, lineamientos de diseño de UI/UX, estándares de código y reglas obligatorias para el desarrollo y mantenimiento del proyecto **VerboLandia**. Cualquier agente o desarrollador que trabaje en este repositorio debe seguir estrictamente estas directrices.

---

## 1. Política de Cero Hardcoding (Strings, Colores y Dimensiones)

* **Textos de Usuario (`strings.xml`)**:
  * Queda estrictamente prohibido colocar texto visible en duro (*hardcodeado*) en archivos de layout XML (`android:text="..."`), clases de Kotlin (`"..."`) o diálogos.
  * Todo texto debe residir en `res/values/strings.xml`.
  * Utilizar formatos con argumentos dinámicos en lugar de concatenaciones manuales:
    ```xml
    <!-- Correcto -->
    <string name="triad_progress_format">Verbo %1$d de %2$d</string>
    <string name="triad_accuracy_format">Aciertos: %1$d de %2$d (%3$d%%)</string>
    ```
    ```kotlin
    // Correcto
    binding.tvProgress.text = getString(R.string.triad_progress_format, current, total)
    ```

* **Colores y Estilos Temáticos**:
  * No utilizar códigos hexadecimales en duro (`#FFFFFF`, `#000000`) en vistas ni en código.
  * Usar atributos de tema de Material 3 (`?attr/colorSurfaceContainerLow`, `?attr/colorPrimary`, `?attr/colorOnSurface`, `?attr/colorOutlineVariant`) para compatibilidad nativa con Modo Claro y Modo Oscuro.
  * Para colores de estado, referenciar la paleta centralizada en `res/values/colors.xml` (`@color/success`, `@color/error`, `@color/accent_gold`).

* **Dimensiones y Espaciados**:
  * Utilizar siempre las dimensiones estandarizadas de `res/values/dimens.xml` (`@dimen/margin`, `@dimen/spacing_md`, `@dimen/rounded_lg`, `@dimen/button_height`).

---

## 2. Política de Iconos (Sin Emojis)

* **Prohibición Total de Emojis**:
  * No utilizar emojis en la interfaz de usuario, títulos, botones, tarjetas, métricas, textos de feedback o notificaciones (evitar `⏱️`, `🔥`, `⚡`, `💡`, `✔`, `❌`, `🏆`, `📈`).
  * Los emojis causan inconsistencias visuales según la versión de Android y rompen la estética profesional de la aplicación.

* **Uso Exclusivo de Iconos Vectoriales (`VectorDrawable`)**:
  * Todas las representaciones visuales deben utilizar drawables vectoriales XML ubicados en `res/drawable/` (`ic_*.xml`).
  * Iconos estándar del proyecto:
    * Cronómetro / Tiempo: `@drawable/ic_timer`
    * Racha / Fuego: `@drawable/ic_fire`
    * Combo / Energía: `@drawable/ic_flash`
    * Logro / Trofeo: `@drawable/ic_medal`
    * Consejos mnemotécnicos: `@drawable/ic_lightbulb`
    * Éxito y confirmación: `@drawable/ic_check`
    * Advertencias y errores: `@drawable/ic_warning`
    * Audio: `@drawable/ic_volume`
    * Compartir: `@drawable/ic_share`
  * Aplicar tintes mediante `app:tint="?attr/..."` o `ColorStateList`.

---

## 3. Diseño Responsivo y Ocupación Completa de Pantalla

* **Eliminación de Espacios Muertos**:
  * Ninguna pantalla de ejercicio o actividad debe dejar espacios vacíos o bandas muertas en la parte inferior del dispositivo.
  * Los contenedores de interacción principal (rejillas de opciones, tarjetas de respuesta) deben estructurarse con `layout_height="0dp"` y restricciones verticales completas hacia el pie de la pantalla en `ConstraintLayout`.
  * Distribuir las filas y tarjetas de opciones mediante `layout_weight="1"` y `layout_height="match_parent"` para que se expandan ergonómicamente llenando el 100% de la altura disponible.

---

## 4. Filosofía Pedagógica y Gamificación

* **Regla de Un Solo Intento con Revelación Inmediata**:
  * En actividades evaluativas (como *Tríada Rápida*), el usuario tiene **un solo intento** por ranura/verbo.
  * Si el usuario comete un error:
    1. Deshabilitar inmediatamente todas las tarjetas de opciones para evitar intentos ciegos repetitivos.
    2. Resaltar la tarjeta pulsada con error (`R.color.error`).
    3. Resaltar la tarjeta con la opción correcta en verde (`R.color.success`).
    4. Revelar en la matriz de la tríada ambas formas faltantes con su fonética IPA para asegurar el aprendizaje activo.
    5. Reiniciar la racha (`streak = 0`) y mostrar la tarjeta de retroalimentación pedagógica con el motivo del fallo y la regla mnemotécnica.
    6. Pronunciar la tríada completa mediante TextToSpeech (TTS).

* **Dificultad Adaptativa Impulsada por Racha Viva**:
  * El catálogo debe iniciar y mantenerse por defecto con selección aleatoria del grupo base accesible (niveles A1 y A2).
  * Únicamente cuando el usuario sostenga una racha activa larga ($\ge 3$ aciertos consecutivos), el sistema debe escalar dinámicamente a verbos irregulares de mayor dificultad (B1, B2 o C1).
  * Si la racha se interrumpe, el sistema debe descender con suavidad al nivel base para afianzar la confianza del estudiante.

* **Distractores Inteligentes No Repetitivos**:
  * Generar siempre 4 opciones distintas por paso (evitar duplicados de formas idénticas como *hit/hit* o formas redundantes).
  * Incluir trampas morfológicas verosímiles: regularizaciones en `-ed` para irregulares (`chosed`, `goed`), trampas en `-en` para regulares, gerundios contextuales y formas reales muestreadas del catálogo global.

---

## 5. Base de Datos Relacional y Herramientas Externas

* **Base de Datos SQLite (`verbs.db`) como Única Fuente de la Verdad**:
  * La base de datos relacional v2 (`user_version = 3`) contiene el catálogo completo de 185 verbos (A-Z) con:
    * Formas base, pasado, participio y gerundio.
    * Transcripciones fonéticas y notación IPA (`ipa_base`, `ipa_past`, `ipa_participle`).
    * Significados oficiales en español en cada registro.
    * Tablas relacionales para 12 tiempos verbales (`verb_tenses`) y phrasal verbs (`phrasal_verbs`).
  * Todos los componentes deben leer y actualizar el progreso a través del `VerbRepository`.

* **Integración de Herramientas de Google**:
  * **Sí usar Google TextToSpeech (TTS)**: Para toda pronunciación en inglés mediante `android.speech.tts.TextToSpeech` y `AudioSpeaker`.
  * **Sí usar Google Traductor Web**: Para consultas extendidas y contextuales a través de `GrammarWebActivity` mediante WebView directo (`https://translate.google.com/?sl=en&tl=es&text={verb}&op=translate`).
  * **No usar Google ML Kit On-Device Translation**: No añadir descargas offline de modelos de 30 MB. Toda la traducción esencial ya viene integrada y optimizada dentro de `verbs.db`.

---

## 6. Higiene de Compilación y Control de Versiones

* **Variables de Entorno y SDK de Android**:
  * En este entorno, el JDK de Android Studio reside en:
    ```bash
    JAVA_HOME=/opt/android-studio/jbr
    ```
  * Antes de finalizar cualquier tarea o commit, compilar y ejecutar las pruebas unitarias:
    ```bash
    JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest
    ```
* **Git y Ramas**:
  * La rama de trabajo principal del desarrollador es `Gael`, la cual se fusiona periódicamente en `main`.
  * Mantener los commits limpios, semánticos y descriptivos (`feat:`, `fix:`, `refactor:`, `chore:`).
