package com.padilla.backend.service.email;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.from-address}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Envía el email de bienvenida al nuevo usuario con sus credenciales temporales.
     * Best-effort: si falla, loguea el error pero NO lanza excepción
     * para no revertir la creación del usuario.
     */
    public void sendWelcomeEmail(String toEmail, String toName, String temporaryPassword) {
        if (!isConfigured()) {
            log.warn("[EmailService] API key de Resend no configurada. Email a {} no enviado.", toEmail);
            return;
        }
        try {
            Resend resend = new Resend(apiKey);

            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject("Bienvenido a Padilla — Tus credenciales de acceso")
                    .html(buildWelcomeEmailHtml(toName, toEmail, temporaryPassword))
                    .build();

            resend.emails().send(email);
            log.info("[EmailService] Email de bienvenida enviado a {}", toEmail);

        } catch (Exception e) {
            log.error("[EmailService] Error al enviar email de bienvenida a {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Reenvía el email de acceso cuando el anterior expiró.
     * Mismo comportamiento best-effort que sendWelcomeEmail.
     */
    public void sendResendAccessEmail(String toEmail, String toName, String temporaryPassword) {
        if (!isConfigured()) {
            log.warn("[EmailService] API key de Resend no configurada. Email a {} no enviado.", toEmail);
            return;
        }
        try {
            Resend resend = new Resend(apiKey);

            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject("Padilla — Nuevo acceso solicitado")
                    .html(buildResendAccessEmailHtml(toName, toEmail, temporaryPassword))
                    .build();

            resend.emails().send(email);
            log.info("[EmailService] Email de reenvío de acceso enviado a {}", toEmail);

        } catch (Exception e) {
            log.error("[EmailService] Error al reenviar email de acceso a {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Envía el email de restablecimiento de contraseña (flujo "olvidé mi contraseña").
     * Best-effort: si falla, loguea el error pero NO lanza excepción.
     */
    public void sendPasswordResetEmail(String toEmail, String toName, String temporaryPassword) {
        if (!isConfigured()) {
            log.warn("[EmailService] API key de Resend no configurada. Email a {} no enviado.", toEmail);
            return;
        }
        try {
            Resend resend = new Resend(apiKey);

            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(toEmail)
                    .subject("Padilla — Restablecimiento de contraseña")
                    .html(buildPasswordResetEmailHtml(toName, toEmail, temporaryPassword))
                    .build();

            resend.emails().send(email);
            log.info("[EmailService] Email de restablecimiento de contraseña enviado a {}", toEmail);

        } catch (Exception e) {
            log.error("[EmailService] Error al enviar email de restablecimiento a {}: {}", toEmail, e.getMessage());
        }
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("re_YOUR");
    }

    // -------------------------------------------------------
    // Templates HTML
    // -------------------------------------------------------

    private String buildWelcomeEmailHtml(String name, String email, String temporaryPassword) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background-color:#f5f5f5;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f5f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background-color:#004274;padding:32px 40px;text-align:center;">
                              <h1 style="color:#ffffff;margin:0;font-size:24px;font-weight:700;letter-spacing:1px;">PADILLA</h1>
                              <p style="color:#a8c8e8;margin:6px 0 0;font-size:13px;">Gestión Inmobiliaria</p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <h2 style="color:#1a1a2e;margin:0 0 16px;font-size:20px;">Bienvenido, %s</h2>
                              <p style="color:#555;font-size:15px;line-height:1.6;margin:0 0 24px;">
                                Tu cuenta en la plataforma Padilla fue creada exitosamente.
                                A continuación encontrás tus credenciales para ingresar por primera vez.
                              </p>

                              <!-- Credentials box -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;border-radius:6px;border-left:4px solid #004274;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:20px 24px;">
                                    <p style="margin:0 0 10px;font-size:13px;color:#888;text-transform:uppercase;letter-spacing:0.5px;">Tus credenciales</p>
                                    <p style="margin:0 0 8px;font-size:15px;color:#333;">
                                      <strong>Email:</strong> %s
                                    </p>
                                    <p style="margin:0;font-size:15px;color:#333;">
                                      <strong>Contraseña temporal:</strong>
                                      <span style="font-family:monospace;background:#e2e8f0;padding:2px 8px;border-radius:4px;font-size:16px;color:#1a1a2e;">%s</span>
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- Warning -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#fff8e1;border-radius:6px;border-left:4px solid #f4272b;margin-bottom:32px;">
                                <tr>
                                  <td style="padding:16px 20px;">
                                    <p style="margin:0;font-size:14px;color:#7a5c00;line-height:1.5;">
                                      ⚠️ <strong>Este acceso vence en 24 horas.</strong><br>
                                      Ingresá al sistema y cambiá tu contraseña antes de que expire.
                                      Si no lo hacés a tiempo, deberás solicitar un nuevo acceso al administrador.
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- CTA Button -->
                              <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td align="center">
                                    <a href="%s/login"
                                       style="display:inline-block;background-color:#f4272b;color:#ffffff;text-decoration:none;padding:14px 40px;border-radius:6px;font-size:15px;font-weight:700;letter-spacing:0.5px;">
                                      INGRESAR AL SISTEMA
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Password requirements -->
                          <tr>
                            <td style="padding:0 40px 32px;">
                              <p style="color:#888;font-size:13px;margin:0 0 8px;">Al ingresar, el sistema te pedirá crear una nueva contraseña que cumpla con:</p>
                              <ul style="color:#666;font-size:13px;margin:0;padding-left:20px;line-height:1.8;">
                                <li>Mínimo 8 caracteres</li>
                                <li>Al menos 1 letra mayúscula</li>
                                <li>Al menos 1 número</li>
                                <li>Al menos 1 símbolo (!@#$%%^&*)</li>
                              </ul>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#f0f4f8;padding:20px 40px;border-top:1px solid #e2e8f0;text-align:center;">
                              <p style="color:#aaa;font-size:12px;margin:0;line-height:1.6;">
                                Este email fue enviado automáticamente por la plataforma Padilla.<br>
                                Si recibís este email por error, ignoralo.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, email, temporaryPassword, frontendUrl);
    }

    private String buildPasswordResetEmailHtml(String name, String email, String temporaryPassword) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background-color:#f5f5f5;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f5f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background-color:#004274;padding:32px 40px;text-align:center;">
                              <h1 style="color:#ffffff;margin:0;font-size:24px;font-weight:700;letter-spacing:1px;">PADILLA</h1>
                              <p style="color:#a8c8e8;margin:6px 0 0;font-size:13px;">Gestión Inmobiliaria</p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <h2 style="color:#1a1a2e;margin:0 0 16px;font-size:20px;">Restablecé tu contraseña, %s</h2>
                              <p style="color:#555;font-size:15px;line-height:1.6;margin:0 0 24px;">
                                Recibimos una solicitud para restablecer la contraseña de tu cuenta.
                                A continuación encontrás tus credenciales temporales de acceso.
                              </p>

                              <!-- Credentials box -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;border-radius:6px;border-left:4px solid #004274;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:20px 24px;">
                                    <p style="margin:0 0 10px;font-size:13px;color:#888;text-transform:uppercase;letter-spacing:0.5px;">Acceso temporal</p>
                                    <p style="margin:0 0 8px;font-size:15px;color:#333;">
                                      <strong>Email:</strong> %s
                                    </p>
                                    <p style="margin:0;font-size:15px;color:#333;">
                                      <strong>Contraseña temporal:</strong>
                                      <span style="font-family:monospace;background:#e2e8f0;padding:2px 8px;border-radius:4px;font-size:16px;color:#1a1a2e;">%s</span>
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- Warning -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#fff8e1;border-radius:6px;border-left:4px solid #f4272b;margin-bottom:32px;">
                                <tr>
                                  <td style="padding:16px 20px;">
                                    <p style="margin:0;font-size:14px;color:#7a5c00;line-height:1.5;">
                                      ⚠️ <strong>Este acceso vence en 24 horas.</strong><br>
                                      Ingresá con la contraseña temporal y creá una nueva contraseña antes de que expire.
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- CTA Button -->
                              <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td align="center">
                                    <a href="%s/login"
                                       style="display:inline-block;background-color:#f4272b;color:#ffffff;text-decoration:none;padding:14px 40px;border-radius:6px;font-size:15px;font-weight:700;letter-spacing:0.5px;">
                                      INGRESAR AL SISTEMA
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Note -->
                          <tr>
                            <td style="padding:0 40px 32px;">
                              <p style="color:#888;font-size:13px;margin:0;">
                                Si no solicitaste este restablecimiento de contraseña, ignorá este email.
                                Tu contraseña actual seguirá siendo válida si no la cambiás.
                              </p>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#f0f4f8;padding:20px 40px;border-top:1px solid #e2e8f0;text-align:center;">
                              <p style="color:#aaa;font-size:12px;margin:0;line-height:1.6;">
                                Este email fue enviado automáticamente por la plataforma Padilla.<br>
                                Si recibís este email por error, ignoralo.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, email, temporaryPassword, frontendUrl);
    }

    private String buildResendAccessEmailHtml(String name, String email, String temporaryPassword) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background-color:#f5f5f5;font-family:Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f5f5f5;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background-color:#004274;padding:32px 40px;text-align:center;">
                              <h1 style="color:#ffffff;margin:0;font-size:24px;font-weight:700;letter-spacing:1px;">PADILLA</h1>
                              <p style="color:#a8c8e8;margin:6px 0 0;font-size:13px;">Gestión Inmobiliaria</p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <h2 style="color:#1a1a2e;margin:0 0 16px;font-size:20px;">Nuevo acceso generado, %s</h2>
                              <p style="color:#555;font-size:15px;line-height:1.6;margin:0 0 24px;">
                                Se generó un nuevo acceso temporal para tu cuenta en la plataforma Padilla.
                              </p>

                              <!-- Credentials box -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f0f4f8;border-radius:6px;border-left:4px solid #004274;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:20px 24px;">
                                    <p style="margin:0 0 10px;font-size:13px;color:#888;text-transform:uppercase;letter-spacing:0.5px;">Tus nuevas credenciales</p>
                                    <p style="margin:0 0 8px;font-size:15px;color:#333;">
                                      <strong>Email:</strong> %s
                                    </p>
                                    <p style="margin:0;font-size:15px;color:#333;">
                                      <strong>Contraseña temporal:</strong>
                                      <span style="font-family:monospace;background:#e2e8f0;padding:2px 8px;border-radius:4px;font-size:16px;color:#1a1a2e;">%s</span>
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- Warning -->
                              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#fff8e1;border-radius:6px;border-left:4px solid #f4272b;margin-bottom:32px;">
                                <tr>
                                  <td style="padding:16px 20px;">
                                    <p style="margin:0;font-size:14px;color:#7a5c00;line-height:1.5;">
                                      ⚠️ <strong>Este acceso vence en 24 horas.</strong><br>
                                      Ingresá y cambiá tu contraseña antes de que expire.
                                    </p>
                                  </td>
                                </tr>
                              </table>

                              <!-- CTA Button -->
                              <table width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td align="center">
                                    <a href="%s/login"
                                       style="display:inline-block;background-color:#f4272b;color:#ffffff;text-decoration:none;padding:14px 40px;border-radius:6px;font-size:15px;font-weight:700;letter-spacing:0.5px;">
                                      INGRESAR AL SISTEMA
                                    </a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background-color:#f0f4f8;padding:20px 40px;border-top:1px solid #e2e8f0;text-align:center;">
                              <p style="color:#aaa;font-size:12px;margin:0;line-height:1.6;">
                                Este email fue enviado automáticamente por la plataforma Padilla.<br>
                                Si recibís este email por error, ignoralo.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(name, email, temporaryPassword, frontendUrl);
    }
}
