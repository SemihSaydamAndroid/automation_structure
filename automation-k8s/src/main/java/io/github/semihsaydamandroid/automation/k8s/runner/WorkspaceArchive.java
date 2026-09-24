package io.github.semihsaydamandroid.automation.k8s.runner;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/** tar.gz packing of the local project (minus build output and VCS data) and unpacking of results. */
public final class WorkspaceArchive {

    private WorkspaceArchive() {
    }

    /** Packs {@code root} into {@code target}; directories named in {@code excludes} are skipped at any depth. */
    public static long pack(Path root, Path target, Set<String> excludes) throws IOException {
        long[] count = {0};
        try (OutputStream file = Files.newOutputStream(target);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(
                     new GzipCompressorOutputStream(new BufferedOutputStream(file)))) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && excludes.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile() || excludes.contains(file.getFileName().toString())) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = root.relativize(file).toString().replace('\\', '/');
                    TarArchiveEntry entry = new TarArchiveEntry(file.toFile(), name);
                    entry.setMode(Files.isExecutable(file) ? 0100755 : 0100644);
                    tar.putArchiveEntry(entry);
                    Files.copy(file, tar);
                    tar.closeArchiveEntry();
                    count[0]++;
                    return FileVisitResult.CONTINUE;
                }
            });
            tar.finish();
        }
        return count[0];
    }

    /** Extracts a tar.gz stream below {@code destination}, refusing entries that escape it. */
    public static long unpack(InputStream archive, Path destination) throws IOException {
        long count = 0;
        Path root = destination.toAbsolutePath().normalize();
        try (TarArchiveInputStream tar = new TarArchiveInputStream(
                new GzipCompressorInputStream(new BufferedInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new IOException("Archive entry escapes destination: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(tar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        }
        return count;
    }
}
