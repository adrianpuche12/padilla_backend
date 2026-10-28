package com.padilla.backend.security;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Politica de contrasenas de Propero (features/01-notificaciones: minimo 8, 1 mayuscula, 1 numero,
 * 1 simbolo). PRO-031: unica implementacion; antes estaba solo dentro de AuthController.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    private static final Pattern HAS_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_NUMBER = Pattern.compile(".*[0-9].*");
    private static final Pattern HAS_SYMBOL = Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

    private PasswordPolicy() {
    }

    /** Mensaje del primer requisito que no se cumple, o vacio si la contrasena es valida. */
    public static Optional<String> violation(String password) {
        if (password == null || password.length() < MIN_LENGTH)
            return Optional.of("La contraseña debe tener al menos " + MIN_LENGTH + " caracteres");
        if (!HAS_UPPERCASE.matcher(password).matches())
            return Optional.of("La contraseña debe contener al menos 1 letra mayúscula");
        if (!HAS_NUMBER.matcher(password).matches())
            return Optional.of("La contraseña debe contener al menos 1 número");
        if (!HAS_SYMBOL.matcher(password).matches())
            return Optional.of("La contraseña debe contener al menos 1 símbolo (!@#$%^&*)");
        return Optional.empty();
    }
}
