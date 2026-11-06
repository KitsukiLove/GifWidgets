# DECISIONS (GifWidgets)

Resumen único de las decisiones del proyecto. El detalle por etapa está en `decisions/DECISIONS-etapa*.md`.

## Valores por defecto
Nombre GifWidgets · paquete `app.gifwidgets` · minSdk 26 · compileSdk/targetSdk 36 · AGP 9 (Kotlin integrado) · fps 12 · Píxel nítido activado · WebP sin pérdida · uso del 80 % del presupuesto de memoria · límites de importación 50 MB, 4096 px, 400 fotogramas y 6.291.456 píxeles · horario nocturno 23:00–07:00 desactivado · pausa con ahorro de batería activada · botón de pausa oculto por defecto · acción al tocar: alternar animación · conservar originales activado.

## Reglas que no se rompen
Sin permisos propios (ni INTERNET), sin servicios en primer plano, WakeLock, receptores de pantalla, WorkManager periódico, alarmas exactas ni `updatePeriodMillis` ≠ 0. El lanzador mueve la animación; la app nunca actualiza por fotograma. Sin Play services, análisis, anuncios, IA ni bibliotecas de imágenes de terceros. Lo no verificado se marca `VERIFY`.

## Arquitectura
- **Widget:** RemoteViews clásicos con `ViewFlipper` (no Glance), un solo intervalo; la animación se remuestrea a fps constante al guardar. Pausar = cambiar al layout quieto con un `updateAppWidget` completo. Esquinas, borde y fondo van dentro del bitmap.
- **Tamaño:** 3 proveedores (2×2, 4×2, 4×4) con ajustes por widget. La forma (vertical/horizontal) sigue la orientación actual. «Automático» rellena si recorta ≤ 45 %. Zoom 50–300 %.
- **Memoria:** presupuesto 6 × pantalla × 80 % (supuesto), reintentos al 60 % hasta un mínimo del 8 %; debounce de 500 ms al redimensionar.
- **GIF:** decodificador propio desde la especificación (sin código de terceros). Retardos 0 y 1 → 10 cs. Entrada no fiable: firma, límites y copia con tope.
- **Control:** `effective = master && actual && !(ahorro && pausar)`; de noche `actual = anulación ?: false`. Una alarma `setWindow(RTC, 15 min)`, sin `BOOT_COMPLETED` (se rearma desde `onUpdate`, `onEnabled`, abrir la app y cambiar ajustes).
- **Datos:** `filesDir` privado (`library/`, `widgets/<id>/`, `app_settings.json`); sin DataStore para no añadir dependencias. ZIP formato 1 con protección zip-slip y límites. Copia en la nube excluida; traspaso directo permitido.

## Etapa 4b, «añadir sin editar» y «Elegir GIF»
- **Añadir a pantalla de inicio:** menú de cada animación → tamaño → `requestPinAppWidget`. La animación elegida se guarda como asignación pendiente (`pending_assignment.txt`, caduca a los 5 min).
- **Vista previa del selector:** Android 15+: `setWidgetPreview` con el fotograma de reposo de la animación optimizada más reciente, solo cuando cambia (límite del sistema ≈ 2 llamadas/hora; reintento a los 10 min); `removeWidgetPreview` si se vacía la biblioteca. Android 12–14: `previewLayout`; anteriores: `previewImage`.
- **Elegir GIF** (en la configuración del widget): selector → importar → guardar optimizado con los valores del editor (fps del GIF limitado por Ajustes, Píxel nítido de Ajustes, ajuste automático a la memoria) → elegido para ese widget. Todo o nada.
- **Todo GIF importado queda optimizado** (botón de la biblioteca, «Compartir», «Elegir GIF»), así sirve como widget sin pasar por el editor; se puede re-editar desde el original (si «Conservar originales» está activado).
- **Widgets sin editar:** `widgetFeatures="reconfigurable|configuration_optional"` (Android 12+). Al ponerlo, `WidgetUpdater` le da la animación pendiente o la optimizada más reciente con el aspecto por defecto. Sin animaciones todavía, muestra un widget vacío cuyo toque abre su configuración (donde está «Elegir GIF»). En Android 11 o inferior sigue abriéndose la configuración al ponerlo.
- De WidgetsPro solo se tomó el **comportamiento** (elegir el GIF directamente en el widget). Ningún código copiado.

