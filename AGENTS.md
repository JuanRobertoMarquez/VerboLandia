# Reglas y Guía de Desarrollo para VerboLandia (AGENTS.md)

Este documento define la arquitectura, lineamientos de diseño de UI/UX, estándares de código, roles especializados y reglas obligatorias para el desarrollo y mantenimiento del proyecto **VerboLandia**. Cualquier agente o desarrollador que trabaje en este repositorio debe seguir estrictamente estas directrices.

---

## 1. Política de Cero Hardcoding (Strings, Colores y Dimensiones)

* **Textos de Usuario (`strings.xml`)**:
  * Queda estrictamente prohibido colocar texto visible en duro (*hardcodeado*) en archivos de layout XML (`android:text="..."`), clases de Kotlin (`"..."`) o diálogos.
  * Todo texto visible debe residir en `app/src/main/res/values/strings.xml`.
  * Utilizar formatos con argumentos dinámicos en lugar de concatenaciones manuales (`+` o `"${...}"`):
    ```xml
    <!-- Correcto -->
    <string name="triad_progress_format">Verbo %1$d de \%2$d</string>
    <string name="triad_accuracy_format">Aciertos: %1$d de %2$d (\%3$d%%)</string>
    ```
    ```kotlin
    // Correcto
    binding.tvProgress.text = getString(R.string.triad_progress_format, current, total)
    ```

* **Excepciones Permitidas al Hardcoding**:
  * **Sí usar** atributos del espacio `tools:` (`tools:text`, `tools:listitem`, `tools:visibility`) en layouts XML para previsualización en tiempo de diseño dentro de Android Studio.
  * **Sí usar** literales en código únicamente para nombres de columnas/tablas SQLite, claves de `SharedPreferences` y argumentos internos de `Bundle`.

* **Colores y Estilos Temáticos**:
  * No utilizar códigos hexadecimales en duro (`#FFFFFF`, `#000000`) en vistas ni en código.
  * Usar atributos de tema de Material 3 (`?attr/colorSurfaceContainerLow`, `?attr/colorPrimary`, `?attr/colorOnSurface`, `?attr/colorOutlineVariant`) para compatibilidad nativa con Modo Claro y Modo Oscuro.
  * Para colores de estado, referenciar la paleta centralizada en `res/values/colors.xml` (`@color/success`, `@color/error`, `@color/accent_gold`).

* **Dimensiones y Espaciados**:
  * Utilizar siempre las dimensiones estandarizadas de `res/values/dimens.xml` (`@dimen/margin`, `@dimen/spacing_md`, `@dimen/rounded_lg`, `@dimen/button_height`).

---

## 2. Política de Iconos (Sin Emojis)

* **Prohibición Total de Emojis**:
  * No utilizar emojis en la interfaz de usuario, títulos, botones, tarjetas, métricas, textos de feedback ni notificaciones (evitar `⏱️`, `🔥`, `⚡`, `💡`, `✔`, `❌`, `🏆`, `📈`, `🎱`, `📅`, `⭐` o flechas unicode como `➔`).
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
  * Aplicar tintes mediante `app:tint="?attr/..."` o `ColorStateList`. En contadores dinámicos, separar el icono en un `ImageView` o `app:drawableStartCompat` y dejar en `strings.xml` únicamente el formato numérico/textual.

---

## 3. Diseño Responsivo, Rendimiento XML y Ocupación de Pantalla

* **Eliminación de Espacios Muertos**:
  * Ninguna pantalla de ejercicio o actividad debe dejar espacios vacíos o bandas muertas en la parte inferior del dispositivo.
  * Los contenedores de interacción principal (rejillas de opciones, tarjetas de respuesta) deben estructurarse con `layout_height="0dp"` y restricciones verticales completas hacia el pie de la pantalla en `ConstraintLayout`.
  * Distribuir las filas y tarjetas de opciones mediante `layout_weight="1"` y `layout_height="match_parent"` para que se expandan ergonómicamente llenando el 100% de la altura disponible.

* **Prevención de Truncamiento y Legibilidad**:
  * **Anti-truncamiento**: En `ConstraintLayout`, configurar los `TextView` con texto dinámico usando `android:layout_width="0dp"` anclados entre sus restricciones `Start` y `End` para evitar cortes abruptos (`...`).
  * **Accesibilidad**: Tamaño mínimo de fuente de `12sp` en toda la aplicación y widgets.
  * **Cero Overdraw**: No declarar `android:background="?android:attr/colorBackground"` en la raíz de los layouts si el tema global ya pinta el fondo.
  * **Modularidad e Insets**: Evitar jerarquías anidadas profundas; extraer bloques repetitivos mediante `<include>` y gestionar *Window Insets* (`statusBars`) para que la barra de estado del sistema no se encime con los encabezados.

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
  * Todos los componentes deben leer y actualizar el progreso a través de `VerbRepository` y `UserDatabaseHelper`.

