package org.rinoize.rims2000r;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class CoreTests {
    static void check(boolean b, String message) { if (!b) throw new AssertionError(message); }
    static byte[] zip(String flashName, int flashSize, boolean boot) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry(flashName)); z.write(new byte[flashSize]); z.closeEntry();
            if (boot) { z.putNextEntry(new ZipEntry("full FW/boot-362.ms2000.bin")); z.write(new byte[768]); z.closeEntry(); }
        }
        return b.toByteArray();
    }
    static void rejects(byte[] zip, File root) throws Exception {
        boolean rejected = false;
        try { FirmwareImporter.importZip(new ByteArrayInputStream(zip), root); } catch (IOException e) { rejected = true; }
        check(rejected, "invalid ZIP accepted"); check(FirmwareImporter.ready(root), "previous ROMs lost");
    }
    public static void main(String[] args) throws Exception {
        List<byte[]> sent = new ArrayList<>();
        MidiFramer f = new MidiFramer((b,n) -> sent.add(Arrays.copyOf(b,n)));
        f.accept(new byte[]{(byte)0x90,60}, 0, 2); check(sent.isEmpty(), "partial MIDI sent");
        f.accept(new byte[]{100,61,110}, 0, 3);
        check(Arrays.equals(sent.get(0),new byte[]{(byte)0x90,60,100}), "fragmented note");
        check(Arrays.equals(sent.get(1),new byte[]{(byte)0x90,61,110}), "running status");
        f.accept(new byte[]{(byte)0xb0,64,127,(byte)0xe0,0,64},0,6);
        check(sent.size()==4, "sustain and pitch bend");
        f.accept(new byte[]{(byte)0xf0,0x42,(byte)0xf8,1,(byte)0xf7},0,5);
        check(Arrays.equals(sent.get(4),new byte[]{(byte)0xf8}), "realtime during SysEx");
        check(Arrays.equals(sent.get(5),new byte[]{(byte)0xf0,0x42,1,(byte)0xf7}), "SysEx framing");
        File root = Files.createTempDirectory("rims-import-test").resolve("ms2000").toFile();
        FirmwareImporter.importZip(new ByteArrayInputStream(zip("flash.bin",1048576,true)),root);
        check(FirmwareImporter.ready(root), "valid import failed");
        rejects(zip("flash.bin",12,true),root);
        rejects(zip("../flash.bin",1048576,true),root);
        rejects(zip("flash.bin",1048576,false),root);
        System.out.println("PASS: MIDI fragmentation/running status/CC64/pitch bend/realtime/SysEx; ZIP validation and rollback");
    }
}
