# Medición de batería (etapa 7)

> Los comandos de abajo son los habituales de `adb`, pero **hay que probarlos en tu móvil**: pueden variar entre versiones de Android. Paquete de la app: `app.gifwidgets`.

**Idea:** la app no debe gastar nada mientras el widget se mueve, porque lo mueve el lanzador (ViewFlipper) y la app no está viva. Estas pruebas lo comprueban.

## Antes de empezar
1. En el Pixel: Ajustes → Acerca del teléfono → toca 7 veces «Número de compilación»; luego Opciones de desarrollador → **Depuración USB**.
2. En el PC, abre una terminal en `%LOCALAPPDATA%\Android\Sdk\platform-tools` y comprueba con `adb devices` (acepta la huella en el móvil).
3. Instala la versión **release** (ver README) y pon **un** widget con una animación mediana (unos 30–60 fotogramas). Abre la app una vez y ciérrala (desliza fuera de recientes).

## Prueba 1 — La app no está viva con la pantalla apagada
1. Apaga la pantalla con el botón y espera 1 minuto.
2. Ejecuta: `adb shell pidof app.gifwidgets`
3. **Esperado:** no imprime nada (no hay proceso). Si imprime un número, anótalo y repite tras 5 minutos.

## Prueba 2 — Sin bloqueos de CPU ni alarmas raras
1. `adb shell dumpsys batterystats --reset`
2. Usa el móvil con normalidad 20–30 minutos (con el widget a la vista).
3. `adb shell dumpsys batterystats app.gifwidgets > bateria.txt` y abre `bateria.txt`.
4. **Esperado:** sin *wakelocks* de `app.gifwidgets`, sin trabajos periódicos, sin servicios. Alarmas: `adb shell "dumpsys alarm | grep gifwidgets"` solo puede mostrar **una** alarma inexacta, y solo si activaste el horario nocturno.

## Prueba 3 — Widget animado frente a pausado (20 minutos cada uno)
Para que el consumo sea comparable: mismo brillo, misma red, móvil sin cargar, pantalla de inicio siempre encendida.
1. Carga la pantalla de inicio con el widget **animado**. Pon el móvil sin cargar y deja la pantalla encendida 20 minutos. Anota el % de batería al empezar y al terminar.
2. Pausa todo con el interruptor «Animaciones» de la app. Repite 20 minutos.
3. Rellena la tabla:

| Caso | Batería inicio | Batería final | Gasto |
|---|---|---|---|
| Animado (20 min) | | | |
| Pausado (20 min) | | | |

La diferencia debe ser pequeña. Una pantalla encendida gasta mucho más que cualquier widget, por eso se compara contra el mismo caso pausado, no contra nada.

## Prueba 4 — Pantalla apagada con el widget animado (30 minutos)
1. Widget animado, pantalla encendida; anota el % de batería y mira Ajustes → Batería → Uso de la batería (app y launcher).
2. `adb shell dumpsys batterystats --reset`, apaga la pantalla y déjala 30 minutos.
3. Mira otra vez Uso de la batería y `adb shell dumpsys batterystats app.gifwidgets`.
4. **Esperado:** el gasto atribuido a `app.gifwidgets` y al launcher **no sube**.
5. **Si sube o la animación parece seguir corriendo con la pantalla apagada: PARA y cuéntamelo. No se arregla añadiendo servicios ni receptores de pantalla** (están prohibidos en este proyecto).

## Prueba 5 — Memoria con el máximo de fotogramas
1. En Android Studio abre **View → Tool Windows → Profiler** y arranca la app (versión de depuración).
2. Pon un widget con una animación de 300–400 fotogramas, cámbiale el tamaño varias veces y gira la pantalla.
3. **Esperado:** sin errores de memoria ni cierres; la memoria baja al terminar. Si el sistema rechaza una actualización, la app reintenta con menos píxeles (sale en la configuración como «reducido»).

## Extra — StrictMode (versión debug)
En Logcat, filtra por `StrictMode`. Cada aviso es lectura o escritura de disco en el hilo principal. Los de archivos muy pequeños (por ejemplo, ajustes) son aceptables; anota los demás.

## Qué anotar y enviarme
Resultado de cada prueba (OK / no OK), la tabla de la prueba 3 y el contenido de `bateria.txt` si algo no cuadra.
