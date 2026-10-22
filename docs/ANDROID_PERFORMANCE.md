# Android .2: rendimiento, audio y pruebas en el móvil

Esta revisión conserva el package `org.mushroomrecompiled` y el certificado de
la primera APK. Instalarla sobre la anterior permite conservar la ROM recordada,
los ajustes y los cinco slots. No desinstalar para actualizar. La compatibilidad
de snapshots anteriores se comprobó en Linux; el flujo completo de actualización
y guardado Android todavía necesita pruebas en el dispositivo.

## Primera prueba

1. Abrir **Rendimiento y pruebas → Aplicar ajustes recomendados**. Selecciona 4:3,
   filtro nítido, sin scanlines, 48 kHz, estéreo y reserva de 24 ms. Se activa el
   pequeño contador de FPS; se puede ocultar desde ese mismo menú.
2. Usar primero el altavoz del móvil. Jugar en el mapa y 1-1 durante dos minutos.
   Probar caminar, mantener B y saltar con A. El vuelo requiere el power-up y las
   condiciones originales del juego.
3. Abrir **Rendimiento y pruebas → Probar botones táctiles**. Mantener A+B con dos
   dedos, añadir una diagonal, soltar un dedo y después todos. El indicador debe
   reflejar únicamente las entradas que siguen pulsadas. Probar el menú, Atrás,
   cambio de aplicación y vuelta al juego. La prueba pausa el núcleo y usa las
   mismas máscaras de entrada que la partida.
4. Repetir la misma escena en 16:9. Después probar pausa/reanudación, los cinco
   slots, cierre/reapertura y un mando físico. Si los controles quedaron en una
   posición incómoda, usar **Controles → Restaurar controles** o su editor.
5. Tras reproducir un fallo, abrir **Rendimiento y pruebas → Guardar informe
   .txt**. El selector de Android permite guardarlo donde se prefiera. El informe
   incluye versión, modelo, ajustes y hasta 90 mediciones recientes; no incluye
   ROM, URI privado, snapshots ni imágenes del juego. Anotar junto al informe la
   escena, la salida de audio utilizada y los pasos para repetir el problema.

Para una prueba prolongada, jugar al menos 30 minutos y registrar si el rendimiento
cambia al calentarse el teléfono. La revisión no declara 60 FPS estables en el
Galaxy A15 sin medirlo allí.

## Qué indican las mediciones

- **FPS juego**: cuadros completos que realmente ejecutó el núcleo. La cadencia
  NTSC original es aproximadamente 60,10 Hz.
- **FPS pantalla**: imágenes presentadas. Un panel a 60 Hz puede presentar menos
  imágenes que las ejecutadas por el núcleo; el hilo de vídeo toma la última.
- **CPU**: tiempo de trabajo del hilo del juego, incluida composición y entrega
  de audio; el máximo ayuda a localizar picos. El presupuesto por cuadro es unos
  16,64 ms. No es una medición del uso total del procesador.
- **Presentar/esperar**: incluye la espera de sincronización de pantalla. Esa
  espera ocurre en otro hilo y no detiene CPU, PPU ni APU.
- **Cola / reserva efectiva**: audio en el búfer de la aplicación. La latencia
  audible incluye además el dispositivo de salida; 24 ms de reserva no significa
  24 ms de latencia total. Si un backend negocia bloques mayores, la reserva
  efectiva se amplía para poder alimentarlos.
- **Faltas**: episodios en que el consumidor pidió más muestras de las disponibles.
  **Recortes**: limitaciones del audio acumulado para impedir una cola creciente.
  Comparar esos contadores antes y después de reproducir el fallo.

Si el audio tiene faltas con el núcleo por debajo del presupuesto, comparar 24 y
32/48 ms desde Audio. Comparar Bluetooth después de probar el altavoz. La
frecuencia y la velocidad del juego no se cambian para esconder faltas de audio.

## Cambios técnicos de esta revisión

- NDK: `-O3` y LTO para el runtime y el C recompilado, sin fast-math ni saltos de
  lógica. La PPU sigue siendo **LLE por ciclos**. La ruta de tramos estables usa
  los mismos registros de desplazamiento, accesos de bus y flancos de IRQ; las
  operaciones pendientes y los bordes de línea siguen la ruta general.
- El núcleo tiene un único hilo propietario, separado del renderer SDL/OpenGL ES.
  Se copia una imagen completa a un buzón fijo; una pantalla lenta puede omitir
  imágenes antiguas sin saltar cuadros de CPU/PPU/APU ni bloquear el audio.
- 4:3 evita capturas de CHR, OAM y capas panorámicas que solo necesita 16:9.
  Android solicita un modo de pantalla de 60 Hz y usa pistas de planificación
  de rendimiento cuando Android 12+ las ofrece.
- Audio SDL: prioridad AAudio, solicitud de modo de baja latencia y dos bursts
  nativos; alternativas OpenSL ES y AudioTrack. Callback con FIFO fijo, sin
  asignaciones, cola limitada y transiciones suaves al faltar/reiniciar audio.
  Reiniciar/cargar limpia PCM y filtros de salida anteriores, sin reiniciar los
  registros de la APU al cargar un snapshot.
- El acelerómetro deja de registrarse como joystick. Se conservan los mandos
  físicos SDL. Los dedos usan almacenamiento fijo por ID; un dedo entre botones
  superpuestos elige el más próximo, A+B sigue disponible con varios dedos y la
  cruceta conserva su captura al deslizar un poco fuera del borde visible.
- START/SELECT son botones alargados; A/B y cruceta muestran pulsación. Los menús
  comparten tipografía, colores, tarjetas y desplazamiento. El overlay deja de
  invalidarse periódicamente si sus controles no cambiaron.
- Scanlines se componen con una textura en una pasada, en lugar de 120 líneas
  enviadas por cuadro.

Las fuentes nuevas y los parches reproducibles de SDL están en el repositorio.
La ROM y el C privado generado siguen fuera del árbol público. 19:9 sigue
inhabilitado; los modos compatibles se mantienen en 4:3 y 16:9.

## Reproducir la medición del núcleo

```sh
cmake -S . -B build/performance -DCMAKE_BUILD_TYPE=Release \
  -DSMB3_OPTIMIZE_NATIVE=ON -DSMB3_LLE_RENDER_SPANS=ON
cmake --build build/performance --target smb3-benchmark smb3-ppu-span-verify --parallel 2
build/performance/smb3-ppu-span-verify "$SMB3_ROM_PATH"
build/performance/smb3-benchmark "$SMB3_ROM_PATH" 0
build/performance/smb3-benchmark "$SMB3_ROM_PATH" 1
```

El benchmark ejecuta el juego real sin limitador, calienta hasta 1-1 y mide 600
cuadros. Debe ejecutarse sin otras compilaciones o pruebas simultáneas. Un
resultado x86_64 sin renderer Android no certifica el rendimiento ARM64.
Los resultados ejecutados están en [ANDROID_TESTS.md](ANDROID_TESTS.md).