## Etapa de diseño — look KixKi Store
- **Alcance (lo que pidió el usuario):** solo la app por dentro. Estilo + paisaje + barra inferior, con los dos mundos de la página (Pixel y Colina XP) y un interruptor. El icono de la app y los botones del widget no se tocaron en esa etapa (sí en el Rediseño 2). Fuera de alcance en esa etapa: música, lluvia, minijuegos, estaciones, ovejita animada y sonidos. **Esto cambió en el «Rediseño 2»** (ver al final de este archivo y `traspaso/decisions/DECISIONS-diseno2.md`).
- **Paleta y fuentes:** las de la página (modo claro y oscuro). Fuentes Pixelify Sans (texto) y Jersey 10 (títulos), licencia SIL OFL, recortadas a caracteres latinos (≈27 KB cada una, ≈108 KB en total). Se quita el color dinámico (Material You): el look es el de la página.
- **Dos estilos, una paleta:** PIXEL = marcos planos con las esquinas «mordidas» y sombra dura que se hunde al pulsar; XP = ventanas redondeadas y barra inferior tipo barra de tareas degradada. Se elige con el botón «Estilo» de la barra inferior o en Ajustes (se guarda en `app_settings.json`, campo `style`).
- **Tema claro/oscuro:** el botón «Tema» de la barra inferior alterna entre claro y oscuro (como el de la página) y lo guarda; Ajustes sigue pudiendo volver a «Sistema».
- **Piezas** (`ui/design/` y `ui/theme/`): `PixelShape`, `GifDesign` (marco, sombra, barras de título), `Button`/`OutlinedButton`/`TextButton`/`Card`/`Switch` con los mismos nombres y parámetros que los de Material (cada pantalla solo cambió su `import`), `ScreenWindow` (ventana con barra de título «Ajustes.exe», «Editor.exe»…), `LandscapeHeader` (paisaje dibujado con Canvas, sin imágenes), `LogoBlock`, `GifBottomBar` (Inicio · Animaciones · Tema · Estilo · Ajustes) e iconos pixel de 12×12.
- **Barra inferior:** «Animaciones» es el interruptor universal (antes arriba, igual que el mosaico de Ajustes rápidos); «Ajustes» abre Ajustes y desde ahí Privacidad.
- **Batería y tamaño:** todo es estático (sin animaciones ni imágenes); el paisaje solo se redibuja cuando la pantalla se recompone. Sin permisos, servicios ni trabajo nuevo en segundo plano.
- **Segunda pasada (la biblioteca como la página):** las tarjetas son mini ventanas con barra de título («nombre del GIF» + botón «⋯» con las acciones) y los datos van en etiquetas rosas (tamaño, fotogramas, fps, peso, transparencia, WebP); buscador con lupa pixel; chips de filtro con contador (Todas · Transparencia · Optimizadas); «Biblioteca» con su contador y el orden como chip; un mosaico «+» con borde discontinuo (como una carpeta vacía) sustituye al botón flotante, que tapaba las tarjetas; fondo de franjas de cielo con tramado y estrellitas (Pixel) o degradado suave (XP); logo en tres capas como el de la página. La ovejita de la escena XP se dibuja ahora en píxeles enteros (antes salían rayas).
- **Arte:** la ovejita Kixki es de la propia página del usuario (sprite 18×10 copiado de su CSS); los iconos y el paisaje están dibujados para este proyecto.

## Etapa 8 — pantalla de bloqueo y calor
- **Pantalla de bloqueo:** `android:widgetCategory="home_screen|keyguard"` en los tres `widget_info*.xml` (verificado en la documentación oficial; los widgets de bloqueo en teléfonos llegaron con Android 16 QPR2, en una página aparte llamada «hub mode»). Las versiones anteriores ignoran `keyguard`. Sin código nuevo, sin permisos y sin trabajo en segundo plano. La vista previa generada (Android 15+) se publica también para la categoría `keyguard`.
- **Pausa por calor: NO implementada, a propósito.** El sistema solo avisa de la temperatura con un oyente (`addThermalStatusListener`) que exige un proceso vivo, y la regla del proyecto lo prohíbe. Una consulta «perezosa» (`getCurrentThermalStatus` al actualizar o tocar) solo pausaría cuando casualmente hubiera una actualización: no protegería de verdad y daría falsa seguridad. Se puede añadir esa versión parcial si se pide.

## Etapa 7 — calidad
- **StrictMode** solo en depuración (`GifWidgetsApp`): solo registra, no cierra la app.
- **R8 + reducción de recursos** en `release` (ver README). `proguard-rules.pro` sin reglas `-keep`: no hay reflexión propia ni serialización por biblioteca.
- **Objetivo APK < 5 MB:** se mide con «Analyze APK» (no medido por el agente).
- **Revisión del manifiesto:** permisos propios: ninguno (se quita el permiso interno de androidx.core). Componentes exportados: `MainActivity` (lanzador), `ImportActivity` (destino de Compartir, trata el archivo como no fiable), `WidgetConfigureActivity` (la inicia el lanzador) y los proveedores de widget; el receptor de acciones y el editor no están exportados. Los `PendingIntent` son explícitos; solo el de `requestPinAppWidget` es mutable (necesario para recibir el id del widget).
- **Documentos:** README y LICENSE (GPL-3.0) en la raíz; PRIVACY, BATTERY_TESTING, PRUEBAS, THIRD_PARTY y este archivo en `docs/`.

