package org.rinoize.rims2000r;

/** JNI entry points. All engine start/stop calls are serialized by the activity's executor. */
final class NativeBridge {
    static { System.loadLibrary("rims2000r_native"); }
    private NativeBridge() {}
    static native String start(String directory, int queueFrames, int audioDeviceId, boolean dspThread);
    static native void stop();
    static native int state(); // 0 stopped, 1 booting, 2 running, 3 error
    static native String stats();
    static native void midi(byte[] bytes, int offset, int count);
    static native void panic();
    static native void volume(float gain);
    static native void resetStats();
}
