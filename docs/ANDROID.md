# Mushroom Recompiled: Android ARM64

Este port integra el juego recompilado de la base **v1.5.0**. No contiene una ROM,
WebView, RetroArch, libretro ni un sustituto del juego. La APK incluye código
nativo derivado de la ROM; el árbol público de fuentes no incluye ese código ni
los datos extraídos. Se necesita una ROM privada tanto para generar el C al
compilar como para cargar los datos originales al ejecutar.

## Identidad del archivo autorizado

Se validan **las tres condiciones**, también en el selector Android:

| Campo | Valor |
|---|---|
| Juego | Super Mario Bros. 3 (USA) (Rev 1).nes |
| Tamaño | 393232 bytes |
| CRC32, excluyendo los primeros 16 bytes | `2E6301ED` |
| SHA-256 de todo el archivo | `4377A7F5E6EB50BDD2AC6F249BF1A7085500ACA8EB41F38545C3A2731C51A579` |

La petición inicial indicaba `1DDC4B429490E298C05FA70389034107C0CB50F671C7C1BAAA3F228224AFC2E9`.
La ROM facilitada por el usuario coincidía en tamaño y CRC, pero tenía el SHA-256
de la tabla. El usuario autorizó expresamente utilizar su identidad real sin
modificar el archivo. No se cambia la cabecera ni se acepta cualquier ROM por
tener el mismo nombre. La comprobación también se actualizó en el script Windows.

## Arquitectura

`runtime/glue.c`, `src/widescreen.c` y `src/wide_enemies.c` forman parte del mismo
`smb3-core` que PC. CPU 6502, PPU, APU, DMA, MMC3, ejecución por ciclos, snapshots
y fallback limitado proceden de `tools/nesrecomp-src/runner/cyc`. La compilación
Windows original también enlaza ese directorio. Los encabezados heredados de
`runtime/framework/` se conservan; no se conecta otro runtime en paralelo.

`tools/generate_android.py` compila **NESRecomp para el anfitrión**, valida la ROM
y utiliza `game.toml` y `docs/smb3-cycle-seeds.txt` para emitir el C en `generated/`.
El NDK compila después ese C para `arm64-v8a`. Un manifiesto privado identifica
la ROM, configuración, semillas y fuentes del generador. Si falta la entrada,
la generación falla; CMake rechaza un árbol sin el dispatch real del juego.

La interfaz Win32 en `src/app.c` y `src/preferences.c` permanece en el target
Windows. Android usa `GameActivity`/JNI y `Settings`/SharedPreferences. La entrada
de mandos en `src/input.c` es compartida; se conserva `SDL_GameController`, el
fallback de joystick, SDL_GameControllerDB, analógico, deadzone y once acciones
reasignables. El nuevo `input_refresh()` permite que Android procese sus eventos
SDL sin perder eventos de ciclo de vida o del renderizador.

La preparación aplica un parche pequeño al Java original de SDL 2.30.12 para
declarar la exportación de los receivers USB/Bluetooth que Android 14+ exige.
Los mandos Android habituales no requieren permiso de archivos ni escaneo.
El permiso de dispositivos cercanos para Bluetooth HID especializado puede
habilitarse desde Controles, cuando el usuario lo necesita.

El hilo `NESRecomp` es el único que accede al núcleo. Las acciones JNI se ponen en una
cola; los dedos y la pausa se comunican mediante atómicos. El hilo SDL presenta
el último framebuffer completo mediante un buzón fijo protegido; la espera de
EGL/vsync no bloquea los ciclos ni la producción de audio. Se usa un renderer
SDL acelerado **OpenGL ES**, una textura ARGB8888 reutilizada y el framebuffer
original. SDL abre el dispositivo de audio Android; mantiene la cadencia NTSC
del runtime sin cambiar la velocidad para corregir el sonido.
La presentación puede omitir imágenes atrasadas mientras el núcleo ejecuta todos
sus cuadros y conserva su audio a la cadencia original.
El diagnóstico distingue FPS del juego y FPS presentados.
Hay volumen,
mute, mono/estéreo dual mono, 44.1/48/96 kHz y reserva de 20–80 ms (24 ms por defecto).
AAudio solicita baja latencia; OpenSL ES y AudioTrack sirven de alternativas.
El callback usa un FIFO fijo y adapta la reserva mínima al bloque negociado.
Los contadores
de audio y ejecución pueden consultarse y exportarse en Rendimiento y pruebas.
Consultar [ANDROID_PERFORMANCE.md](ANDROID_PERFORMANCE.md) para probar la revisión
`.2`, los nuevos controles y las optimizaciones de la PPU LLE.

## Uso

1. Instalar la APK ARM64 y abrir Mushroom Recompiled en horizontal.
2. Seleccionar el **archivo `.nes` descomprimido** con el selector de documentos
   del sistema. No se requieren permisos generales de almacenamiento.
3. Iniciar el juego. Se recuerda el URI SAF con permiso persistente y una copia
   validada queda en almacenamiento privado. En el siguiente arranque se vuelve
   a abrir el documento original. Al volver de otra aplicación también se
   comprueba el acceso, sin reiniciar la partida. Si falta o cambia, se ofrece
   seleccionar nuevamente y la aplicación permanece abierta.
4. Atrás, el botón pequeño de menú o la acción Menú del mando abren la pausa.
   Continuar, Reiniciar, Guardar, Cargar, Controles, Vídeo, Audio, Cambiar ROM,
   Información y Salir ejecutan operaciones reales.

