# RiMS2000R Android — Alpha 0.1.1

Primera aplicación instalable de diagnóstico para Android ARM64 (mínimo Android 8 / API 26). Conserva el núcleo H8S/2350 + DSP56362 y ejecuta firmware aportado por el usuario. **Una compilación correcta no demuestra que arranque, suene bien o rinda a tiempo real en la Galaxy Tab A9. La validación física sigue pendiente.**

## Corrección de interfaz en 0.1.1

- Reserva dinámicamente el espacio de las barras de estado/navegación, la barra de tareas y los recortes de pantalla. No se ocultan las barras ni se supone una altura fija.
- **Copiar diagnóstico** queda en la cabecera fija, fuera del desplazamiento. Se mantiene el acceso al final del diagnóstico.
- Los controles y el diagnóstico siguen siendo desplazables dentro de la zona segura, también al cambiar la altura disponible.
- La versión mostrada y la copiada se obtienen de `BuildConfig.VERSION_NAME`.
- No se modifican el motor nativo, la síntesis, los buffers, MIDI ni la importación de firmware. Se mantiene el identificador de aplicación para actualizar conservando ROMs y preferencias, siempre que se use la misma firma.

Validación visual definitiva pendiente en la Galaxy Tab A9: comprobar botón superior fijo, reloj sin solapamiento y fila inferior por encima de la barra de tareas.

## Obtener y probar la APK

En GitHub Actions, abrir una ejecución exitosa de **Android APK Alpha** y descargar el artefacto **RiMS2000R-Android-0.1.1-alpha-APK**. Contiene el APK de desarrollo firmado, código fuente del mismo commit y sumas SHA-256. La antigua acción `Android ARM64 native build` sigue produciendo solo una biblioteca `.so`.

1. Instalar `RiMS2000R-Android-0.1.1-alpha.apk`.
2. Pulsar **Importar ROMs (ZIP)** y seleccionar un ZIP propio con `flash.bin` (1.048.576 bytes), `boot-362.bin` o `boot-362.ms2000.bin` (768 bytes). Opcional: `hd44780_a00.bin` (4.096 bytes). Se admiten subcarpetas como `full FW`; no se admiten archivos RAR directamente.
3. Elegir **512 muestras** y la salida de la tablet o sus auriculares. Dejar desactivado **DSP en otro hilo** para la primera prueba.
4. Pulsar **Arrancar**. Tras cuatro segundos de tiempo emulado se habilitan las notas. Comprobar el LCD y mantener pulsado **Do** o **4 notas**; empezar con volumen moderado.
5. Conectar un teclado USB al hub, pulsar **MIDI USB** y elegir su puerto de salida. Los bytes MIDI se envían al firmware; probar Note On/Off, CC1, sustain CC64 y pitch bend.
6. Copiar el diagnóstico después de probar cambios de programa, acordes y varios minutos de uso. Probar la cola de 1024 o la opción de hilo DSP por separado si hay cortes.

## Incluido

- Importación ZIP acotada y validada, en almacenamiento privado. Rechaza rutas peligrosas, duplicados y tamaños incorrectos; no elimina una importación válida si la nueva falla. No identifica por sí sola la autenticidad o versión del firmware.
- Audio AAudio estéreo float a 48 kHz; selector de dispositivo y volumen con rampa. Rechaza formatos no compatibles en lugar de reproducir a velocidad incorrecta.
- Emulación en hilo productor; el callback de audio solo consume una FIFO preasignada, sin ejecutar el JIT ni tomar mutex.
- Colas seleccionables de 256/512/1024 muestras. El tamaño puede elevarse a dos bursts del dispositivo. **La cola de la app y el buffer AAudio son distintos y ambos se muestran; no representan la latencia total.**
- Carga de render media/pico, faltantes de cola, xruns AAudio, tiempo emulado y MIDI recibido/descartado. La carga es tiempo de cálculo dividido por el plazo de audio, no utilización global de CPU ni un benchmark del hardware.
- Entrada USB MIDI con reconstrucción de mensajes fragmentados y running status; cuantización al siguiente bloque de 128 frames, no timestamps sample-accurate.
- Program Change, canal para las notas de prueba, PANIC, cuatro notas táctiles, acorde de prueba y teclas QWERTY A-S-D-F-G-H-J-K.
- Pantalla encendida en primer plano. Se detiene el motor al pasar a segundo plano. No pide permisos de Internet, micrófono ni acceso general al almacenamiento.

## No incluido / pendiente de validar

Panel completo, biblioteca `.syx`, MIDI OUT, entrada de audio y vocoder, funcionamiento en segundo plano, pruebas físicas de latencia/consumo/estabilidad y fidelidad. El botón de reset limpia carga y faltantes; los xruns AAudio pertenecen al stream actual. La selección de canal de prueba no reprograma el canal Global del firmware.

## Compilar

JDK 17, Gradle 8.9, Android SDK 35, Build Tools 35.0.0, NDK r27c `27.2.12479018` y CMake 3.22.1:

```sh
gradle -p android :app:assembleDebug
```

La clave de firma de desarrollo se conserva mediante caché de Actions para permitir actualizaciones dentro de esta rama. Si se pierde la caché, la firma puede cambiar: no es una clave de producción.

## Pruebas

El workflow ejecuta tests de FIFO (incluida concurrencia), mensajes MIDI e importación transaccional con datos sintéticos. Compila la app y verifica firma, presencia de la biblioteca ARM64 y ausencia de los nombres ROM conocidos en el APK. **No ejecuta ROMs en CI.**

## Licencia y firmware

GPL-3.0; conservar avisos de Tamás Bársony/Tuth y The Usual Suspects/DSP56300. La APK muestra la licencia y el artefacto incluye el código fuente correspondiente. Las ROMs no forman parte del repositorio ni de artefactos públicos. Korg y MS2000 son marcas de Korg; esta adaptación no está afiliada ni avalada por Korg.
