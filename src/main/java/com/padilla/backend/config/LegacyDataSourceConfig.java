package com.padilla.backend.config;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.padilla.backend.repository.legacy",
        entityManagerFactoryRef = "legacyEntityManagerFactory",
        transactionManagerRef = "legacyTransactionManager"
)
public class LegacyDataSourceConfig {

    @Bean
    @ConfigurationProperties("legacy.datasource")
    public DataSourceProperties legacyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource legacyDataSource(
            @Qualifier("legacyDataSourceProperties") DataSourceProperties
                    properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean
    legacyEntityManagerFactory(
            @Qualifier("legacyDataSource") DataSource dataSource,
            @Value("${legacy.db.schema:padilla}") String schema) {
        LocalContainerEntityManagerFactoryBean em = new
                LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.padilla.backend.entity.legacy");

        HibernateJpaVendorAdapter vendorAdapter = new
                HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect");
        // PRO-002: schema legacy por ambiente (legacy.db.schema). Nunca se modifica el schema legacy.
        jpaProperties.put("hibernate.default_schema", schema);
        jpaProperties.put("hibernate.hbm2ddl.auto", "none");
        jpaProperties.put("hibernate.show_sql", "false");
        em.setJpaProperties(jpaProperties);

        return em;
    }

    /** PRO-081: consultas directas a la base legacy (tablas sin entidad, como satisfaction_dashboard). */
    @Bean
    public org.springframework.jdbc.core.JdbcTemplate legacyJdbcTemplate(@Qualifier("legacyDataSource") DataSource dataSource) {
        return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager legacyTransactionManager(
            @Qualifier("legacyEntityManagerFactory") EntityManagerFactory
                    emf) {
        return new JpaTransactionManager(emf);
    }
}