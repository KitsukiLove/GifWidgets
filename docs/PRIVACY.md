# Privacidad de GifWidgets

**Resumen:** la app no envía nada a ningún sitio. No puede: no tiene permiso de internet.

## Permisos
Solo los del modo opcional «Máxima calidad» de un widget: servicio en primer plano (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`) y notificaciones (`POST_NOTIFICATIONS`, opcional). Ninguno da acceso a internet ni a tus datos; sin ese modo no hacen nada. Para comprobarlo: en Android Studio, `app/src/main/AndroidManifest.xml` → pestaña **Merged Manifest**, o en la propia app, **Ajustes → Privacidad**.

## Tus archivos
- Un GIF entra solo cuando tú lo eliges con el selector del sistema o lo compartes desde otra app. La app recibe únicamente ese archivo, con acceso temporal que concede el sistema. No lee tus fotos ni tu galería.
- GIF, fotogramas y ajustes se guardan en el almacenamiento **privado** de la app. Ninguna otra app puede leerlos. Al desinstalar la app se borran.
- **Exportar** crea un ZIP donde tú decides guardarlo (selector del sistema). **Importar** lee solo el ZIP que tú eliges, y lo valida antes de tocar tu biblioteca.

## Pantalla de bloqueo
Si añades un widget a la página de widgets de la pantalla de bloqueo (Android 16 QPR2 o superior), su animación se verá **sin desbloquear el móvil**: cualquiera que lo tenga delante podrá verla. Elige con cuidado qué GIF pones ahí.

## Copias de seguridad
Las reglas de la app excluyen la copia en la nube de Google. La biblioteca solo viaja a un móvil nuevo por el traspaso directo entre dispositivos (Android 12 o superior). Para una copia propia usa **Exportar** en Ajustes.

## Segundo plano
Sin procesos permanentes (salvo el servicio del modo «Máxima calidad» mientras un widget en ese modo está animando, con su notificación). Solo trabajos cortos al tocar un widget, al cambiar su tamaño, al añadirlo, y —si activas el horario nocturno— una alarma inexacta que no despierta el móvil. El lanzador es quien mueve la animación.

## Sonido
La música y los sonidos (opcionales) se crean en tu teléfono; no hay archivos de audio ni red. La música solo suena mientras la app está abierta y se detiene al salir. La app no usa el micrófono.

## Componentes
Bibliotecas de Android y Jetpack, Kotlin y corrutinas (ver `THIRD_PARTY.md`). El decodificador de GIF y la conversión a WebP son propios. Sin Google Play services, sin análisis, sin anuncios, sin modelos de IA.

## Contacto
Proyecto personal sin servidor ni cuentas: no hay datos tuyos que pedir, corregir o borrar fuera de tu móvil.
