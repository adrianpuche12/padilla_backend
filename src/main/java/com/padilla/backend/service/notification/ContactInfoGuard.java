package com.padilla.backend.service.notification;

import com.padilla.backend.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * PRO-064 (ARQ 4 Mod.4, regla de oro): los actores no pueden compartir datos de contacto por la
 * plataforma; toda comunicacion la intermedia la inmobiliaria. Se rechazan mensajes con telefonos,
 * emails o links de mensajeria.
 */
public final class ContactInfoGuard {

    // 8 o mas digitos, con espacios, guiones, puntos, parentesis o + intercalados (ej. +54 9 341 555-1234)
    private static final Pattern PHONE = Pattern.compile("(?<![\\w#])\\+?\\(?\\d[\\d\\s().-]{6,}\\d(?![\\w])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern MESSENGER = Pattern.compile("(?i)(wa\\.me|whatsapp|t\\.me/|telegram\\.me|instagram\\.com/)");

    private ContactInfoGuard() {
    }

    public static boolean containsContactInfo(String text) {
        if (text == null) return false;
        if (EMAIL.matcher(text).find() || MESSENGER.matcher(text).find()) return true;
        var m = PHONE.matcher(text);
        while (m.find()) {
            long digitos = m.group().chars().filter(Character::isDigit).count();
            if (digitos >= 8) return true;
        }
        return false;
    }

    public static void requireNoContactInfo(String text) {
        if (containsContactInfo(text)) {
            throw new BadRequestException("Por seguridad no se pueden compartir teléfonos, emails ni links de mensajería. "
                    + "La inmobiliaria coordina todo por la plataforma.");
        }
    }
}
