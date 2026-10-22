# Verificación del port Android

Fecha: 22 de octubre de 2026. Entorno: Linux x86_64, GCC 14, JDK 17, SDK 36,
NDK 28.2.13676358/Clang, CMake y Gradle 8.13. La ROM privada se validó con la
identidad autorizada en [ANDROID.md](ANDROID.md). No se modificó.

**No había un dispositivo Android conectado a este entorno.** `adb devices` devolvió una lista
vacía. Los resultados del núcleo en Linux no certifican la aplicación Android,
el Galaxy A15, un modelo de mando ni reproducción audible sin cortes.

## Revisión actual: `1.5.0-android.2`

El usuario informó tirones incluso en 4:3 con el audio silenciado en la primera
APK. Esta revisión separa el hilo del núcleo de EGL/vsync, optimiza la PPU LLE,
renueva audio y controles y añade diagnóstico exportable. La guía de uso para
las pruebas está en [ANDROID_PERFORMANCE.md](ANDROID_PERFORMANCE.md).

| Comprobación nueva | Resultado ejecutado |
|---|---|
| Regresiones del núcleo, vídeo y audio | CTest 9/9; 483,54 s, incluidos los guiones originales de runtime, widescreen y escenas 4/6 |
| PPU LLE: ruta general frente a tramos estables | Coinciden 1.800 hashes por modo en 4:3 y 16:9; cada hash cubre hardware, memoria, framebuffer completo y PCM |
| Frontend nativo real en Linux, SDL headless | Dos sesiones dentro del mismo proceso: el núcleo avanza durante un bloqueo de presentación de 600 ms; pausa, cinco slots, cargar, reiniciar, cambio de formato y cierre pasaron |
| Buzón entre hilos | Productor de 2.000 imágenes y consumidor lento: último cuadro completo, dimensiones coherentes, sin píxeles mezclados y cierre correcto |
| FIFO de audio | Mono/estéreo, wrap de índices, cola acotada, faltas y fundidos pasaron |
| Sanitizadores | ASan/UBSan pasaron en FIFO, buzón y frontend Linux con núcleo real; no equivalen a ejecutar Android |
| Guardado anterior | Un snapshot de la compilación previa cargó en Linux y mantuvo continuación determinista |
| Reinicios del núcleo | Tres arranques comparan estado y PCM; no se conserva audio del arranque anterior |
| JVM de controles | IDs, A+B, soltado/cancelación, botones superpuestos, SELECT/START y captura extendida de cruceta pasaron |
| Mandos SDL | Prueba virtual volvió a pasar; el acelerómetro está deshabilitado como joystick en Android |
| Windows | Aplicación y verificador Win32 recompilados con LLVM-MinGW; no se ejecutaron en Windows |
| Android | Release ARM64 y APK de instrumentación compiladas, `-O3`, LTO y PPU LLE verificados en las órdenes NDK; lint: 0 errores y 60 advertencias |
| Paquete | Firma Release válida con el mismo certificado; `zipalign -c -P 16 4` y alineación ELF de 16 KiB pasaron; solo ARM64 |

Las pruebas headless ejecutan el código C real del frontend y el juego. Usan los
drivers de prueba de SDL, sin pantalla ni salida audible; esos drivers y el
bloqueo artificial no están habilitados en la APK Android. La prueba MotionEvent
se compiló, pero sigue pendiente ejecutarla en ARM64.

Una medición consecutiva sin compilaciones simultáneas en este servidor compartido
dio los siguientes tiempos. Son una muestra de rendimiento x86_64, no una medición
del teléfono ni una promesa de 60 FPS. Los hashes finales de estado, imagen y
audio coinciden con el binario anterior en ambos modos.

| Trabajo por cuadro de juego | Anterior, GCC `-O3` | Actual, `-O3` + LTO + tramos LLE |
|---|---:|---:|
| Núcleo 4:3 | 11,874 ms | 10,530 ms |
| Núcleo 16:9 | 16,058 ms | 10,788 ms |
| Compositor 16:9 | 0,586 ms | 0,505 ms |

APK actual: `MushroomRecompiled-Android-arm64.apk`, **6.374.819 bytes**,
package `org.mushroomrecompiled`, versión `15002` / `1.5.0-android.2`.
SHA-256:
`2e2168f321b788c7bbafdc662d9951e1dd1b7656e713b3a92bfd48e31f8c873b`.
No contiene `.nes`, código C privado generado ni keystore. Las bibliotecas
compiladas sí contienen código derivado del juego, como requiere la recompilación.
Conservar el mismo respaldo privado de firma de la entrega anterior.

## Primera entrega: `1.5.0-android.1`

