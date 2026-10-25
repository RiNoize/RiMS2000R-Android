package org.rinoize.rims2000r;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Bounded, allow-listed ZIP importer. Never writes a path taken from an archive. */
final class FirmwareImporter {
    private static final Map<String, Integer> SIZES = Map.of(
        "flash.bin", 1048576, "boot-362.bin", 768,
        "boot-362.ms2000.bin", 768, "hd44780_a00.bin", 4096);
    private FirmwareImporter() {}
    static boolean ready(File root) {
        return new File(root, "flash.bin").length() == 1048576 &&
            (new File(root, "boot-362.ms2000.bin").length() == 768 || new File(root, "boot-362.bin").length() == 768);
    }
    static void recover(File root) throws IOException {
        File previous = new File(root.getPath() + ".previous");
        if (!root.exists() && previous.isDirectory() && !previous.renameTo(root))
            throw new IOException("No se pudo recuperar la importación anterior");
    }
    static void importZip(InputStream input, File root) throws IOException {
        recover(root);
        File staging = new File(root.getPath() + ".import");
        File previous = new File(root.getPath() + ".previous");
        deleteTree(staging);
        if (!staging.mkdirs()) throw new IOException("No se pudo crear la carpeta temporal");
        try {
            Set<String> seen = new HashSet<>();
            long total = 0; int entries = 0;
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 128) throw new IOException("El ZIP contiene demasiadas entradas");
                    String path = entry.getName().replace('\\', '/');
                    if (path.startsWith("/") || Arrays.asList(path.split("/")).contains(".."))
                        throw new IOException("El ZIP contiene una ruta no permitida");
                    String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    Integer expected = SIZES.get(name);
                    if (expected != null && !seen.add(name)) throw new IOException("ROM duplicada: " + name);
                    long size = 0;
                    OutputStream output = expected == null ? null : new FileOutputStream(new File(staging, name));
                    try {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            size += n; total += n;
                            if (total > 8 * 1024 * 1024 || (expected != null && size > expected))
                                throw new IOException("Tamaño no válido: " + name);
                            if (output != null) output.write(buffer, 0, n);
                        }
                    } finally { if (output != null) output.close(); }
                    if (expected != null && size != expected) throw new IOException(name + ": se esperan " + expected + " bytes");
                    zip.closeEntry();
                }
            }
            if (!ready(staging)) throw new IOException("Faltan flash.bin (1 MB) y/o boot-362.bin (768 bytes). Seleccioná un ZIP, no un RAR.");
            // Reimporting the same flash preserves the emulator's separately written state.
            if (ready(root) && Arrays.equals(hash(new File(root, "flash.bin")), hash(new File(staging, "flash.bin")))) {
                for (String name : new String[]{"ms2000_flash_state.bin", "ms2000_flash_state.sectors"}) {
                    File f = new File(root, name);
                    if (f.isFile() && f.length() <= 1048576)
                        Files.copy(f.toPath(), new File(staging, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            deleteTree(previous);
            boolean hadPrevious = root.exists();
            if (hadPrevious && !root.renameTo(previous)) throw new IOException("No se pudo respaldar la carpeta anterior");
            if (!staging.renameTo(root)) {
                if (hadPrevious && !previous.renameTo(root)) throw new IOException("Importación interrumpida; se conserva el respaldo .previous");
                throw new IOException("No se pudo completar la importación");
            }
            deleteTree(previous);
        } finally { deleteTree(staging); }
    }
    private static byte[] hash(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(file)) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) digest.update(b, 0, n);
            }
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new IOException("No se pudo eliminar " + file.getName());
    }
}
