package com.padilla.backend.service.storage;

import com.padilla.backend.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * PRO-046: archivos en disco bajo app.storage.path (en Docker, un volumen montado en /app/files).
 * La clave ya trae la agencia como primer segmento, asi cada inmobiliaria tiene su carpeta.
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${app.storage.path:./data/files}") String path) {
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream content, long size) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    @Override
    public InputStream load(String key) {
        Path file = resolve(key);
        if (!Files.exists(file)) {
            throw new NotFoundException("El archivo no esta disponible");
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo {}: {}", key, e.getMessage());
        }
    }

    /** Evita salir de la carpeta raiz (path traversal). */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Clave de archivo invalida");
        }
        return p;
    }
}