## Lista VERIFY final
| Punto | Estado |
|---|---|
| El widget anima fluido; `setFlipInterval` remoto | Confirmado por el usuario (etapa 4a: «funciona muy bien»); sin medición formal |
| Etapas 5 y 6 (control, ajustes, ZIP, privacidad) | Compilan y funcionan (confirmado por el usuario) |
| «Añadir a pantalla de inicio», vistas previas y «Elegir GIF» | El usuario confirmó que funciona; falta comprobar por separado la vista previa generada y que llegue el id en el callback (si no llega, la configuración aplica la animación igual) |
| `widgetFeatures="reconfigurable\|configuration_optional"` | Valor verificado en la documentación oficial de Android (12+). Falta confirmar en el Pixel 9 que al añadir no se abre la configuración |
| `setWidgetPreview` / `removeWidgetPreview` (firmas, límite de llamadas) | Verificadas en la documentación oficial (API 35) |
| El ViewFlipper se detiene con la pantalla apagada | **Pendiente: medir** (BATTERY_TESTING, prueba 4). Si falla, parar y avisar |
| Tope de memoria de bitmaps (6 × pantalla) y rechazo | Pendiente (hay reintentos; medir con la prueba 5) |
| `OPTION_APPWIDGET_SIZES` y aviso del launcher al girar | Pendiente |
| `onUpdate` tras reiniciar; `setWindow` RTC sin permiso | Pendiente |
| `POWER_SAVE_MODE_CHANGED` en manifiesto; `requestAddTileService` | Pendiente (mejor esfuerzo) |
| «Reproducir una vez» (alarma inexacta) | Experimental, pendiente |
| WebP lossless/lossy y alfa; `createScaledBitmap(filter=false)`; RGB_565 | Pendiente de revisión visual |
| `path="."` en las reglas de copia | Pendiente |
| Widgets en la pantalla de bloqueo (`keyguard`) | Categoría verificada en la documentación oficial. Falta confirmar en el Pixel 9 (Android 16 QPR2 o superior) que GifWidgets aparece en el selector de la pantalla de bloqueo y que el toque pide desbloquear |
| Look KixKi: márgenes bajo la barra de estado y de navegación (Android 15+), tamaños de texto de las fuentes pixel, el paisaje en pantallas anchas | Pendiente: revisión visual en el Pixel 9 |
| Compilación R8 de la versión `release` | Pendiente (hay que activarla en `build.gradle.kts`, ver README) |
| `PendingIntent` mutable del callback de pin; `previewImage` como `layer-list` | Pendiente |

## Revisión de robustez (octubre 2026)
- **Hilos auxiliares protegidos:** todo cuerpo de `Thread` de widgets pasa por `runGuarded` (`util/Guarded.kt`); una excepción suelta ya no mata la app.
- **Escritura atómica:** `AtomicFiles` usa un archivo temporal único por llamada (dos guardados a la vez del mismo archivo fallaban con `NoSuchFileException`).
- **Un widget, una actualización a la vez:** `WidgetUpdater` tiene un candado por id; los toques (alternar, siguiente, reproducir una vez) leen y escriben sus ajustes bajo ese candado. Al borrar un widget se espera a la actualización en curso (`forget`) y al abrir la app se limpian carpetas huérfanas (`cleanOrphans`, nunca si el sistema no informa de ningún widget).
- **Render solo de lo necesario:** quieto = solo el fotograma de reposo; animando = solo los fotogramas. La caché puede ser parcial y se escribe DESPUÉS de publicar el widget (así el codificado lossless no retrasa lo que se ve ni agota el tiempo del receiver).
- **Interruptor universal desde un widget:** vuelve a dibujar en el mismo hilo del receiver (`sync = true`) y alterna bajo un candado.
- **«Reproducir una vez»:** guarda `playUntilMs`; cuenta como «reproduciendo» solo mientras el reloj no lo pase, aunque la alarma se pierda (reinicio, forzar detención).
- **Alarma de noche:** se cancela al quitar el último widget (`onDisabled`) y se vuelve a armar con cambios de zona horaria u hora.
- **Configuración del widget:** `configChanges` para no perder lo editado al girar el móvil.
- **Editor:** avisa antes de salir con cambios sin guardar (título, «Atrás» y gesto); la vista previa se cancela cuando el deslizador ya se movió.
- **Importar ZIP:** se comprueban las dimensiones de cada `.webp` (máx. 4096 y 6,29 Mpx, como el límite de GIF) y que no pasen de 400 fotogramas; «sin espacio» ya no se muestra como «ZIP inválido»; los widgets vacíos se refrescan al terminar.
- **Borrar animación:** avisa cuántos widgets la usan.
- **Compilación:** R8 y reducción de recursos activados, solo es/en, sin bloque de metadatos de dependencias; `versionName` 0.1.0. Sin cambios de versiones de bibliotecas (no se pudieron comprobar sin red).
- **Sin cambiar a propósito:** WebP «sin pérdida» en Android 8–10 (la constante antigua es con pérdida; en Android 11+ no pasa); la pausa por ahorro de batería (se comprobó en el Pixel 9 que funciona).

