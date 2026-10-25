# RiMS2000R Android — Alpha 0.1.2 Perf Test

APK experimental ARM64 para Galaxy Tab A9. Motor original H8S/2350 + DSP56362; firmware separado. **Compilar no demuestra rendimiento en tiempo real en el dispositivo.**

## Instalación sin borrar 0.1.1

Esta APK se llama **RiMS2000R Perf Test**, con identificador `org.rinoize.rims2000r.perftest`. Se instala al lado de 0.1.1: no la reemplaza ni toca sus datos. Se usa este modo porque la clave anterior no estaba disponible en el runner. **No desinstalar la versión anterior.**

GitHub Actions → ejecución exitosa de **Android APK Alpha** → artefacto **RiMS2000R-Android-0.1.2-alpha-APK**. Contiene APK de prueba, fuente, SHA-256 y `SIGNING.txt`. La antigua acción **Android ARM64 native build** solo entrega una biblioteca `.so`.

1. Instalar `RiMS2000R-Android-0.1.2-alpha-perftest.apk` y abrir **RiMS2000R Perf Test**.
2. Importar de nuevo el mismo ZIP propio de ROMs; esta app tiene almacenamiento separado. No incluye ROMs ni importa RAR directamente.
3. Detener la app anterior y probar primero 512 muestras, la misma salida y programa, DSP separado desactivado. Solo una app debe estar generando audio.
4. Reiniciar los medidores, tocar una nota y acordes, y copiar diagnóstico después de varios minutos.
5. Como comparación separada, detener, activar **DSP en otro hilo** y repetir con el mismo programa y buffer. No cambiar varias opciones a la vez.

ROMs esperadas: `flash.bin` de 1.048.576 bytes y `boot-362.bin` o `boot-362.ms2000.bin` de 768 bytes. LCD opcional: `hd44780_a00.bin`, 4.096 bytes. El estado activo después de cuatro segundos emulados no valida automáticamente el arranque.

## Cambios de rendimiento y diagnóstico

- -O3/NDEBUG explícitos en todas las bibliotecas, ThinLTO solo para H8S/core y puente Android. DSP56300/AsmJit permanecen fuera de LTO como dependencias independientes. CI verifica los comandos reales.
- Sin fast-math ni cambios de relojes emulados, polifonía, 48 kHz o bloques de 128 frames.
- Prioridad de audio de mejor esfuerzo para el trabajador DSP opcional; diagnóstico de prioridad real del productor. Sin root ni prioridad RT privilegiada.
- Carga acumulada y ventanas de dos segundos emulados; tiempo de CPU del productor separado del tiempo transcurrido. **Con DSP separado, CPU productor no incluye ese segundo hilo.**
- LCD leído del snapshot GPIO que utiliza el plugin original, no del búfer obsoleto.
- Contadores de frames sin dato DSP, frames con señal y pico digital antes del volumen.
- Sin logcat síncrono rutinario durante housekeeping. Errores visibles en diagnóstico.
- Se conserva la zona segura y el botón fijo de copiar diagnóstico.

**No se ha medido aún la mejora en la Samsung.** Las comparaciones de audio locales x86_64 sirven para detectar regresiones en las secuencias probadas, no para estimar rendimiento ARM64.

Cola de app y buffer AAudio son distintos y no son la latencia total. AAudio puede no registrar xruns mientras el productor no entrega muestras suficientes y el callback completa con ceros.

## Funciones y límites

Importación ZIP privada acotada/validada, AAudio estéreo float, volumen con rampa, salida seleccionable, productor/FIFO, entrada USB MIDI, Program Change, notas táctiles/QWERTY, PANIC y pantalla encendida. Al pasar a segundo plano se detiene el motor.

Pendientes: panel completo, biblioteca SysEx, MIDI OUT, audio de entrada/vocoder, segundo plano y validación física de sonido, latencia y estabilidad. El canal de prueba no reprograma el canal Global del firmware.

## Compilar y validar

JDK 17, Gradle 8.9, SDK 35, Build Tools 35.0.0, NDK r27c `27.2.12479018`, CMake 3.22.1. Crear una clave de desarrollo local en `~/.android/debug.keystore` antes de compilar; CI la crea si falta y la conserva en caché, nunca la publica.

```sh
gradle -p android :app:assembleDebug -PrimsPerfSideBySide=true
```

Sin la propiedad `rimsPerfSideBySide`, se usa el identificador original; no utilizar esa variante como actualización sin comprobar certificados.

CI prueba FIFO concurrente, MIDI, importación ZIP y métricas; verifica optimizaciones, firma, identificador paralelo y biblioteca ARM64. No ejecuta ROMs. La caché de firma es de desarrollo, no una clave de producción.

## Licencia

GPL-3.0, con atribuciones a Tamás Bársony/Tuth y The Usual Suspects/DSP56300. Se incluye código fuente correspondiente. **No hay ROMs en GitHub, CI ni APK.** Korg/MS2000 son marcas de Korg; esta adaptación no está afiliada ni avalada por Korg.
