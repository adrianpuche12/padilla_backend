package com.padilla.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @DependsOn("sshTunnelInitializer")
    @ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue = "true")
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSourceWithTunnel(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue = "false", matchIfMissing = true)
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSourceDirect(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}