## GIFs más fluidos y mejor encuadre (octubre 2026)
- **Encuadre AUTO:** `ScaleMath.AUTO_MAX_CROP` pasa de 0,45 a 0,10: se llena el widget solo si se recorta ≤ ~10 % del GIF; si no, se ve entero. Antes se recortaba casi la mitad y se agrandaba de más.
- **Tipo de GIF (`GifKind`):** ≤ 256 px de lado largo = pixel art (nítido, se quitan fotogramas antes que nitidez, como antes); mayor = foto/banner/grabación (suave, filtro de área, **se conservan todos los fotogramas** y se baja un poco la resolución).
- **`planSmoothFit`:** todos los fotogramas, tamaño lineal mínimo 50 % (`SMOOTH_MIN_SCALE`); solo si aun así no cabe se quitan fotogramas a ese tamaño mínimo. `WidgetPlanner.plan(..., smooth)` lo usa para los GIF grandes.
- **Configuración automática al colocar:** `WidgetSettings.forAnimation` decide «Píxel nítido» según el tipo (y el interruptor global de Ajustes); se usa al añadir sin configurar, al anclar desde la biblioteca, en la vista previa del selector y en la pantalla de configuración.
- **Importación («Elegir GIF» y biblioteca):** `QuickFit` elige velocidad (la del GIF, 5–20 fps), tamaño (≤ 960 px) y, si no cabe en el presupuesto de pantalla completa, baja resolución (suave) o fotogramas (pixel art). Ya no se usa `AutoFit.fit` ni el tope de 12 fps de Ajustes en este camino (Ajustes sigue mandando en el editor; por defecto ahora 20). `OptimizedWriter` usa 2–4 hilos según los núcleos.
- **Selección múltiple:** `PickMultipleVisualMedia`, cola secuencial (nunca en paralelo, por memoria), progreso «Importando 2/5…» y cada GIF aparece al terminar.
- **Redimensionar:** a los 150 ms una imagen fija al nuevo tamaño (1 fotograma) y a los 500 ms el render animado completo; los fotogramas se decodifican y componen en 2–4 hilos, en orden.
- **Sin medir:** los umbrales (256 px, 960 px, 50 %, 20 fps) son heurísticas; más fps cuestan algo más de CPU/batería del lanzador. Ajustar en `GifKind`, `QuickFit` y `WidgetMemoryCalculator.SMOOTH_MIN_SCALE` tras probar en el Pixel 9.

### Corrección tras probar en el móvil (GIF pesados lentos)
- **Caché del widget:** antes era WebP sin pérdida (esfuerzo 80) y se escribía con el candado del widget cogido: con 40+ fotogramas tardaba segundos y todo toque o cambio de tamaño esperaba. Ahora son píxeles en bruto (milisegundos); el formato lleva versión en la clave.
- **Fotos sin transparencia:** se guardan con pérdida (calidad 85) al importar; el codificado y decodificado es varias veces más rápido y ocupan menos. Pixel art y GIF con transparencia siguen sin pérdida.
- **Tamaño del bitmap del widget:** `WidgetRenderer.bitmapSize` no gasta píxeles en agrandar (un GIF de 480 px en un widget de 1000 px se dibuja a ~640 px y el lanzador lo escala). Con memoria limitada, eso son más fotogramas. No se aplica al pixel art con «Píxel nítido».
- **Suavidad:** el límite para bajar resolución es 240 px de lado largo (`SMOOTH_MIN_LONG_SIDE`) en vez de «la mitad»; fps máximo 15 (antes 20).
- **Componer fotogramas:** ya no se crea una capa extra por fotograma si no hay esquinas redondeadas.

## Modo «Máxima calidad» (octubre 2026)
- **Qué es:** interruptor POR WIDGET (pantalla de configuración). Un servicio en primer plano (`HighQualityService`, tipo `specialUse`) reproduce el GIF fotograma a fotograma: cada uno se dibuja al tamaño completo del widget y se envía con `partiallyUpdateAppWidget`. No depende del límite de ~12 MB de bitmaps de RemoteViews (todos los fotogramas, resolución original), como hacen otras apps de GIF.
- **Coste aceptado por la usuaria:** permisos `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` y `POST_NOTIFICATIONS` (este último opcional; sin él el servicio funciona y la notificación queda oculta), notificación fija mientras reproduce y más batería. Sigue sin `INTERNET`. La pantalla de privacidad lista los permisos declarados.
- **Ahorro dentro de lo posible:** el servicio solo existe mientras algún widget en este modo esté animando (pausa, noche, ahorro de batería, interruptor universal y «reproducir una vez» pasan por `WidgetUpdater.update`, que llama a `halt`/`requestSync`); se detiene del todo si no queda ninguno y se pausa con la pantalla apagada. Máx. ~30 fps (`MIN_FRAME_MS`).
- **Fotogramas:** `HighQualityFrames` rehace desde `original.gif` un segundo juego `frames_hq/` con las ediciones del usuario (recorte, fondo, orden) a la velocidad del GIF (máx. 30 fps) y hasta 1280 px, sin ajuste de memoria (`HighQualityParams`). Se genera al activar el interruptor (con progreso) o, si falta o está desactualizado, al empezar a reproducir. Sin el original se usan los fotogramas normales (se mueve a su velocidad, sin mejorar calidad).
- **Imagen fija:** el widget en este modo, pausado o mientras lo reproduce el servicio, muestra UNA imagen a tamaño completo (sin ajuste de memoria).
- **Límites de Android:** desde Android 12 no se puede iniciar un servicio en primer plano desde segundo plano (p. ej. al reiniciar el móvil): `HighQualityService.start` devuelve false y el widget usa el modo normal hasta que se abra la app o se toque el widget. Sin probar en dispositivo: ritmo real de fotogramas, consumo y comportamiento del lanzador del Pixel 9 son lo primero que hay que medir.