| Prueba | Resultado |
|---|---|
| Generador NESRecomp compilado para Linux | Pasó |
| Generación del C a partir de la ROM real | 11.769 instrucciones, 19 archivos de bancos y dispatch; una instrucción queda en el fallback original |
| `npm test` original | 4/4 pasaron; se restauró `tools/rom.mjs`, que faltaba en el fork |
| SHA-256 y CRC en C | Vectores de referencia, relleno SHA de dos bloques y rechazo de entradas inválidas pasaron |
| Validador Java y multitáctil en JVM | Pasaron ROM real, rechazo de datos modificados, A+B, IDs independientes, dos dedos en B, deslizamiento, diagonales, soltar y cancelar |
| `smb3-verify` original | Pasó: 3.000 cuadros, 1.316 cambios de imagen y 2.395.990 muestras de audio mono |
| Cobertura real del guion anterior | 89.334.341 ciclos nativos, 4.770 de fallback en ROM, 0 en RAM, 0 de otro tipo; aproximadamente 99,9947% nativos |
| Comparación del mismo guion | 3.000 hashes de estado nativo/referencia coinciden; mismo modelo de hardware del runtime, no comparación con una NES física |
| Snapshot del runtime | Continuación determinista después de guardar/cargar |
| Reinicio de sesión del núcleo | Pasó: tres ciclos cargar/ejecutar/descargar, sin ROM lógica ni botones retenidos y con arranque determinista |
| Audio generado por el núcleo | 60 cuadros: 43.968 muestras a 44,1 kHz, 47.857 a 48 kHz y 95.714 a 96 kHz |
| `smb3-wide-verify` original | Pasó: 1.800 hashes nativo/referencia con la misma política amplia, activación/retención de enemigos, capas de sprites, HUD, márgenes, bordes y snapshot |
| `smb3-scenes-verify` original, fases 4 y 6 | Ambas pasaron: terreno, plataformas, alineación OAM y retorno al encuadre nativo en escenas incompatibles |
| SDL_GameController virtual | Pasaron A+B, soltado, diagonales, analógico, reasignación, conservación de eventos y hotplug |
| Destinos de vídeo Android | Pasaron 4:3, 16:9, entero, píxeles cuadrados y diversos tamaños de área segura, incluido 2340×1080 |
| Entrada privada ausente en CI | Falla con los nombres de los secrets necesarios, sin generar ROM ni APK falsas |
| Código recompilado ausente en CMake | Rechazado con mensaje de generación requerida |
| Compilación y lint Android | Release ARM64 y APK de instrumentación compiladas; lint: 0 errores y 55 advertencias, sin baseline que oculte errores |
| Firma y empaquetado de la APK | Firma Release RSA de 3072 bits válida, esquema v2; `zipalign -c -P 16 4` pasó; las tres bibliotecas son AArch64 con segmentos LOAD alineados a 16 KiB |
| Compilación cruzada Windows | Pasó: LLVM-MinGW x86_64, aplicación Win32, recursos e interfaz, más los cuatro verificadores originales; sin ejecutar los binarios Windows |

Se inspeccionaron capturas generadas por el núcleo real: mapa del mundo y nivel
horizontal 1-1 ampliado a 426×240. Las capturas, ROM, snapshots y fuentes
generados permanecen fuera de los archivos públicos.

La APK de la primera entrega fue `MushroomRecompiled-Android-arm64.apk`, de 6.399.875 bytes,
package `org.mushroomrecompiled`, versión `1.5.0-android.1` / `15001`, SDK mínimo
26 y objetivo 36. Contiene `libmain.so`, `libSDL2.so` y `libc++_shared.so`
exclusivamente para `arm64-v8a`; se comprobó el JNI de inicio de sesión del
binario final. No contiene archivos `.nes`, fuentes C generados ni claves de
firma. El código nativo compilado sí deriva de la ROM privada.

SHA-256 de la APK:
`4220d12747abf746ba1d373ad33d026fc7dad99268ecb06ae7b3a3549bab62b9`.
Conservar el respaldo privado del keystore para firmar futuras actualizaciones.
Las advertencias de lint restantes incluyen compatibilidad heredada de SDL,
APIs obsoletas y recomendaciones de interfaz; no se han probado en dispositivo.

## Matriz de las pruebas solicitadas

