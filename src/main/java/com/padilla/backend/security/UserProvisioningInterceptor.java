package com.padilla.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** PRO-005: antes de cada request autenticado al API se asegura que el usuario exista en users. */
@Component
@RequiredArgsConstructor
public class UserProvisioningInterceptor implements HandlerInterceptor {

    private final UserProvisioningService provisioningService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken && auth.isAuthenticated()
                && CurrentUser.roleOf(auth).isPresent()) {
            provisioningService.ensureProvisioned();
        }
        return true;
    }
}
