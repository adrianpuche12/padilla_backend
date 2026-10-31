package com.padilla.backend.service.storage;

import org.springframework.core.io.AbstractResource;

import java.io.InputStream;

/**
 * PRO-120: archivo guardado que se puede abrir más de una vez. Spring solo atiende pedidos con
 * Range (206 Partial Content) sobre recursos reabribles, no sobre un InputStreamResource; sin esto
 * la app mobile no puede reproducir un video sin bajarlo entero.
 */
public class StoredResource extends AbstractResource {

    private final StorageService storage;
    private final String key;
    private final String filename;
    private final long size;

    public StoredResource(StorageService storage, String key, String filename, long size) {
        this.storage = storage;
        this.key = key;
        this.filename = filename;
        this.size = size;
    }

    @Override
    public InputStream getInputStream() {
        return storage.load(key);
    }

    @Override
    public long contentLength() {
        return size;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public String getDescription() {
        return "Adjunto " + key;
    }
}