### Encuadre AUTO graduado (banners)
- `ScaleMath.autoScale`: si llenar el widget corta ≤ 35 % (`AUTO_MAX_CROP`) → llena; si cortaría más, la imagen se agranda solo hasta cortar exactamente 35 % (escala = ver entera / 0,65). Es continuo: ya no hay salto entre «entera pequeña con barras» y «muy recortada». Antes: todo o nada con 45 % y luego 10 %, que dejaba los banners chicos con barras.
- La clave de la caché del widget sube a `v3` para que los widgets ya puestos se redibujen con este encuadre.

## Rediseño 2 — más fiel a la página (estaciones, música, sonidos, icono)
Pedido del usuario: «se ve básico / poco vivo» y «no se parece lo bastante a la web». Respuestas: rehacerlo más fiel; añadir **estaciones y fondos** y **música y sonidos**; rehacer **icono y botones del widget**.
- **Regla de batería intacta (salvo el modo «Máxima calidad», opcional y por widget, ver su sección):** sin servicios en primer plano, sin WakeLock, sin trabajo periódico. La música (`audio/KixkiAudio.kt`) es un `AudioTrack` en un hilo que **solo existe mientras alguna pantalla de la app está visible** (`GifWidgetsApp` cuenta las actividades iniciadas); al salir, el hilo termina y el `AudioTrack` se libera. Pide el foco de audio y se calla si otra app lo toma. Todo se sintetiza en el teléfono (sin archivos de audio, sin red).
- **Valores por defecto:** música **apagada**, sonidos **encendidos** (suaves, con el mismo límite que la página: 90 ms entre sonidos, máx. 6 cada 2,5 s, y callan si otra app está reproduciendo música), estación **Automática** (por fecha), fondo animado **encendido**.
- **Fondo animado** (`ui/design/Scenes.kt`): el reloj de la escena (`AppTheme`) late ~8 veces por segundo **solo** con la pantalla iniciada, el ajuste activado, ahorro de batería apagado y animaciones del sistema ≠ 0; si no, la escena queda quieta y no corre nada.
- **Estaciones** (`control/SeasonLogic.kt`, paletas generadas de `scenes.js` de la página): Clásico, Valle, Primavera, Verano, Otoño, Invierno, Halloween, Navidad + Automática (reglas de fechas de la página).
- **Ajustes nuevos en `AppSettings`** (al final, con valor por defecto; los archivos viejos siguen cargando): `season`, `musicOn`, `soundOn`, `animatedBackground`.
- **Ovejita Kixki** animada (respira, parpadea, salta y hace «beee» al tocarla), logo pixel con ola, botones del banner (tema, música, estación).
- **Icono** adaptable (`mipmap-anydpi-v26/ic_launcher.xml`, ovejita sobre el cielo lila) con versión monocromática (Android 13+). **Botones del widget**: `ic_widget_play` / `ic_widget_pause` ahora son botones pixel con el símbolo incluido.
- **Controles:** `Slider` propio (cuadrado, con marcas). Casillas, chips, pestañas y diálogos siguen siendo los de Material con los colores nuevos.
- `ui/design/Landscape.kt` quedó vacío (se puede borrar); su contenido pasó a `Hero.kt` y `Wordmark.kt`.
- Pruebas nuevas: `SeasonLogicTest`, `SceneryAndSoundSettingsTest`, `audio/SynthTest`.
- **VERIFY (no se pudo compilar en el entorno del agente):** todo el código Compose (Scenes, Hero, Wordmark, Slider, Settings, Main) y `KixkiAudio` (AudioTrack, foco de audio); el brillo/volumen de la música en el Pixel 9; que `ic_widget_play/pause` encajen con `widget_static.xml` (no estaba en el ZIP); rendimiento del fondo animado en pantallas de alta frecuencia; consumo de batería con el fondo animado (medir con BATTERY_TESTING).


## Integración del Rediseño 2 con los arreglos (octubre 2026)
- El rediseño se fusionó con el código ya corregido mediante fusión de tres vías (original → rediseño, sobre la versión actual): pantallas, ajustes, textos es/en y recursos del rediseño; se conservan todos los arreglos de robustez, el encuadre AUTO graduado, la importación múltiple, la caché en bruto y el modo «Máxima calidad».
- En `MainActivity` el botón flotante de importar desaparece (el rediseño lo sustituye por la barra inferior y el botón de la biblioteca vacía); el texto «Importando 2/5…» se muestra en esos botones.
- Los controles `Slider` de Editor y Configuración usan el del rediseño (`ui.design.Slider`, mismos parámetros).
- Las reglas de «solo mientras la app está visible» del audio no cambian; el único servicio en primer plano sigue siendo el de «Máxima calidad».

