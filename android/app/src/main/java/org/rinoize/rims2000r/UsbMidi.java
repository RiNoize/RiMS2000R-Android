package org.rinoize.rims2000r;

import android.app.*;
import android.content.*;
import android.media.midi.*;
import android.os.*;
import android.widget.Toast;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Uses Android's MIDI service; the USB keyboard's OUTPUT port is this synth's input. */
final class UsbMidi {
    private final Activity activity;
    private final MidiManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MidiDevice device;
    private MidiOutputPort port;
    private MidiReceiver receiver;
    private int generation;
    private boolean destroyed;
    private volatile String status = "MIDI USB desconectado", last = "Sin mensajes";
    private final AtomicLong received = new AtomicLong();
    private final MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (device != null && device.getInfo().getId() == info.getId()) disconnect();
        }
    };
    UsbMidi(Activity activity) {
        this.activity = activity;
        manager = (MidiManager)activity.getSystemService(Context.MIDI_SERVICE);
        if (manager != null) manager.registerDeviceCallback(callback, handler);
        else status = "Este Android no ofrece servicio MIDI";
    }
    void choose() {
        if (manager == null) return;
        List<MidiDeviceInfo> devices = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (MidiDeviceInfo info : manager.getDevices()) {
            if (info.getType() != MidiDeviceInfo.TYPE_USB) continue;
            for (MidiDeviceInfo.PortInfo p : info.getPorts()) {
                if (p.getType() != MidiDeviceInfo.PortInfo.TYPE_OUTPUT) continue;
                devices.add(info); ports.add(p.getPortNumber());
                labels.add(name(info) + " · " + (p.getName().isEmpty() ? "Puerto " + (p.getPortNumber() + 1) : p.getName()));
            }
        }
        if (labels.isEmpty()) {
            Toast.makeText(activity, "Conectá un teclado MIDI USB al hub y volvé a pulsar MIDI USB.", Toast.LENGTH_LONG).show(); return;
        }
        labels.add("Desconectar MIDI");
        new AlertDialog.Builder(activity).setTitle("Entrada MIDI del sintetizador")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                if (which == devices.size()) disconnect();
                else connect(devices.get(which), ports.get(which));
            }).show();
    }
    private static String name(MidiDeviceInfo info) {
        String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        return name == null || name.isEmpty() ? "Dispositivo USB " + info.getId() : name;
    }
    private void connect(MidiDeviceInfo info, int portNumber) {
        disconnect();
        int token = generation;
        status = "Abriendo " + name(info);
        manager.openDevice(info, opened -> {
            if (opened == null) { if (token == generation) status = "No se pudo abrir " + name(info); return; }
            if (destroyed || token != generation) { try { opened.close(); } catch (IOException ignored) {} return; }
            MidiOutputPort output = opened.openOutputPort(portNumber);
            if (output == null) { try { opened.close(); } catch (IOException ignored) {} status = "Puerto ocupado o no disponible"; return; }
            MidiFramer framer = new MidiFramer((bytes, length) -> {
                NativeBridge.midi(bytes, 0, length);
                received.incrementAndGet();
                if ((bytes[0] & 255) < 0xf8) {
                    StringBuilder s = new StringBuilder();
                    for (int i = 0; i < Math.min(length, 12); i++) s.append(String.format(Locale.ROOT, "%02X ", bytes[i] & 255));
                    last = s.toString();
                }
            });
            receiver = new MidiReceiver() {
                @Override public void onSend(byte[] data, int offset, int count, long timestamp) {
                    // Alpha quantizes ingress to the next 128-frame engine block, not sample-accurate timestamps.
                    framer.accept(data, offset, count);
                }
            };
            device = opened; port = output; output.connect(receiver);
            status = "MIDI: " + name(info) + " · puerto " + (portNumber + 1);
        }, handler);
    }
    void disconnect() {
        generation++;
        NativeBridge.panic();
        if (port != null) {
            if (receiver != null) port.disconnect(receiver);
            try { port.close(); } catch (IOException ignored) {}
        }
        if (device != null) { try { device.close(); } catch (IOException ignored) {} }
        port = null; device = null; receiver = null; status = "MIDI USB desconectado";
    }
    String description() { return status + "\nRX " + received.get() + " mensajes · " + last; }
    void close() {
        destroyed = true; disconnect();
        if (manager != null) manager.unregisterDeviceCallback(callback);
    }
}
