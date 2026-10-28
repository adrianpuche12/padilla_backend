package com.padilla.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner;
import
        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import
        org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import
        org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.padilla.backend.repository",
        excludeFilters =
        @org.springframework.context.annotation.ComponentScan.Filter(
                type =
                        org.springframework.context.annotation.FilterType.REGEX,
                pattern =
                        "com\\.padilla\\.backend\\.repository\\.legacy\\..*"
        ),
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @DependsOn("sshTunnelInitializer")
    @ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue =
            "true")
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource
    dataSourceWithTunnel(@Qualifier("dataSourceProperties")
                         DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue =
            "false", matchIfMissing = true)
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSourceDirect(@Qualifier("dataSourceProperties")
                                       DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * PRO-002: el schema, el ddl-auto y show-sql salen del profile activo
     * (app.db.schema, spring.jpa.hibernate.ddl-auto, spring.jpa.show-sql).
     * Las entidades de com.padilla.backend.entity.legacy se excluyen: pertenecen
     * a la base legacy (LegacyDataSourceConfig).
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            ResourceLoader resourceLoader,
            @Value("${app.db.schema}") String schema,
            @Value("${spring.jpa.hibernate.ddl-auto:none}") String ddlAuto,
            @Value("${spring.jpa.show-sql:false}") boolean showSql) {
        LocalContainerEntityManagerFactoryBean em = new
                LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setManagedTypes(mainManagedTypes(resourceLoader));

        HibernateJpaVendorAdapter vendorAdapter = new
                HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.default_schema", schema);
        jpaProperties.put("hibernate.hbm2ddl.auto", ddlAuto);
        jpaProperties.put("hibernate.show_sql", String.valueOf(showSql));
        em.setJpaProperties(jpaProperties);

        return em;
    }

    static PersistenceManagedTypes mainManagedTypes(ResourceLoader resourceLoader) {
        PersistenceManagedTypes scanned = new PersistenceManagedTypesScanner(resourceLoader)
                .scan("com.padilla.backend.entity");
        List<String> classes = scanned.getManagedClassNames().stream()
                .filter(name -> !name.startsWith(LEGACY_PACKAGE))
                .toList();
        return PersistenceManagedTypes.of(classes, scanned.getManagedPackages());
    }

    private static final String LEGACY_PACKAGE = "com.padilla.backend.entity.legacy.";

    /**
     * PRO-081: JdbcTemplate de la base principal, declarado a mano. Al existir legacyJdbcTemplate,
     * Spring Boot deja de crear el suyo y todo el codigo terminaria usando la base legacy (fuera de la
     * transaccion con SET LOCAL de la agencia).
     */
    @Bean
    @Primary
    public org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf) {
        // PRO-021: fija app.current_agency (SET LOCAL) al iniciar cada transaccion
        return new AgencyAwareJpaTransactionManager(emf);
    }
}