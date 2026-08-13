package de.bht.modulebook.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// runs Flyway database migrations when app starts
@Configuration
public class DatabaseMigrationConfig {

    @Bean
    ApplicationRunner migrateDatabase(DataSource dataSource) {
        return args -> Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
