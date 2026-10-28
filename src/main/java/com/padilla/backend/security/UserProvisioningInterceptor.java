package com.padilla.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * Antes de cada request autenticado al API:
 * 1. PRO-021: resuelve la agencia del usuario y la fija en AgencyContext (de ahi la toma el
 *    transaction manager para el SET LOCAL de RLS).
 * 2. PRO-005: si el usuario no existe en users, lo crea (en la agencia por defecto).
 */
@Component
@RequiredArgsConstructor
public class UserProvisioningInterceptor implements HandlerInterceptor {

    private final UserProvisioningService provisioningService;
    private final AgencyResolver agencyResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken) || CurrentUser.roleOf(auth).isEmpty()) {
            return true;
        }
        UUID userId = CurrentUser.id();
        Optional<UUID> agency = agencyResolver.agencyOf(userId);
        if (agency.isPresent()) {
            AgencyContext.set(agency.get());
            return true;
        }
        Optional<UUID> fallback = agencyResolver.defaultAgency();
        if (fallback.isPresent()) {
            AgencyContext.set(fallback.get());
            provisioningService.ensureProvisioned();
        }
        // Sin agencia: los endpoints de negocio no ven datos (RLS) y AgencyContext.require() da 403
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AgencyContext.clear();
    }
}
