#include "../app/src/main/cpp/audio_fifo.h"
#include <cassert>
#include <thread>
#include <iostream>
int main() {
    AudioFifo<8> small;
    float l[8], r[8], out[16];
    for (int i = 0; i < 8; ++i) { l[i] = float(i); r[i] = -float(i); }
    assert(small.pop(out, 8) == 0);
    assert(small.push(l, r, 8)); assert(!small.push(l, r, 1));
    assert(small.pop(out, 3) == 3); assert(small.push(l, r, 3));
    assert(small.pop(out, 8) == 8);
    for (int i = 0; i < 5; ++i) assert(out[2*i] == float(i+3));
    for (int i = 0; i < 3; ++i) assert(out[2*(i+5)] == float(i));
    AudioFifo<1024> fifo;
    constexpr uint32_t count = 300000;
    std::thread producer([&] {
        for (uint32_t i = 0; i < count; ++i) {
            float a = float(i), b = -float(i);
            while (!fifo.push(&a, &b, 1)) std::this_thread::yield();
        }
    });
    for (uint32_t i = 0; i < count; ++i) {
        while (fifo.pop(out, 1) == 0) std::this_thread::yield();
        assert(out[0] == float(i)); assert(out[1] == -float(i));
    }
    producer.join(); assert(fifo.size() == 0);
    std::cout << "PASS: empty, full, wraparound and 300000 concurrent stereo frames\n";
}
