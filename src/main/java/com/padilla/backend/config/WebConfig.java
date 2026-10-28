package com.padilla.backend.config;

import com.padilla.backend.security.UserProvisioningInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final UserProvisioningInterceptor userProvisioningInterceptor;
    private final com.padilla.backend.security.LeadsModuleInterceptor leadsModuleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userProvisioningInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**");
        // PRO-080: corre despues del anterior (necesita la agencia ya resuelta)
        registry.addInterceptor(leadsModuleInterceptor)
                .addPathPatterns("/api/leads/**", "/api/sellers/**", "/api/sources/**");
    }
}
