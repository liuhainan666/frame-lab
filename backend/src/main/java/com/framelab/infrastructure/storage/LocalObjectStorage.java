package com.framelab.infrastructure.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import com.framelab.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;

    public LocalObjectStorage(StorageProperties properties) {
        this.root = properties.getRoot().toAbsolutePath().normalize();
    }

    @Override
    public StoredObject writeMaterial(long userId, String assetId, byte[] bytes, String extension) {
        String storageKey = "materials/" + userId + "/" + assetId + "." + extension;
        Path target = resolve(storageKey);
        Path temp = resolve("tmp/asset-" + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.createDirectories(temp.getParent());
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            moveAtomically(temp, target);
            return new StoredObject(storageKey);
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw new StorageException("Unable to persist material", ex);
        }
    }

    @Override
    public StoredObject writeResult(long userId, String taskId, byte[] bytes) {
        String storageKey = "results/" + userId + "/" + taskId + ".jpg";
        Path target = resolve(storageKey);
        Path temp = resolve("tmp/task-" + taskId + "/result-" + UUID.randomUUID() + ".jpg");
        try {
            Files.createDirectories(target.getParent());
            Files.createDirectories(temp.getParent());
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            moveAtomically(temp, target);
            return new StoredObject(storageKey);
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw new StorageException("Unable to persist result", ex);
        }
    }

    @Override
    public Resource open(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new StorageException("Stored object is missing");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            throw new StorageException("Unable to delete stored object", ex);
        }
    }

    @Override
    public void deleteTaskTemp(String taskId) {
        Path directory = resolve("tmp/task-" + taskId);
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException ex) {
            throw new StorageException("Unable to clean task temporary files", ex);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new StorageException("Invalid storage key");
        }
        return path;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary cleanup is best effort; the original failure is more useful.
        }
    }
}