Los cinco slots usan snapshots del runtime y se guardan en el directorio privado
de la app. Se escribe un temporal, se sincroniza el archivo y se renombra para
no truncar el slot previo si falla la escritura. Cambiar de app libera botones,
pausa el núcleo y limpia la cola de sonido. Volver muestra el menú de pausa.
No hay restauración automática de un snapshot: el usuario elige Cargar estado.

La cruceta permite diagonales y deslizamiento. Cada dedo se identifica por su
**pointer ID**, independientemente de su índice. A+B simultáneos y varios dedos
sobre el mismo botón se combinan sin liberar el botón que aún tiene otro dedo.
UP/POINTER_UP, CANCEL, pérdida de foco, pausa y desconexión liberan las entradas.
El menú Controles permite arrastrar cada control, ajustar su tamaño, opacidad,
deadzone y reasignaciones. Un mando puede ocultar los controles; el menú sigue
visible. Las posiciones quedan limitadas por el área segura y se guardan.
Por defecto se reserva una franja inferior para START/SELECT para que no tapen
el HUD. Se puede desactivar; desaparece al ocultar los controles con un mando.

4:3 presenta el framebuffer original con el aspecto NES de PC. 16:9 usa el
compositor original de 426×240 y su activación/render de enemigos; no estira un
framebuffer 4:3 para llenar la pantalla. El HUD y las escenas incompatibles se
mantienen centrados con márgenes negros. También hay escalado entero, píxeles
cuadrados, filtro nítido/suave, brillo y scanlines. Las zonas de recorte y barras
de Android se descuentan del destino. **19:9 no se implementa**: requiere ampliar
y validar cámara, culling y activación fuera del alcance original de 16:9.

## Compilación local en Linux/macOS

Requiere Python 3.12+, CMake, compilador C del anfitrión, **JDK 17**, Android SDK
36, build-tools 36.0.0, NDK **28.2.13676358**, CMake Android **3.31.6**. Gradle
8.13 y SDL 2.30.12 están fijados y tienen SHA-256 verificado. No requiere Windows.
El NDK r28 y el empaquetado moderno permiten alineación nativa de 16 KiB.

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;28.2.13676358' 'cmake;3.31.6'
export SMB3_ROM_PATH='/ruta/privada/Super Mario Bros. 3 (USA) (Rev 1).nes'
python3 tools/prepare_android.py
python3 tools/generate_android.py --rom "$SMB3_ROM_PATH"

# Usar una clave Release persistente y conservarla para todas las actualizaciones.
export ANDROID_KEYSTORE_PATH='/ruta/privada/release.jks'
export ANDROID_KEYSTORE_PASSWORD='contraseña-privada'
export ANDROID_KEY_ALIAS='mushroom-release'
export ANDROID_KEY_PASSWORD='contraseña-privada'
cd android
./gradlew :app:exportReleaseApk :app:lintRelease
```

Resultado: `build/android-release/MushroomRecompiled-Android-arm64.apk`.
Release falla con un mensaje claro si falta ROM o firma; no usa una firma debug
como sustituto. Para desarrollo se puede usar `:app:assembleDebug`, que también
requiere generar el juego real. El package name es **`org.mushroomrecompiled`**;
mantenerlo y mantener el mismo certificado para actualizar una instalación.
No versionar ni compartir públicamente el keystore o sus contraseñas.

## GitHub Actions: entrada privada

El workflow `.github/workflows/android.yml` se ejecuta manualmente y usa el
environment **`android-private-build`**. Configurarlo con acceso limitado a las
ramas de confianza y, si corresponde al equipo, revisión del environment.
El workflow no está conectado a pull requests externos.

La ROM no cabe en un Secret de GitHub. Guardar una copia **cifrada** en un
almacenamiento privado accesible mediante HTTPS y usar un URL firmado de corta
duración. Solo el URL y la contraseña pequeña se guardan como secrets. Ejemplo
para producir el archivo privado (el original no cambia):

```sh
export ROM_ARCHIVE_PASSWORD='contraseña-larga-privada'
openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
  -pass env:ROM_ARCHIVE_PASSWORD -in '/ruta/original.nes' -out '/ruta/privada/game.nes.enc'
```

Secrets del environment:

| Secret | Contenido |
|---|---|
| `ROM_DOWNLOAD_URL` | URL HTTPS privado al `.nes.enc`, válido durante el job |
| `ROM_ARCHIVE_PASSWORD` | Contraseña usada con el comando anterior |
| `ANDROID_KEYSTORE_BASE64` | Base64 del keystore Release pequeño |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del keystore |
| `ANDROID_KEY_ALIAS` | Alias existente |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave |

El helper limita la descarga, descifra en un temporal privado y verifica tamaño,
CRC y SHA antes de generar código. No imprime URL, clave ni bytes de la ROM.
No se guardan cachés con datos derivados. El único artifact subido es
**MushroomRecompiled-Android-arm64.apk**, con retención de siete días; no se
adjuntan ROM, código generado, snapshots, capturas, símbolos ni keystore. La
limpieza final borra entradas e intermedios privados incluso tras un error.
La APK sí contiene código derivado del juego, como requiere una recompilación;
su distribución debe respetar los derechos aplicables.

## Verificación

Consultar [ANDROID_TESTS.md](ANDROID_TESTS.md) para resultados, comandos y pruebas
pendientes en hardware. Compilar una APK no demuestra arranque, audio audible,
multitáctil real, compatibilidad con un mando físico ni 60 FPS en un Galaxy A15.

Se conservan MIT para la integración, **PolyForm Noncommercial 1.0.0** para
NESRecomp, zlib para SDL2/ControllerDB y las demás licencias de `LICENSES/`.
Los textos también se empaquetan y pueden leerse en Información > Licencias.