### Kixki, el personaje real (octubre 2026)
- El diseño dibujaba una ovejita distinta (18 × 10, cara naranja, pezuñas verdes: la «ovejita de abajo» de la página). Kixki de verdad es el sprite de 20 × 15 de `kixki.js` (lana crema, carita pequeña color durazno, estrella arriba, bufanda del color principal del tema, pezuñas oscuras). `SheepSprite` ahora es ese sprite; la bufanda es morada de día y dorada de noche, como en la página (`--pri`).
- El icono del lanzador (`ic_kixki_foreground`, `ic_kixki_monochrome`) usa el mismo sprite. El parpadeo pinta los ojos con el color de la cara.

## Rediseño 3 — «al grano» y más cerca de la página (octubre 2026)
Pedido: acercar la app a la página (tarjetas, botones, ventanas .exe, fondo, editor/configuración, letra), ir al grano sin banner, y que Kixki quede discreta con consejos fáciles.
- **Sin banner:** la biblioteca empieza con una barra fina (logo pequeño + botones de tema, música y estación; avisan con un mensaje corto) y sigue con la franja «Consejo», el buscador y las ventanas de GIF. `Hero.kt` ahora solo tiene `KixkiMini` (el sprite quieto) y `ToolButton`; `HeroScene`, la ovejita que salta/bala y sus frases (`kixki_phrases`) se eliminaron. Los fondos y estaciones (Valle y demás), la música, los sonidos y los dos estilos siguen igual.
- **Kixki discreta:** una ventana de bienvenida **una sola vez** (`KixkiWelcome`, ajuste `welcomeSeen`) y una franja «Consejo» (`TipStrip`) con un consejo que rota solo cada 14 s **solo con la pantalla visible**, no reacciona a toques y se cierra con su X (ajuste `tipsOn`; Ajustes → «Consejos de Kixki» la vuelve a encender). Aparece en biblioteca, editor y configuración del widget. Los consejos están en `arrays.xml` (es y en): `tips_library`, `tips_editor`, `tips_widget`.
- **Ventana de cada GIF:** sigue siendo una ventana .exe, ahora con borde de 4 dp, sombra solo hacia abajo (como las carpetas de la página), color pastel según el GIF (`cardTint`) y botón de cerrar (X) que **pregunta antes de borrar** con el mismo diálogo de «Borrar» (incluye el aviso de widgets en uso).
- **Letra como la página:** cuerpo 16–17 sp, títulos de ventana 20, encabezados 26, botones y etiquetas en negrita. Todo en sp: respeta el tamaño de letra del sistema.
- **Ajustes nuevos en `AppSettings`** (al final, con valor por defecto; los archivos viejos siguen cargando): `welcomeSeen` (clave `welcomeSeen`) y `tipsOn` (clave `tips`). Prueba nueva: `TipsSettingsTest`.
- Sin permisos nuevos, sin red, sin trabajo en segundo plano.
- **VERIFY (no se pudo compilar en el entorno del agente):** todo el código Compose nuevo (`Tips.kt`, `Hero.kt`, `Kit.kt`, `Windows.kt`, `MainActivity`, Editor y Configuración); que la franja no tape contenido en pantallas pequeñas o con letra grande; que el diálogo de bienvenida se vea bien en claro/oscuro y en Pixel/XP.

## Rediseño 4 — Kixki paseante, etiquetas, configuración con muchos GIF, memoria compartida (octubre 2026)
Pedido: una Kixki que se mueve entre las ventanas como en la página pero discreta y que da un consejo al tocarla; ventanas de GIF con menos etiquetas (y sin «transparencia» falsa); con muchos GIF, no poder encontrarlos en la configuración del widget; y el menú de mantener pulsado un widget puesto que no abre.
- **Kixki paseante** (`KixkiWalker`, `ui/design/Tips.kt`): en la biblioteca camina despacio sobre el borde superior de las ventanas visibles y de vez en cuando salta a otra; se queda quieta entre paseos. Al tocarla suena su «beee» y sale una burbuja con un consejo (`tips_library`); la burbuja se cierra sola a los 7 s. **Solo se mueve con la pantalla visible** y solo si el fondo animado está activo y no hay ahorro de batería ni animaciones del sistema apagadas (si no, queda quieta). No intercepta toques fuera de ella: la lista sigue deslizándose. La franja «Consejo» se eliminó. En el editor y en la configuración del widget queda quieta en la barra de título (`KixkiCorner`, `ScreenWindow(actions = …)`); al tocarla sale un consejo (`tips_editor`, `tips_widget`). Se apaga en Ajustes → «Kixki y sus consejos» (`tipsOn`).
- **Etiquetas de la ventana:** solo «fotogramas» y «fps» y, si de verdad la tiene, «Transparente». El peso, el tamaño y «Optimizado» se quitan de la tarjeta.
- **Transparencia real:** `hasAlpha` copiaba la bandera «color transparente» del GIF, que muchos GIF opacos traen puesta. Ahora `OptimizedWriter` mide los píxeles guardados (`AlphaScan`) y la biblioteca vieja se corrige una sola vez al abrir la app (`AlphaCheck`, campo `alphaChecked` de `meta.json`; las entradas que dicen «sí» se reexaminan, las demás se marcan sin leer nada).
- **Configuración del widget con muchos GIF:** la vista previa y la línea de memoria quedan **fijas arriba** (150 dp) y las opciones se desplazan debajo; la lista de botones de radio se sustituyó por una **cuadrícula de miniaturas** (3 columnas, alto máximo con desplazamiento propio) con **buscador** cuando hay más de 6. Lo mismo para la lista del modo «siguiente animación». El hueco enorme de arriba venía de contar dos veces el margen de la barra de estado. La vista previa es una imagen fija del aspecto del widget (no se anima).
- **Widget puesto que no abre el menú al mantener pulsado:** no pude reproducirlo ni verlo en el móvil. Lo más probable es el launcher saturado: cada widget animado le deja en memoria hasta el 80 % del tope de la plataforma, y con muchos widgets suma demasiado. Ahora los widgets animados **comparten** un total (`WidgetShare`: ~16 pantallas entre todos; con 1–2 widgets no cambia nada, con 5 cada uno usa ~53 %, con 10 ~26 %), y el nivel guardado solo se actualiza si el sistema rechazó la actualización. El toque del widget (`setOnClickPendingIntent` en la raíz) es el normal y no impide el menú del launcher. VERIFY en el móvil.
- **VERIFY (no se pudo compilar):** `Tips.kt` (posiciones sobre la cuadrícula, salto, burbuja), `WidgetConfigureActivity` (estructura y `GifGrid` dentro de un `verticalScroll`), `WidgetUpdater` (reparto de memoria), `AlphaCheck`. Pruebas nuevas: `AlphaScanTest`, `WidgetShareTest`.

