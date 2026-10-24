// RiMS2000R Android Alpha - original firmware engine, JNI, AAudio and USB-MIDI ingress.
// GPL-3.0; no proprietary ROM data in this file or the APK.
#include <jni.h>
#include <aaudio/AAudio.h>
#include <android/log.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <sys/resource.h>
#include <unistd.h>
#include "audio_fifo.h"
#include "core/ms2000_runner.h"
#include "core/rom_paths.h"

namespace {
using Clock = std::chrono::steady_clock;
constexpr uint32_t kBlock = 128;
constexpr uint64_t kWarmup = 48000 * 4;
static_assert(std::atomic<float>::is_always_lock_free, "audio gain must be lock free");

class Engine {
    std::mutex lifecycle_, textMutex_, midiMutex_;
    std::unique_ptr<MS2000::Ms2kRunner> runner_;
    std::thread producer_;
    AAudioStream* stream_ = nullptr;
    AudioFifo<> fifo_;
    std::atomic<bool> run_{false}, panic_{false};
    std::atomic<int> state_{0}, error_{0};
    std::atomic<float> gain_{0.5f};
    float callbackGain_ = 0.0f;
    std::array<uint8_t, 65536> midi_{};
    uint32_t midiRead_ = 0, midiWrite_ = 0;
    std::atomic<uint64_t> midiBytes_{0}, dropped_{0};
    std::atomic<uint64_t> workNs_{0}, workFrames_{0}, peakNs_{0}, underruns_{0}, missing_{0}, rendered_{0};
    std::atomic<int> xruns_{0}, hardwareFrames_{0}, burst_{0}, device_{0};
    std::atomic<uint32_t> target_{512};
    std::string message_ = "Motor detenido", lcd_;

