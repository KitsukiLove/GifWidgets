# Pruebas manuales (lista corta, en el teléfono)

Las pruebas unitarias (`./gradlew testDebugUnitTest`) cubren la lógica pura. Esto es lo que solo se ve en el teléfono.

## Widget vacío
- [ ] Pon un widget, bórrale el GIF desde la biblioteca: se ve solo Kixki + «Toca aquí para añadir GIF», sin fondo.
- [ ] Tócalo: abre la configuración.

## Añadir GIF
- [ ] Biblioteca → Añadir → un GIF: pregunta el nombre con la sugerencia; «Añadir» lo importa con ese nombre.
- [ ] Elegir 3 GIF: pregunta 1 de 3; «Añadir el resto así» importa los demás; «Cancelar» no añade ninguno.
- [ ] Compartir un GIF desde otra app: se añade sin preguntar.
- [ ] Configuración del widget → «Elegir GIF»: pregunta el nombre y lo elige para ese widget.

## Pantallas
- [ ] Diálogos (renombrar, borrar, anclar), chips, pestañas, casillas y botones de opción con el estilo de la app en claro/oscuro y Pixel/XP.
- [ ] Ajustes y configuración del widget: secciones que se abren/cierran y recuerdan el estado al girar.
- [ ] «Restablecer» de cada sección solo cambia esa sección.
- [ ] Editor: modo simple por defecto; «Avanzado» muestra las pestañas.

## Widget
- [ ] «Tamaño real» muestra el widget del tamaño que ocupará (fondo claro/oscuro).
- [ ] «Copiar estilo» copia el marco/colores de otro widget y no cambia el GIF.
- [ ] Rotación automática: 2+ GIF en la lista y «cada 15 min»; tras ~15–25 min con la pantalla encendida cambia solo. Con la pantalla apagada puede esperar a que la enciendas (es lo esperado: no despierta el teléfono).

## Biblioteca
- [ ] Favorito: sube al principio; filtro «Favoritos».
- [ ] Mover a colección: aparece la pastilla; elegirla filtra; moviendo el último GIF desaparece y no deja la lista vacía.
- [ ] Modo «Lista»/«Tarjetas» se recuerda al reabrir.
- [ ] Respaldo: con ≥ 3 GIF y respaldo viejo aparece el aviso; «Más tarde» lo oculta; exportar lo quita.