* **Integración de Herramientas de Google**:
  * **Sí usar Google TextToSpeech (TTS)**: Para toda pronunciación en inglés mediante `android.speech.tts.TextToSpeech` y `AudioSpeaker`.
  * **Sí usar Google Traductor Web**: Para consultas extendidas y contextuales a través de `GrammarWebActivity` mediante WebView directo (`https://translate.google.com/?sl=en&tl=es&text={verb}&op=translate`).
  * **No usar Google ML Kit On-Device Translation**: No añadir descargas offline de modelos de 30 MB. Toda la traducción esencial ya viene integrada y optimizada dentro de `verbs.db`.

---

## 6. Estándares de Arquitectura Kotlin y Ciclo de Vida

* **Arquitectura y ViewBinding**:
  * Arquitectura *Single Activity* (`MainActivity`) con Jetpack Navigation Component, vistas XML y `ViewBinding` exclusivo (prohibido `findViewById`).
  * Todo `Fragment` debe liberar la referencia de binding (`_binding = null`) dentro de `onDestroyView()` para evitar fugas de memoria (*memory leaks*).

* **Concurrencia y Corrutinas**:
  * Ejecutar todas las consultas a SQLite (`VerbRepository`, `DatabaseHelper`, `UserDatabaseHelper`) y cálculos pesados fuera del hilo principal usando `withContext(Dispatchers.IO)` y `viewLifecycleOwner.lifecycleScope`.
  * Prohibido usar `GlobalScope`.

* **Permisos, Notificaciones y Widgets**:
  * Solicitar y validar `POST_NOTIFICATIONS` (Android 13+) de forma segura sin provocar cierres inesperados.
  * Configurar `NotificationChannel` antes de disparar alertas y proteger todos los `PendingIntent` con `FLAG_IMMUTABLE`.
  * Declarar explícitamente `android:exported` en cada componente dentro de `AndroidManifest.xml`.

---

## 7. Roles de Agentes Especializados (Personas)

Cuando el usuario invoque un rol específico en el chat, el agente debe limitarse estrictamente al alcance de esa persona y **no modificar archivos ni pantallas fuera de la tarea solicitada**:

1. **Agente Auditor de Recursos (`@AuditorRecursos`)**:
   * **Alcance**: `strings.xml`, `colors.xml`, `dimens.xml`, `res/drawable/` y literales en `.kt`/`.xml`.
   * **Misión**: Erradicar textos, colores y medidas en duro (respetando las excepciones `tools:`), sustituir concatenaciones por placeholders (`%1$s`, `%1$d`) con `getString()`, y reemplazar cualquier emoji por iconos `VectorDrawable` (`ic_*.xml`).

2. **Agente Especialista UX/UI (`@EspecialistaUI`)**:
   * **Alcance**: `res/layout/*.xml`, `res/values/themes.xml`, `res/values-night/themes.xml` y componentes visuales.
   * **Misión**: Garantizar ocupación completa de pantalla (cero espacios muertos), responsividad, correcta aplicación de tokens de `dimens.xml`, compatibilidad con Modo Claro/Oscuro y ausencia total de textos cortados (`...`).

3. **Agente Arquitecto Kotlin (`@ArquitectoKotlin`)**:
   * **Alcance**: `Activities`, `Fragments`, `Adapters`, `ViewModels`, `VerbRepository` y `Helpers`.
   * **Misión**: Implementar la lógica de negocio, garantizar el manejo seguro de `ViewBinding` y corrutinas en `Dispatchers.IO`, mantener intacto el flujo dinámico de datos y respetar las reglas pedagógicas de VerboLandia.

4. **Agente QA y Estabilidad (`@AgenteQA`)**:
   * **Alcance**: Pruebas unitarias (`app/src/test/`), `AndroidManifest.xml`, notificaciones, widgets y diagnósticos de Gradle/Lint.
   * **Misión**: Detectar fugas de memoria, código obsoleto (*deprecated*), permisos mal gestionados y validar que la compilación y pruebas pasen sin errores ni advertencias críticas.

---

## 8. Higiene de Compilación y Control de Versiones

* **Variables de Entorno y SDK de Android**:
  * En este entorno, el JDK de Android Studio reside en:
    ```bash
    JAVA_HOME=/opt/android-studio/jbr
    ```
  * Antes de finalizar cualquier tarea o commit, compilar y ejecutar las pruebas unitarias para garantizar que el proyecto siempre quede ejecutable:
    ```bash
    JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug testDebugUnitTest
    ```

* **Git y Ramas**:
  * La rama de trabajo principal del desarrollador es `Gael`, la cual se fusiona periódicamente en `main`.
  * Mantener los commits limpios, semánticos y descriptivos (`feat:`, `fix:`, `refactor:`, `chore:`).