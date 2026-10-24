# RiMS2000R Android — Alpha 0.1.0, ARM64 bootstrap

**Status:** primera etapa para **compilar y enlazar** el núcleo original H8S/2350 + DSP56362 en Android `arm64-v8a`. Todavía **NO** es una APK ni ofrece audio, MIDI o interfaz Android.

## Compilación nativa

Se requieren Android NDK r27c (`27.2.12479018`), Ninja y CMake. Desde la raíz del proyecto:

```bash
cmake -S . -B build/android-arm64 -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_HOME/ndk/27.2.12479018/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build build/android-arm64 --target rims2000r_native --parallel 2
```

El resultado previsto es `librims2000r_native.so`, una biblioteca de diagnóstico que expone `rims2000r_native_abi_version()`, `rims2000r_has_roms()` y `rims2000r_link_probe()`. Los símbolos no arrancan el firmware. La compilación y su enlace en Android **aún deben confirmarse** en CI.

El workflow de GitHub Actions `Android ARM64 native build` intenta compilar la biblioteca sin archivos ROM. Si tiene éxito, publica un artefacto con la biblioteca nativa; **no es una APK instalable**.

## Cambios de portabilidad

- Aísla el núcleo de VST3, interfaz desktop y tests de escritorio durante la compilación Android.
- Excluye VTune/Perf y `librt` donde Android no los ofrece.
- Impide que Android se confunda con Linux/ALSA en `audio_output.cpp`. El backend de audio AAudio/Oboe vendrá después.
- Incorpora correcciones ARM64 equivalentes a las propuestas en [Tuth/ms2000r-emu PR #10](https://github.com/Tuth/ms2000r-emu/pull/10): `intrin.h` solo en MSVC, `_ReturnAddress()` para Clang y guard del hexdump JIT x64.

## Próximos pasos

1. Resolver los errores del compilador NDK/Clang y enlazar el núcleo ARM64.
2. Ejecutar ROMs obtenidas legalmente de un MS2000R, medir velocidad real y generar bloques estéreo de 48 kHz.
3. Integrar Android MIDI USB, Oboe/AAudio, selector de buffer y métricas de CPU/underruns.
4. Construir una APK y su panel táctil horizontal adaptado a Galaxy Tab A9.

## ROMs y licencia

No se incluyen ROMs ni firmware Korg. El motor necesita `flash.bin` y `boot-362.bin` de hardware propio. Estos archivos **no se deben subir al repositorio ni incluir en artifacts públicos o en la APK**. Se preserva [GPL-3.0](../LICENSE.md), con atribución al proyecto de Tamás Bársony/Tuth y al emulador DSP56300.
