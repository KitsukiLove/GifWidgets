# GifWidgets

App Android privada y local que convierte tus GIF en **widgets animados** para la pantalla de inicio con el mínimo gasto de batería. Hecha para un Google Pixel 9; funciona desde Android 8 (API 26).

Sin internet, sin cuentas, sin anuncios, sin análisis, sin IA. Todo vive en el almacenamiento privado de la app. Solo pide permisos (servicio en primer plano y notificaciones) para el modo opcional «Máxima calidad» de un widget; si no lo usas, no hacen nada.

## Qué hace
- **Añadir un GIF y listo:** al importar un GIF (botón de la biblioteca, «Compartir» desde otra app o «Elegir GIF» en un widget) la app lo guarda ya **optimizado** (fotogramas WebP) con valores por defecto. No hace falta abrir el editor.
- **Widgets sin editar:** en Android 12 o superior, al poner un widget no se abre ninguna pantalla: recibe la animación más reciente (o la que elegiste con «Añadir a pantalla de inicio»). Puedes cambiar lo que quieras después (mantén pulsado el widget → configurar). En versiones anteriores se abre la configuración al ponerlo.
- **Tres tamaños** de widget (2×2, 4×2, 4×4), todos redimensionables. Ajustes por widget: escala (automática, ajustar, rellenar, centrar), zoom, esquinas, borde, fondo, brillo, opacidad, velocidad, botón de pausa opcional y qué hace el toque.
- **Editor (opcional):** quitar fondo sin IA (color clave o negro → transparente), recortar y reordenar fotogramas, cambiar velocidad y tamaño, con medidor de memoria y ajuste automático. Siempre se re-edita desde el original, sin acumular pérdida.
- **Máxima calidad (opcional, por widget):** reproduce el GIF fotograma a fotograma a su resolución y velocidad desde un servicio en primer plano (con notificación), sin el límite de memoria de los widgets. Gasta más batería; se pausa con la pantalla apagada, de noche, con ahorro de batería o al pausar.
- **Control de batería:** interruptor universal (también en Ajustes rápidos), pausa con el ahorro de batería, horario nocturno con una sola alarma inexacta.
- **Copia de seguridad:** Exportar / Importar la biblioteca en un ZIP (Ajustes).
- Vista previa en el selector de widgets (Android 15 o superior muestra tu animación más reciente).
- **Look KixKi Store:** la app tiene el aspecto de tu página: paleta lila, fuentes pixel, ventanas «.exe», paisaje de fondo, una Kixki discreta que da consejos y barra inferior. Dos estilos con interruptor (**Pixel** y **Colina XP**) y modo claro u oscuro, desde la barra inferior o en Ajustes.
- **Pantalla de bloqueo:** los widgets también se ofrecen en la página de widgets de la pantalla de bloqueo (Android 16 QPR2 o superior). Actívala en Ajustes → Pantalla y tacto → Pantalla de bloqueo → «Widgets en la pantalla de bloqueo», desliza de derecha a izquierda en la pantalla de bloqueo y mantén pulsado para añadir uno.

## Cómo compilar
1. Abre la carpeta del proyecto con Android Studio (Windows, Mac o Linux).
2. Deja que sincronice Gradle y pulsa **Run ▶** con el móvil conectado (depuración USB).
3. Para instalar la versión final (más pequeña y rápida), mira «Versión final (release)».

## Versión final (release): R8 y recursos
`app/build.gradle.kts` ya trae el release con R8, recursos reducidos, solo español/inglés y firma de depuración (solo uso personal):
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("debug")
    }
}
```
Después: **Build → Generate App Bundles or APKs → Generate APKs**, o en terminal `gradlew assembleRelease`. El APK queda en `app/build/outputs/apk/release/`. Objetivo: **menos de 5 MB**. Para verlo: **Build → Analyze APK…**. Prueba la biblioteca, el editor, un widget y Ajustes en esa versión antes de darla por buena.

## Pruebas
- Lógica pura (control, ajustes del widget, ZIP, GIF, edición…): `./gradlew testDebugUnitTest` (o `gradlew.bat testDebugUnitTest` en Windows).
- Lista de cosas para probar a mano en el teléfono: `docs/PRUEBAS.md`.
- Medición de batería en el móvil: ver `docs/BATTERY_TESTING.md`.

## Estructura
Un solo módulo `app`; código en `app/src/main/kotlin/app/gifwidgets/` (`gif/`, `library/`, `edit/`, `memory/`, `widget/`, `control/`, `backup/`, `audio/`, `ui/`). Documentos en `docs/`. Textos en `values/` (español) y `values-en/` (inglés).

## Documentos
En `docs/`: `PRIVACY.md` · `BATTERY_TESTING.md` · `PRUEBAS.md` · `DECISIONS.md` (decisiones y lista VERIFY) · `THIRD_PARTY.md`. En la raíz: `LICENSE`.

## Licencia
GNU General Public License v3.0 (ver `LICENSE`). Compatible con F-Droid.

## Fondo, estaciones, música y sonidos
En Ajustes → «Fondo, música y sonidos» eliges la estación (o Automática, por fecha), activas el fondo animado, la música y los sonidos. En la pantalla principal, los tres botones de la barra superior cambian tema, música y estación. Kixki te saluda una sola vez y después deja consejos fáciles en una franja «Consejo» (se cierra con su X y se reactiva en Ajustes). La música es opcional (apagada al inicio), se crea en tu teléfono y solo suena con la app abierta.

