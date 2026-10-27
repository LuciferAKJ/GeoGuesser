package com.geoguesser.config;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Configures EntityManagerFactory (JPA / Hibernate) to depend on databaseMigrationRunner.
 * This guarantees that databaseMigrationRunner runs before Hibernate initializes and performs
 * ddl-auto updates, preventing startup errors when adding NOT NULL constraints to existing columns.
 */
@Component
public class DatabaseMigrationConfig extends EntityManagerFactoryDependsOnPostProcessor {

    public DatabaseMigrationConfig() {
        super("databaseMigrationRunner");
    }
}
