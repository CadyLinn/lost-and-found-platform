package com.lostfound.backend.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stores photos as files in one directory (app.storage.dir). */
@Component
public class FileSystemPhotoStorage implements PhotoStorage {

    /** Only names we generate ourselves: a UUID, an optional "_thumb", ".jpg". Blocks "../" tricks. */
    private static final Pattern SAFE_NAME = Pattern.compile("[0-9a-f-]{36}(_thumb)?\\.jpg");

    private final Path directory;

    public FileSystemPhotoStorage(@Value("${app.storage.dir}") String directory) throws IOException {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
    }

    @Override
    public void save(String fileName, byte[] content) {
        Path target = resolve(fileName);
        try {
            // Write to a temporary file, then rename: readers never see a half-written photo.
            Path temp = Files.createTempFile(directory, ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save photo " + fileName, e);
        }
    }

    @Override
    public Optional<byte[]> load(String fileName) {
        try {
            return Optional.of(Files.readAllBytes(resolve(fileName)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read photo " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete photo " + fileName, e);
        }
    }

    private Path resolve(String fileName) {
        if (!SAFE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Invalid photo file name");
        }
        return directory.resolve(fileName);
    }
}
