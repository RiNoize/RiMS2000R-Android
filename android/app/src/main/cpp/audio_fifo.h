#pragma once
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>

// Single producer / single consumer. Reset only after BOTH threads are stopped.
// The real-time callback only reads this FIFO; it never calls the emulator.
template<uint32_t Capacity = 16384> class AudioFifo {
    static_assert((Capacity & (Capacity - 1)) == 0, "capacity must be a power of two");
    static_assert(std::atomic<uint32_t>::is_always_lock_free, "audio counters must be lock free");
    std::array<float, Capacity * 2> samples_{};
    alignas(64) std::atomic<uint32_t> write_{0};
    alignas(64) std::atomic<uint32_t> read_{0};
public:
    void reset() { write_.store(0); read_.store(0); }
    uint32_t size() const {
        const uint32_t w = write_.load(std::memory_order_acquire);
        const uint32_t r = read_.load(std::memory_order_acquire);
        const uint32_t d = w - r;
        return d <= Capacity ? d : 0; // a statistics reader can see a newer read index
    }
    bool push(const float* left, const float* right, uint32_t frames) {
        const uint32_t w = write_.load(std::memory_order_relaxed);
        const uint32_t r = read_.load(std::memory_order_acquire);
        if (frames > Capacity || frames > Capacity - (w - r)) return false;
        for (uint32_t i = 0; i < frames; ++i) {
            const uint32_t at = ((w + i) & (Capacity - 1)) * 2;
            samples_[at] = left[i]; samples_[at + 1] = right[i];
        }
        write_.store(w + frames, std::memory_order_release);
        return true;
    }
    uint32_t pop(float* interleaved, uint32_t frames) {
        const uint32_t r = read_.load(std::memory_order_relaxed);
        const uint32_t w = write_.load(std::memory_order_acquire);
        const uint32_t n = std::min(frames, w - r);
        for (uint32_t i = 0; i < n; ++i) {
            const uint32_t at = ((r + i) & (Capacity - 1)) * 2;
            interleaved[i * 2] = samples_[at]; interleaved[i * 2 + 1] = samples_[at + 1];
        }
        read_.store(r + n, std::memory_order_release);
        return n;
    }
};