| # | Flujo | Evidencia actual / pendiente |
|---|---|---|
| 1 | Abrir la aplicación | Pendiente en Android |
| 2 | Selector de ROM | SAF implementado; pendiente probar proveedores de documentos en Android |
| 3 | Validación exacta | C, Java y Python usan tamaño + CRC + SHA; pendiente interacción del selector |
| 4 | Arranque recompilado | Ejecutado en Linux con cobertura medida; pendiente arranque ARM64 en dispositivo |
| 5 | Pantalla de título | Cubierta por arranque y guion del núcleo; pendiente presentación Android |
| 6 | Mapa del mundo | Captura real del núcleo; pendiente presentación Android |
| 7 | Nivel 1-1 | Ejecutado por la prueba panorámica; pendiente flujo Android |
| 8 | Movimiento | Guion real y cámara ampliada; pendiente tacto/mando Android |
| 9 | Saltos, carreras y vuelo | Guion A/B/derecha ejecutado; vuelo y respuesta física pendientes |
| 10 | Enemigos y colisiones | Enemigos y estados comparados; validación manual de colisiones y todas las fases pendiente |
| 11 | Sonido | Muestras reales no nulas y frecuencias correctas; escucha, latencia, Bluetooth y ausencia de cortes pendientes |
| 12 | Pausa/reanudación | Frontend nativo probado en Linux; pendiente probar Activity, Home, bloqueo, permisos y cambio de app Android |
| 13 | Guardar/cargar | Cinco slots y snapshot anterior probados en Linux; pendiente usarlos desde Android y comprobar persistencia tras cierre |
| 14 | 4:3 | Núcleo original y cálculo de destino probados; pendiente presentación Android |
| 15 | 16:9 | Regresiones originales pasaron; pendiente presentación y rendimiento Android |
| 16 | Multitáctil | Lógica JVM pasó; prueba de MotionEvent y dedos físicos pendiente en ARM64 |
| 17 | Mandos | Mando virtual SDL pasó; Bluetooth/USB/Xbox/PlayStation físicos pendientes |
| 18 | Salir y reabrir | Dos sesiones del frontend y tres del núcleo pasaron en Linux; pendiente ciclo completo de Activity/SAF en Android |

## Reproducir las pruebas sin dispositivo

```sh
export SMB3_ROM_PATH='/ruta/privada/compatible.nes'
python3 tools/generate_android.py --rom "$SMB3_ROM_PATH"
cmake -S . -B build/verify -DCMAKE_BUILD_TYPE=Release -DSMB3_TEST_ROM="$SMB3_ROM_PATH" -DSMB3_LLE_RENDER_SPANS=ON
cmake --build build/verify --parallel 4
ctest --test-dir build/verify --output-on-failure
npm test

# Con SDL2 de desarrollo y JDK instalados, añadir:
# -DSMB3_CONTROLLER_TESTS=ON -DSMB3_FRONTEND_TESTS=ON
# Solo validación/viewport, sin disponer de la ROM:
cmake -S . -B build/platform -DSMB3_PLATFORM_TESTS_ONLY=ON
cmake --build build/platform
ctest --test-dir build/platform --output-on-failure

mkdir -p build/java-tests
javac -d build/java-tests android/app/src/main/java/org/mushroomrecompiled/TouchState.java \
  android/app/src/main/java/org/mushroomrecompiled/TouchGeometry.java \
  android/app/src/main/java/org/mushroomrecompiled/RomValidator.java tests/android_host/TouchStateTest.java
java -cp build/java-tests org.mushroomrecompiled.TouchStateTest "$SMB3_ROM_PATH"
```

## Pruebas que necesitan Android ARM64

```sh
cd android
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w org.mushroomrecompiled.test/androidx.test.runner.AndroidJUnitRunner
```

`TouchInstrumentationTest` envía DOWN, POINTER_DOWN, POINTER_UP, MOVE, UP y CANCEL
reales a la vista Android y comprueba las máscaras JNI. **Crear su APK no equivale
a ejecutar esa prueba.** Después es necesario probar con dedos físicos, cambios
de dirección, A+B, pérdida de foco, desconexión y conexión de mandos.

En el Galaxy A15 SM-A155M con Android 16 se debe completar la matriz anterior,
incluido seleccionar un `.nes` local y uno de Drive, cancelar el selector,
rechazar ROM incorrecta/truncada, borrar o revocar el URI recordado, reabrir sin
ROM y recuperar la selección sin cierres. Probar cada slot y reiniciar la app.
Repetir 4:3 y 16:9 en mapa, 1-1, salas verticales, bonus, transiciones y partidas
largas; probar recorte de cámara y navegación por gestos. Medir FPS, temperatura,
latencia y contadores de audio durante al menos 30 minutos. No se garantiza
**60 FPS estables** hasta disponer de esas mediciones.

## Límites restantes

- 19:9 no está habilitado: la expansión comprobada del proyecto original es 16:9.
- La compatibilidad completa de todas las fases ya estaba pendiente en v1.5.0;
  los guiones actuales no demuestran el juego entero ni vuelo en todas sus formas.
- Las pruebas Win32 de interfaz necesitan ejecución en Windows; una compilación
  cruzada solo puede comprobar que los targets siguen compilando y enlazando.
- El workflow se entrega con secretos externos explícitos. Su ejecución en
  GitHub depende de configurar el environment privado y la firma persistente;
  no se ha publicado la ROM para facilitar una ejecución automática.
