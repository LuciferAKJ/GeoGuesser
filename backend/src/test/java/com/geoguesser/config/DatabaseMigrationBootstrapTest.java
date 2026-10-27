package com.geoguesser.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DatabaseMigrationBootstrapTest {

    @Configuration
    static class ValidBootstrapConfig {
        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return mock(JdbcTemplate.class);
        }

        @Bean
        public DatabaseMigrationRunner databaseMigrationRunner(JdbcTemplate jdbcTemplate) {
            return new DatabaseMigrationRunner(jdbcTemplate);
        }

        @Bean
        public static DatabaseMigrationConfig databaseMigrationConfig() {
            return new DatabaseMigrationConfig();
        }

        @Bean(name = "entityManagerFactory")
        public EntityManagerFactory entityManagerFactory() {
            return mock(EntityManagerFactory.class);
        }

        @Bean(name = "transactionManager")
        public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            JpaTransactionManager txManager = new JpaTransactionManager();
            txManager.setEntityManagerFactory(emf);
            return txManager;
        }
    }

    @Configuration
    static class CyclicBootstrapConfig {
        @Bean
        public DataSource dataSource() {
            return mock(DataSource.class);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return mock(JdbcTemplate.class);
        }

        // Simulates previous flawed constructor injecting PlatformTransactionManager
        @Bean
        public DatabaseMigrationRunner databaseMigrationRunner(JdbcTemplate jdbcTemplate, PlatformTransactionManager txManager) {
            return new DatabaseMigrationRunner(jdbcTemplate, txManager);
        }

        @Bean
        public static DatabaseMigrationConfig databaseMigrationConfig() {
            return new DatabaseMigrationConfig();
        }

        @Bean(name = "entityManagerFactory")
        public EntityManagerFactory entityManagerFactory() {
            return mock(EntityManagerFactory.class);
        }

        @Bean(name = "transactionManager")
        public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            JpaTransactionManager txManager = new JpaTransactionManager();
            txManager.setEntityManagerFactory(emf);
            return txManager;
        }
    }

    @Test
    @DisplayName("Spring bootstrap succeeds without circular reference between databaseMigrationRunner, entityManagerFactory, and transactionManager")
    void testBootstrapWithoutCircularReference() {
        assertDoesNotThrow(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(ValidBootstrapConfig.class);
                context.refresh();

                DatabaseMigrationRunner runner = context.getBean(DatabaseMigrationRunner.class);
                assertNotNull(runner);

                // Verify runner transactionManager is not the JPA transactionManager bean
                PlatformTransactionManager runnerTx = runner.getTransactionManager();
                PlatformTransactionManager contextTx = context.getBean("transactionManager", PlatformTransactionManager.class);
                assertNotSame(runnerTx, contextTx);
                assertTrue(contextTx instanceof JpaTransactionManager);

                EntityManagerFactory emf = context.getBean(EntityManagerFactory.class);
                assertNotNull(emf);
            }
        });
    }

    @Test
    @DisplayName("Detects that injecting JPA transactionManager into pre-JPA runner triggers circular dependency cycle")
    void testDetectsCycleWhenJpaTransactionManagerIsInjected() {
        assertThrows(Exception.class, () -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(CyclicBootstrapConfig.class);
                context.refresh();
            }
        });
    }
}
