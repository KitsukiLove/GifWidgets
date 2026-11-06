# THIRD_PARTY

No se incluye código de terceros copiado en el repositorio.

## Código propio basado en especificaciones
- Decodificador GIF (`app/gifwidgets/gif/GifReader.kt`): escrito desde la especificación pública GIF89a. **No** está copiado ni derivado del gist de Xcellent Creations (licencia MIT), por eso no hay aviso MIT.
- Conversión a WebP: usa `Bitmap.compress` de la plataforma Android.

## Referencias estudiadas, sin copiar código
- WidgetsPro (app de código abierto): se estudió qué hace su widget GIF (elegir el GIF al crear el widget). **No se copió código ni recursos**; su motor usa un servicio en primer plano y permisos que este proyecto prohíbe. La función «Elegir GIF» está escrita desde cero sobre el motor propio.

## Bibliotecas (por Gradle, licencia Apache License 2.0)
- AndroidX: Core, Activity (Compose), Lifecycle, Annotation.
- Jetpack Compose: UI, Foundation, Runtime, Material 3.
- Kotlin (biblioteca estándar) y kotlinx.coroutines.
Versiones exactas en `gradle/libs.versions.toml`.

## Solo para pruebas (no van en el APK)
- JUnit 4 (Eclipse Public License 1.0).
- `tools/*.java`: mini ejecutor de pruebas propio.

## Fuentes (van en el APK, en `app/src/main/res/font/`)
- **Pixelify Sans** — Copyright 2021 The Pixelify Sans Project Authors (<https://github.com/eifetx/Pixelify-Sans>). SIL Open Font License 1.1.
- **Jersey 10** — Copyright 2023 The Soft Type Project Authors (<https://github.com/scfried/soft-type-jersey>). SIL Open Font License 1.1.
- Son los archivos de la página KixKi Store del usuario, recortados a caracteres latinos (herramienta fontTools). La OFL permite incluirlas y modificarlas siempre que se conserve el aviso de copyright y la licencia, y que no se vendan solas. **Antes de publicar la app, añade el texto completo de la OFL 1.1** (<https://openfontlicense.org>) en este repositorio.

## Recursos gráficos
- La ovejita Kixki (sprite pixel) es arte del propio usuario (página KixKi Store).
- Paisaje, iconos pixel de la barra inferior y logo: dibujados para este proyecto con `Canvas`; sin imágenes.
- Iconos del widget y del mosaico: formas vectoriales simples (▶, ⏸) del proyecto.
- Icono del lanzador: la ovejita Kixki (arte del usuario) sobre un cielo pixel dibujado para este proyecto (vectores propios en `drawable/ic_kixki_*.xml`).
- Fondos animados por estación, ovejita animada y logo pixel: dibujados con `Canvas`, basados en `scenes.js` de la página KixKi Store del usuario (paletas y composiciones).
- Música y sonidos: sintetizados en el teléfono con código propio (`audio/`), portado del motor `music.js` de la página KixKi Store del usuario. Sin archivos de audio ni bibliotecas.

El texto de la licencia Apache 2.0 está en <https://www.apache.org/licenses/LICENSE-2.0>.
