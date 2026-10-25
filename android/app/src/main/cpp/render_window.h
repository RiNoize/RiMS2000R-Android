#pragma once
#include <algorithm>
#include <cstdint>

// Producer-thread only. CPU is CLOCK_THREAD_CPUTIME_ID, not total process CPU.
// A window spans two seconds of produced audio, independent of UI refresh rate.
struct RenderWindow {
    static constexpr uint64_t kFrames = 96000;
    uint64_t wallNs = 0, cpuNs = 0, frames = 0, maxNs = 0;
    void clear() { wallNs = cpuNs = frames = maxNs = 0; }
    bool add(uint64_t wall, uint64_t cpu, uint32_t n) {
        wallNs += wall; cpuNs += cpu; frames += n;
        maxNs = std::max(maxNs, wall);
        return frames >= kFrames;
    }
    static double percent(uint64_t ns, uint64_t frameCount) {
        return frameCount ? double(ns) * 48000.0 / double(frameCount) / 1e7 : 0.0;
    }
};
