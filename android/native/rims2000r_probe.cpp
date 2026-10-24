// RiMS2000R Android native build/link probe. No sound or UI yet.
// Code derived from the GPL-3.0-licensed upstream project.
#include <cstdint>
#include "core/rom_paths.h"
#include "core/ms2000_runner.h"

#if defined(__GNUC__)
#  define RIMS_EXPORT __attribute__((visibility("default")))
#else
#  define RIMS_EXPORT
#endif

extern "C" {

RIMS_EXPORT uint32_t rims2000r_native_abi_version() { return 1; }

// 1 = required ROMs present; 0 = missing; -1 = invalid directory.
RIMS_EXPORT int rims2000r_has_roms(const char* directory) {
    if (!directory || !directory[0]) return -1;
    return ms2k::hasRoms(std::filesystem::path(directory)) ? 1 : 0;
}

// Force the .so to link the real H8S/DSP emulator objects, not just a stub.
// This intentionally DOES NOT boot the firmware or render audio.
RIMS_EXPORT int rims2000r_link_probe() {
    MS2000::Ms2kConfig config;
    config.enableMidiSmokeTest = false;
    MS2000::Ms2kGuiHooks hooks;
    MS2000::Ms2kRunner runner(config, hooks);
    return runner.ready() ? 2 : 1;
}

} // extern "C"
