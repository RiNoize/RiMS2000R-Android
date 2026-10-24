package org.rinoize.rims2000r;

/** Converts arbitrary MIDI 1.0 byte chunks into complete messages before merging with UI notes. */
final class MidiFramer {
    interface Sink { void send(byte[] data, int length); }
    private final Sink sink;
    private final byte[] message = new byte[65536];
    private final byte[] realtime = new byte[1];
    private int used, needed, running;
    private boolean sysex;
    long discarded;
    MidiFramer(Sink sink) { this.sink = sink; }
    synchronized void accept(byte[] data, int offset, int count) {
        if (offset < 0 || count < 0 || offset > data.length || count > data.length - offset) return;
        for (int i = offset; i < offset + count; ++i) {
            int b = data[i] & 255;
            if (b >= 0xf8) { realtime[0] = (byte)b; sink.send(realtime, 1); continue; }
            if (sysex) {
                if (b == 0xf7) { message[used++] = (byte)b; sink.send(message, used); sysex = false; used = 0; continue; }
                if (b < 0x80) {
                    if (used < message.length - 1) message[used++] = (byte)b;
                    else { sysex = false; used = 0; discarded++; }
                    continue;
                }
                sysex = false; used = 0; discarded++;
            }
            if (b >= 0x80) {
                used = 0; running = b < 0xf0 ? b : 0;
                if (b == 0xf0) { sysex = true; message[used++] = (byte)b; continue; }
                needed = length(b); message[used++] = (byte)b;
                if (needed == 1) { sink.send(message, 1); used = 0; }
                continue;
            }
            if (used == 0) {
                if (running == 0) continue;
                message[used++] = (byte)running; needed = length(running);
            }
            message[used++] = (byte)b;
            if (used == needed) { sink.send(message, used); used = 0; }
        }
    }
    private static int length(int status) {
        if (status < 0xf0) return (status & 0xe0) == 0xc0 ? 2 : 3;
        if (status == 0xf1 || status == 0xf3) return 2;
        if (status == 0xf2) return 3;
        return 1;
    }
}
