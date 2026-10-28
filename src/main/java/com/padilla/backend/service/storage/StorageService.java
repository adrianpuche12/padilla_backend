package com.padilla.backend.service.storage;

import java.io.InputStream;

/**
 * PRO-046 (decision D10): almacenamiento de archivos. Hoy en disco/volumen (LocalStorageService);
 * la interfaz permite cambiar a S3/MinIO sin tocar el resto.
 */
public interface StorageService {

    void store(String key, InputStream content, long size);

    InputStream load(String key);

    void delete(String key);
}
