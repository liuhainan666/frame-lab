package com.framelab.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = "framelab.worker.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigrationSeedTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("framelab")
            .withUsername("framelab")
            .withPassword("framelab_pass");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void runsBothMigrationsAndSeedsTheDemoData() {
        Integer businessTables = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM information_schema.tables
                 WHERE table_schema = DATABASE()
                   AND table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(businessTables).isEqualTo(12);

        assertThat(count("app_user")).isEqualTo(3);
        assertThat(count("template_category")).isEqualTo(6);
        assertThat(count("template")).isEqualTo(13);
        assertThat(count("template_category_translation")).isEqualTo(6);
        assertThat(count("template_translation")).isEqualTo(13);
        assertThat(count("simulation_setting")).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM template
                 WHERE cover_path LIKE '/assets/templates/%.webp'
                   AND is_published = 1
                """, Integer.class)).isEqualTo(13);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM template_category_translation WHERE locale <> 'en-US'
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM template_translation WHERE locale <> 'en-US'
                """, Integer.class)).isZero();

        assertThat(jdbc.queryForObject("""
                SELECT CONCAT(material_check_delay_ms, '/', generate_delay_ms, '/',
                              finalize_delay_ms, '/', fail_times, '/', fail_retryable)
                  FROM simulation_setting WHERE id = 1
                """, String.class)).isEqualTo("800/3000/1200/0/0");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user WHERE password_hash LIKE '$2b$12$%'
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void enforcesSeedUniquenessAndCheckConstraints() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO app_user (email, display_name, password_hash)
                VALUES ('demo@framelab.local', '重复账号', '$2b$12$oDiRaSYrHZCgC1ttuA3YneJun5CYoQwnGS/PGB/falO5W9o/R3uQG')
                """))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO template
                  (id, category_id, title, description, cover_path,
                   required_image_count, max_file_size_bytes)
                VALUES ('invalid-template', 'travel', '非法模板', '用于校验', '/assets/templates/invalid.webp',
                        0, 1048576)
                """))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO simulation_setting (id, fail_stage, fail_times, fail_retryable)
                VALUES (2, NULL, 0, 0)
                """))
                .isInstanceOf(DataAccessException.class);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