    void text(const std::string& message) {
        std::lock_guard<std::mutex> lock(textMutex_); message_ = message;
    }
    static aaudio_data_callback_result_t audioCallback(AAudioStream*, void* self, void* data, int32_t n) {
        return static_cast<Engine*>(self)->audio(static_cast<float*>(data), n);
    }
    static void errorCallback(AAudioStream*, void* self, aaudio_result_t code) {
        // AAudio must not be closed, joined or reconfigured on this callback thread.
        static_cast<Engine*>(self)->error_.store(code, std::memory_order_release);
    }
    aaudio_data_callback_result_t audio(float* data, int32_t frames) {
        if (frames <= 0) return AAUDIO_CALLBACK_RESULT_CONTINUE;
        const uint32_t n = run_.load(std::memory_order_relaxed) ? fifo_.pop(data, uint32_t(frames)) : 0;
        std::fill(data + n * 2, data + uint32_t(frames) * 2, 0.0f);
        if (n < uint32_t(frames) && state_.load(std::memory_order_relaxed) == 2) {
            underruns_.fetch_add(1, std::memory_order_relaxed);
            missing_.fetch_add(uint32_t(frames) - n, std::memory_order_relaxed);
        }
        const float wanted = gain_.load(std::memory_order_relaxed);
        const float step = (wanted - callbackGain_) / float(frames);
        for (int32_t i = 0; i < frames; ++i) {
            callbackGain_ += step;
            for (int ch = 0; ch < 2; ++ch) {
                const float s = data[i * 2 + ch] * callbackGain_;
                data[i * 2 + ch] = std::isfinite(s) ? std::clamp(s, -1.0f, 1.0f) : 0.0f;
            }
        }
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }
    void sendPanic() {
        // End an incomplete SysEx, release sustain and silence every channel.
        const uint8_t end = 0xf7; runner_->sendMIDIData(&end, 1);
        for (uint8_t ch = 0; ch < 16; ++ch) {
            for (uint8_t cc : {uint8_t(64), uint8_t(123), uint8_t(120)}) {
                const uint8_t b[] = {uint8_t(0xb0 | ch), cc, 0}; runner_->sendMIDIData(b, 3);
            }
        }
    }
    void produce() noexcept {
        // Best effort: no root or privileged scheduling is required.
        (void)setpriority(PRIO_PROCESS, 0, -16);
        std::array<float, kBlock> left{}, right{};
        std::array<uint8_t, 2048> messages{};
        uint64_t frames = 0;
        try {
            while (run_.load(std::memory_order_acquire)) {
                if (error_.load()) {
                    text(std::string("Error de salida: ") + AAudio_convertResultToText(error_.load()) + ". Detener y volver a arrancar.");
                    state_.store(3); break;
                }
                if (fifo_.size() >= target_.load()) {
                    std::this_thread::sleep_for(std::chrono::microseconds(250)); continue;
                }
                const auto before = Clock::now();
                if (panic_.exchange(false)) sendPanic();
                uint32_t count = 0;
                {
                    std::lock_guard<std::mutex> lock(midiMutex_);
                    count = std::min<uint32_t>(midiWrite_ - midiRead_, messages.size());
                    for (uint32_t i = 0; i < count; ++i) messages[i] = midi_[(midiRead_ + i) & 65535];
                    midiRead_ += count;
                }
                if (count) runner_->sendMIDIData(messages.data(), count);
                runner_->render(left.data(), right.data(), kBlock);
                const auto ns = uint64_t(std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - before).count());
                frames += kBlock; rendered_.store(frames, std::memory_order_relaxed);
                if (frames >= kWarmup) {
                    if (state_.load() == 1) { state_.store(2); text("Motor activo. Verificar el sonido en la tablet."); }
                    workNs_.fetch_add(ns, std::memory_order_relaxed);
                    workFrames_.fetch_add(kBlock, std::memory_order_relaxed);
                    uint64_t p = peakNs_.load();
                    while (p < ns && !peakNs_.compare_exchange_weak(p, ns)) {}
                }
                if (!fifo_.push(left.data(), right.data(), kBlock)) throw std::runtime_error("FIFO de audio llena");
                if ((frames / kBlock) % 94 == 0) {
                    std::string display = runner_->getLCDContent();
                    for (char& c : display) if ((static_cast<unsigned char>(c) < 32 && c != '\n') || static_cast<unsigned char>(c) > 126) c = ' ';
                    { std::lock_guard<std::mutex> lock(textMutex_); lcd_ = display; }
                    xruns_.store(AAudioStream_getXRunCount(stream_));
                }
            }
        } catch (const std::exception& e) {
            text(std::string("Error del motor: ") + e.what()); state_.store(3);
        } catch (...) { text("Error nativo no identificado"); state_.store(3); }
        run_.store(false, std::memory_order_release);
    }
    void stopLocked() {
        run_.store(false, std::memory_order_release);
        if (producer_.joinable()) producer_.join();
        if (stream_) {
            AAudioStream_requestStop(stream_);
            AAudioStream_close(stream_); stream_ = nullptr;
        }
        if (runner_) { runner_->stop(); runner_.reset(); }
        fifo_.reset();
        { std::lock_guard<std::mutex> lock(midiMutex_); midiRead_ = midiWrite_ = 0; }
        state_.store(0); text("Motor detenido");
    }
public:
    std::string start(const std::string& directory, int queueFrames, int deviceId, bool dspThread) {
        std::lock_guard<std::mutex> lock(lifecycle_);
        stopLocked();
        try {
            if (queueFrames != 256 && queueFrames != 512 && queueFrames != 1024) throw std::runtime_error("Buffer invalido");
            if (!ms2k::hasRoms(directory)) throw std::runtime_error("Importar primero el ZIP con flash.bin y boot-362.bin");
            if (std::filesystem::file_size(std::filesystem::path(directory) / "flash.bin") != 1048576) throw std::runtime_error("flash.bin debe medir 1048576 bytes");
            const auto boot = std::filesystem::path(directory) / ms2k::findBootRom(directory);
            if (std::filesystem::file_size(boot) != 768) throw std::runtime_error("ROM del DSP: se esperan 768 bytes");
            if (chdir(directory.c_str()) != 0) throw std::runtime_error("No se puede abrir la carpeta privada de ROMs");
            setenv("MS2K_MODEL", "R", 1);
            MS2000::Ms2kConfig cfg;
            cfg.romPath = "flash.bin"; cfg.sampleRate = 48000; cfg.quietBoot = true;
            cfg.audio.enabled = false; cfg.useMmcss = false; cfg.enableMidiSmokeTest = false;
            cfg.dspThread = dspThread; cfg.flashStateSave = true; cfg.flashStateLoad = true;
            MS2000::Ms2kGuiHooks hooks;
            hooks.logFn = [](const char* s) { __android_log_write(ANDROID_LOG_DEBUG, "RiMS2000R", s); };
            runner_ = std::make_unique<MS2000::Ms2kRunner>(cfg, hooks);
            if (!runner_->init() || !runner_->startManual()) throw std::runtime_error("El emulador rechazo el arranque");

            AAudioStreamBuilder* builder = nullptr;
            auto result = AAudio_createStreamBuilder(&builder);
            if (result != AAUDIO_OK) throw std::runtime_error(AAudio_convertResultToText(result));
            AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
            AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
            AAudioStreamBuilder_setSampleRate(builder, 48000);
            AAudioStreamBuilder_setChannelCount(builder, 2);
            AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
            AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
            if (deviceId > 0) AAudioStreamBuilder_setDeviceId(builder, deviceId);
            AAudioStreamBuilder_setBufferCapacityInFrames(builder, std::max(2048, queueFrames * 4));
            AAudioStreamBuilder_setDataCallback(builder, audioCallback, this);
            AAudioStreamBuilder_setErrorCallback(builder, errorCallback, this);
            result = AAudioStreamBuilder_openStream(builder, &stream_);
            AAudioStreamBuilder_delete(builder);
            if (result != AAUDIO_OK) throw std::runtime_error(AAudio_convertResultToText(result));
            if (AAudioStream_getSampleRate(stream_) != 48000 || AAudioStream_getChannelCount(stream_) != 2 || AAudioStream_getFormat(stream_) != AAUDIO_FORMAT_PCM_FLOAT)
                throw std::runtime_error("La salida no acepto 48 kHz estereo float; elegir otra salida");
            burst_.store(AAudioStream_getFramesPerBurst(stream_));
            const int actual = AAudioStream_setBufferSizeInFrames(stream_, std::max(queueFrames, burst_.load() * 2));
            if (actual < 0) throw std::runtime_error(AAudio_convertResultToText(actual));
            hardwareFrames_.store(actual); device_.store(AAudioStream_getDeviceId(stream_));
            target_.store(uint32_t(std::max(queueFrames, burst_.load() * 2)));
            if (target_.load() > 8192) throw std::runtime_error("La salida requiere un buffer demasiado grande");
            callbackGain_ = 0; error_.store(0); panic_.store(false);
            midiBytes_.store(0); dropped_.store(0); rendered_.store(0); resetStats();
            { std::lock_guard<std::mutex> textLock(textMutex_); lcd_.clear(); }
            state_.store(1); text("Arrancando el firmware... las notas se habilitan despues de 4 s emulados.");
            run_.store(true, std::memory_order_release);
            producer_ = std::thread(&Engine::produce, this);
            result = AAudioStream_requestStart(stream_);
            if (result != AAUDIO_OK) throw std::runtime_error(AAudio_convertResultToText(result));
            return {};
        } catch (const std::exception& e) {
            const std::string reason = e.what(); stopLocked(); text(reason); state_.store(3); return reason;
        } catch (...) { stopLocked(); text("Error iniciando el motor"); state_.store(3); return "Error iniciando el motor"; }
    }
    void stop() { std::lock_guard<std::mutex> lock(lifecycle_); stopLocked(); }
    int state() const { return state_.load(std::memory_order_acquire); }
    void setVolume(float v) { if (std::isfinite(v)) gain_.store(std::clamp(v, 0.0f, 1.0f)); }
    void panic() { panic_.store(true); }
    void addMidi(const uint8_t* bytes, uint32_t count) {
        if (state() != 2 || count == 0) return;
        std::lock_guard<std::mutex> lock(midiMutex_);
        if (count > 65536 - (midiWrite_ - midiRead_)) {
            dropped_.fetch_add(count); midiRead_ = midiWrite_; panic_.store(true); return;
        }
        for (uint32_t i = 0; i < count; ++i) midi_[(midiWrite_ + i) & 65535] = bytes[i];
        midiWrite_ += count; midiBytes_.fetch_add(count);
    }
    void resetStats() {
        workNs_.store(0); workFrames_.store(0); peakNs_.store(0);
        underruns_.store(0); missing_.store(0); xruns_.store(0);
    }
    std::string stats() {
        const double frames = double(workFrames_.load());
        const double mean = frames > 0 ? double(workNs_.load()) * 48000.0 / frames / 1e7 : 0.0;
        const double peak = double(peakNs_.load()) * 48000.0 / kBlock / 1e7;
        char b[700];
        std::snprintf(b, sizeof b,
            "48 kHz / ARM64 | Cola %u + AAudio %d muestras | burst %d | salida ID %d\n"
            "Carga render: %.1f%% media / %.1f%% pico (100%% = plazo agotado; no CPU total)\n"
            "Underruns de cola: %llu | muestras faltantes: %llu | AAudio xruns: %d\n"
            "Tiempo emulado: %.1f s | MIDI: %llu bytes | descartados: %llu\n",
            target_.load(), hardwareFrames_.load(), burst_.load(), device_.load(), mean, peak,
            (unsigned long long)underruns_.load(), (unsigned long long)missing_.load(), xruns_.load(),
            double(rendered_.load()) / 48000.0, (unsigned long long)midiBytes_.load(), (unsigned long long)dropped_.load());
        std::lock_guard<std::mutex> lock(textMutex_);
        return message_ + "\n\nLCD: " + lcd_ + "\n\n" + b;
    }
};
Engine engine;
}

