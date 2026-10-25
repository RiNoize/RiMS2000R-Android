# RiMS2000R Android — Alpha 0.1.2

APK experimental ARM64 para Galaxy Tab A9. Núcleo original H8S/2350 + DSP56362; firmware separado aportado por el usuario. **La compilación no demuestra funcionamiento ni rendimiento en tiempo real en el dispositivo.**

## Cambios de 0.1.2

- Perfil nativo explícito `-O3` + ThinLTO para H8S, DSP56300, AsmJit y puente Android. Los símbolos internos se ocultan; JNI mantiene exportaciones explícitas. El workflow comprueba los comandos efectivos del compilador.
- No se usa `-ffast-math`. No se cambian los relojes emulados, la frecuencia de 48 kHz, la polifonía, las interrupciones del DSP ni el bloque de render de 128 frames.
- Prioridad de audio de mejor esfuerzo también para el hilo DSP opcional. La prioridad real del productor y el error de solicitud se muestran; no se requieren root ni prioridades RT privilegiadas.
- Carga acumulada y ventana de dos segundos de audio emulado. Tiempo de CPU del productor separado del tiempo transcurrido: **no incluye el hilo DSP separado**, si se activa.
- LCD leído del snapshot GPIO que usa el plugin original, en lugar del buffer obsoleto.
- Contadores de frames sin dato DSP, frames con señal y pico digital previo al volumen para diagnosticar silencio.
- El housekeeping no se envía rutinariamente a logcat; errores de la app siguen visibles.
- Sigue disponible la zona segura y el botón fijo **Copiar diagnóstico** de 0.1.1.

**No hay todavía medición de la ganancia en la tablet.** Comparar primero con 512 muestras, DSP separado desactivado y el mismo programa. Después variar una sola opción por vez (1024 o hilo DSP).

## Descarga e instalación

GitHub Actions → ejecución exitosa de **Android APK Alpha** → artefacto **RiMS2000R-Android-0.1.2-alpha-APK**. Incluye APK, fuente del mismo commit, SHA-256 y `SIGNING.txt`, que compara el certificado con 0.1.1. **No desinstalar una versión con datos sin respaldo.** No asumir continuidad de firma solo porque el APK tenga el mismo identificador.

La acción anterior **Android ARM64 native build** entrega una biblioteca `.so`, no una app.

## Prueba en la tablet

1. Instalar la APK conforme al resultado de `SIGNING.txt`.
2. Importar un ZIP propio con `flash.bin` (1.048.576 bytes), `boot-362.bin` o `boot-362.ms2000.bin` (768 bytes). Opcional: `hd44780_a00.bin` (4.096 bytes). No se importa RAR directamente.
3. Elegir cola 512, auriculares/altavoz de la tablet y DSP separado desactivado.
4. Arrancar, comprobar LCD, tocar una nota y un acorde. El estado activo tras 4 segundos emulados no es validación automática del firmware.
5. Conectar USB MIDI y elegir el puerto del teclado. Pasan Note On/Off, CC1, CC64 y pitch bend al firmware.
6. Reiniciar medidores antes de una prueba repetible, tocar el mismo programa y copiar diagnóstico después de varios minutos. Observar media, pico y crecimiento de underruns, no solo CPU global.

Cola de la app y buffer AAudio son distintos: ninguno representa por sí solo la latencia total. CPU de productor y tiempo de render también son métricas distintas.

## Funciones y límites

Importación ZIP privada acotada/validada, AAudio estéreo float, volumen con rampa, salida seleccionable, productor y FIFO sin bloqueo en el callback, MIDI USB, Program Change, notas táctiles/QWERTY, PANIC y pantalla encendida. Se detiene el motor al pasar a segundo plano.

Pendientes: panel completo, biblioteca `.syx`, MIDI OUT, audio de entrada/vocoder, funcionamiento en segundo plano y validación física de sonido/latencia/estabilidad. La selección de canal de prueba no reprograma el canal Global del firmware.

## Compilación y pruebas

JDK 17, Gradle 8.9, SDK 35, Build Tools 35.0.0, NDK r27c `27.2.12479018`, CMake 3.22.1:

```sh
gradle -p android :app:assembleDebug
```

CI prueba FIFO concurrente, framing MIDI, importación ZIP y métricas; verifica `-O3`/ThinLTO, firma y biblioteca ARM64. No ejecuta ni publica ROMs. La clave de desarrollo se conserva en caché; no es una clave de producción y la compatibilidad de actualización se comprueba con certificados.

## Licencia

GPL-3.0. Conservar atribuciones de Tamás Bársony/Tuth y The Usual Suspects/DSP56300. El artefacto incluye fuente correspondiente. **No incluir ROMs en GitHub, CI ni APK.** Korg/MS2000 son marcas de Korg; esta adaptación no está afiliada ni avalada por Korg.
