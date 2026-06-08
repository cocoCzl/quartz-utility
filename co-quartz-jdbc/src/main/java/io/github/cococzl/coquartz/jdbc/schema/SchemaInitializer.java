package io.github.cococzl.coquartz.jdbc.schema;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final CoQuartzProperties properties;

    public SchemaInitializer(JdbcTemplate jdbcTemplate, CoQuartzProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public void initialize() {
        if (!properties.getLog().isAutoCreateTable()) {
            log.info("Co-Quartz auto table creation is disabled");
            return;
        }

        if (tableExists()) {
            log.info("Co-Quartz quartz_task_log table already exists");
            return;
        }

        String dialect = detectDialect();
        log.info("Co-Quartz creating quartz_task_log table for dialect: {}", dialect);

        String sql = loadSchema(dialect);
        if (sql != null) {
            jdbcTemplate.execute(sql);
            log.info("Co-Quartz quartz_task_log table created successfully");
        } else {
            log.warn("Co-Quartz no schema found for dialect: {}", dialect);
        }
    }

    private boolean tableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quartz_task_log WHERE 1=0", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String detectDialect() {
        try {
            String dbName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
            return dbName.toLowerCase();
        } catch (Exception e) {
            log.warn("Failed to detect database dialect, defaulting to h2", e);
            return "h2";
        }
    }

    private String loadSchema(String dialect) {
        String resourcePath;
        if (dialect.contains("mysql")) {
            resourcePath = "schema-quartz-task-log-mysql.sql";
        } else if (dialect.contains("postgresql")) {
            resourcePath = "schema-quartz-task-log-postgresql.sql";
        } else {
            resourcePath = "schema-quartz-task-log-h2.sql";
        }

        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to load schema: {}", resourcePath, e);
            return null;
        }
    }
}