## Rediseño 5 — Kixki libre, barra inferior, pausa clara, «En uso», selección, «Widgets» (noviembre 2026)
- **Kixki ya no depende del fondo animado.** Se quedaba quieta para quien tiene el ahorro de batería (o «quitar animaciones») porque compartía su regla. Ahora anda siempre que los consejos estén activos y la pantalla visible; sus pasos y saltos van con el reloj de fotogramas (`withFrameNanos`), no con las animaciones de Compose, así que la escala de animaciones del sistema tampoco la congela. El fondo sigue quieto con ahorro de batería (regla anterior).
- **Barra inferior:** se quitó «Inicio». Ahora: Widgets (abre `MyWidgetsActivity`), Pausar todo / Reanudar todo (el texto dice lo que hace al pulsarlo y se resalta mientras todo está en pausa), Estilo, Ajustes.
- **Aviso de pausa** (`PauseBanner`, regla pura `GlobalPauseLogic`): cuando los widgets están quietos por el interruptor general, el ahorro de batería o el horario de noche, la biblioteca lo dice y ofrece un toque: «Reanudar todo», «Mover igualmente» (apaga «pausar con ahorro de batería») o «Ajustes». La opción «Pausar con ahorro de batería» sigue activada por defecto (batería primero).
- **«En uso»:** etiqueta en las ventanas y filtro que sustituye a «Optimizadas» (casi todo está optimizado ya). Lo calcula `WidgetUpdater.usedAnimationIds` al refrescar.
- **Seleccionar:** chip «Seleccionar» → casillas en las ventanas, «Todo», «Borrar (n)» y «Duplicar (n)». El borrado múltiple avisa de cuántos están en uso.
- **Compartir varios GIF** desde otra app (`ACTION_SEND_MULTIPLE`, máximo 20, de uno en uno).
- **Pantalla «Widgets»** (`MyWidgetsActivity`): lista de widgets puestos con miniatura del GIF, estado (se mueve, quieto, en pausa por…, máxima calidad) y «Configurar».
- **Memoria compartida corregida:** el nivel que el sistema fuerza se guarda junto con el reparto con el que se logró (`usage.txt` = `nivel,reparto`, `UsageFile`) y solo vale con el mismo reparto; antes un nivel bajo forzado con muchos widgets se quedaba para siempre. La cuenta de widgets animados se guarda 3 s (`animatedIds`) en lugar de leer todos los ajustes por cada widget. Ajustes muestra cuántos widgets animados hay y el tope de cada uno.
- **Widget vacío:** al borrar el GIF de un widget (o no tener ninguno) se muestra `layout/widget_empty.xml` (tarjeta lila con marco violeta, Kixki y «Toca para elegir un GIF», con colores de noche en `values-night`), en lugar del dibujo de pruebas `widget_test_frame`, que ya no se usa y se puede borrar.
- **Biblioteca vacía:** Kixki aparece también ahí (toca para un consejo).
- **Vista previa de la configuración animada:** un bucle corto (máx. 16 fotogramas, 360 px) dibujado con el aspecto actual, reconstruido 350 ms después del último cambio y solo mientras la pantalla está visible.
- Pruebas nuevas: `GlobalPauseTest`, `UsageFileTest`.
- **VERIFY (no se pudo compilar):** `MyWidgetsActivity`, el modo seleccionar y el banner en `MainActivity`, `Tips.kt` (reloj de fotogramas), `WidgetConfigureActivity` (bucle de vista previa), `ImportActivity` (varios), el nuevo `widget_empty` en el launcher.