extern "C" {
JNIEXPORT jstring JNICALL Java_org_rinoize_rims2000r_NativeBridge_start(JNIEnv* env, jclass, jstring dir, jint frames, jint device, jboolean dsp) {
    if (!dir) return env->NewStringUTF("Directorio nulo");
    const char* p = env->GetStringUTFChars(dir, nullptr);
    if (!p) return nullptr;
    const std::string path(p); env->ReleaseStringUTFChars(dir, p);
    const auto result = engine.start(path, frames, device, dsp == JNI_TRUE);
    return env->NewStringUTF(result.c_str());
}
JNIEXPORT void JNICALL Java_org_rinoize_rims2000r_NativeBridge_stop(JNIEnv*, jclass) { engine.stop(); }
JNIEXPORT jint JNICALL Java_org_rinoize_rims2000r_NativeBridge_state(JNIEnv*, jclass) { return engine.state(); }
JNIEXPORT jstring JNICALL Java_org_rinoize_rims2000r_NativeBridge_stats(JNIEnv* env, jclass) {
    const auto s = engine.stats(); return env->NewStringUTF(s.c_str());
}
JNIEXPORT void JNICALL Java_org_rinoize_rims2000r_NativeBridge_volume(JNIEnv*, jclass, jfloat g) { engine.setVolume(g); }
JNIEXPORT void JNICALL Java_org_rinoize_rims2000r_NativeBridge_panic(JNIEnv*, jclass) { engine.panic(); }
JNIEXPORT void JNICALL Java_org_rinoize_rims2000r_NativeBridge_resetStats(JNIEnv*, jclass) { engine.resetStats(); }
JNIEXPORT void JNICALL Java_org_rinoize_rims2000r_NativeBridge_midi(JNIEnv* env, jclass, jbyteArray bytes, jint offset, jint count) {
    if (!bytes || offset < 0 || count <= 0 || count > 65536) return;
    const jsize len = env->GetArrayLength(bytes);
    if (offset > len || count > len - offset) return;
    jbyte* p = env->GetByteArrayElements(bytes, nullptr);
    if (!p) return;
    engine.addMidi(reinterpret_cast<uint8_t*>(p + offset), uint32_t(count));
    env->ReleaseByteArrayElements(bytes, p, JNI_ABORT);
}
}
