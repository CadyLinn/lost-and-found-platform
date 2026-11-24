package com.lostfound.backend.photo;

import java.util.Optional;

/**
 * Where photo files are kept. Today: {@link FileSystemPhotoStorage} (local disk / Docker volume).
 * For a cloud deployment, add an S3 implementation of this interface; nothing else needs to change.
 */
public interface PhotoStorage {

    /** Saves a file under a name like "{uuid}.jpg". Overwrites if it exists. */
    void save(String fileName, byte[] content);

    Optional<byte[]> load(String fileName);

    /** Deletes the file if it exists. */
    void delete(String fileName);
}