## Rediseño 6 — widget vacío limpio, nombre al añadir, secciones, favoritos y rotación (noviembre 2026)
- **Widget sin GIF** (se borró su animación o aún no hay): solo Kixki y «Toca aquí para añadir GIF», sin tarjeta ni marco (`widget_empty.xml`); tocarlo abre la configuración. `bg_widget_empty.xml` y los colores `widget_empty_*` quedaron sin uso (se pueden borrar).
- **Pregunta de nombre antes de añadir** (biblioteca y «Elegir GIF» del widget): se propone el nombre del archivo ordenado (`AnimationNames.suggest`: sin «.gif», `_`/`-` a espacios, sin colas numéricas largas). Con varios GIF se pregunta uno a uno, con «Añadir el resto así». Compartir desde otra app no pregunta (usa la sugerencia). `GifQuickAdd.add(nameOverride=)`.
- **Controles propios** en `ui/design/Choices.kt`: `GifDialog` (ventana con barra de título), `GifChip`, `GifTabRow`/`GifTab`, `GifCheckbox`, `GifRadio`, `NameField`, `CollapsibleSection`. Mismos parámetros que los de Material pero con nombre propio (con el mismo nombre chocaban con los de Material al compilar). Los menús desplegables (`DropdownMenu`) siguen siendo de Material.
- **Secciones plegables** en la configuración del widget (GIF, Reproducción, Encuadre, Marco y fondo, Colores, Cambio de GIF, Botón y toque) con «Restablecer» por sección (`WidgetSettings.resetX`), y en Ajustes. El estado abierto/cerrado se guarda al girar la pantalla.
- **Editor simple / Avanzado**: por defecto fondo + velocidad + tamaño (y «Ajustar automáticamente» si pasa de memoria); «Avanzado» devuelve las 4 pestañas.
- **Tamaño real** de la vista previa (diálogo, fondo claro u oscuro) y **Copiar estilo** de otro widget (`withLookFrom`: encuadre, marco, colores y botón; no toca GIF, toque ni lista).
- **Favoritos y colecciones**: `AnimationMeta.favorite` y `collection` (una colección por GIF, texto libre ≤ 24). Los favoritos salen primero con cualquier orden (orden estable). Filtros «Favoritos» y una pastilla por colección. En el modo «Seleccionar» se puede mover varios a una colección.
- **Modo lista compacta** de la biblioteca (`AppSettings.compactList`): una fila por GIF con imagen fija (sin animar: menos batería); Kixki no pasea en ese modo.
- **Rotación automática** (`autoRotateMinutes`, `rotatedAtMs`): UN solo `setWindow(RTC, …)` inexacto y sin despertar (como `NightAlarm`), puesto para la hora más cercana de todos los widgets; al sonar (`RotationAlarm.rotateDue`) mueve a cada widget que ya toca y vuelve a programarse. Sin widgets que roten no hay alarma. Mínimo 5 min entre despertares. Se reprograma al abrir la app, al guardar un widget, tras reinicio (onUpdate) y al cambiar la hora. Pura: `AutoRotate` (probada).
- **Recordatorio de respaldo** (`BackupNudge`): con ≥ 3 GIF y ≥ 30 días desde el último respaldo (o desde el GIF más antiguo si nunca hizo uno); «Más tarde» lo oculta 7 días. Se anota la fecha al exportar bien.
- **Logo** de la barra superior crece con el tamaño de letra del sistema (máx. +20 %).
- No se pudo compilar ni ejecutar en el entorno del agente: revisar con `./gradlew testDebugUnitTest` y la lista de `PRUEBAS.md`.

## Limpieza y orden del proyecto (noviembre 2026)
- Borrado: `cambios.diff`, `ControlState-fix/`, `app/src/main.zip`, `app/local.properties`, los tests de plantilla de Android Studio (`com/app/gifwidgets/Example*Test.kt`, paquete equivocado y sin dependencias de `androidTest`) y las carpetas de compilación/IDE (`build/`, `.gradle/`, `.kotlin/`, `.idea/`): Android Studio las regenera.
- Los documentos pasaron a `docs/` (en la raíz quedan `README.md` y `LICENSE`).
- **Controles únicos:** `Dialogs.kt` y los chips/casillas/botones de opción de `Inputs.kt` repetían los de `Choices.kt` (`GifDialog`, `GifChip`, `GifCheckbox`, `GifRadio`, `GifTab`/`GifTabRow`) y chocaban al compilar. Quedó un solo juego en `Choices.kt`; `Inputs.kt` conserva solo las barras de progreso temáticas, que ahora usan el editor, la configuración del widget y la pantalla de importar (antes usaban las de Material).
- Sin tocar (a propósito, borrado «seguro» solamente): código y recursos sin uso (`Landscape.kt`, `LogoBlock`, `bg_widget_empty.xml`, `widget_test_frame.xml`, colores `widget_empty_*` y varias cadenas); lista en la revisión.
