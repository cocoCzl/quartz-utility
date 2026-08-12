package io.github.cococzl.coquartz.jdbc.schema;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;

/** Applies the small, versioned Co-Quartz log-schema sequence when explicitly enabled. */
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
            log.info("Co-Quartz automatic schema migration is disabled");
            return;
        }
        String dialect = detectDialect();
        createHistoryTable();
        applyV1(dialect);
        applyV2(dialect);
    }

    private void createHistoryTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS quartz_task_log_schema_history "
                + "(version INT NOT NULL PRIMARY KEY, installed_at TIMESTAMP NOT NULL)");
    }

    private void applyV1(String dialect) {
        if (isApplied(1)) return;
        executeV1Table(loadSchema(dialect));
        markApplied(1);
    }

    /** Upgrade installations from the pre-execution-correlation table without losing stored records. */
    private void applyV2(String dialect) {
        if (isApplied(2)) return;
        addColumnIfMissing("attempt", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("is_final_attempt", booleanType(dialect) + " NOT NULL DEFAULT " + booleanLiteral(dialect, true));
        addColumnIfMissing("execution_id", "VARCHAR(36)");
        addColumnIfMissing("fire_instance_id", "VARCHAR(200)");
        addColumnIfMissing("scheduler_instance_id", "VARCHAR(200)");
        addColumnIfMissing("definition_version", "VARCHAR(100)");
        createIndex("idx_task_log_job_key_start_time", "job_key, start_time");
        createIndex("idx_task_log_exec_state", "exec_state");
        createIndex("idx_task_log_execution_id", "execution_id");
        markApplied(2);
    }

    private void addColumnIfMissing(String column, String definition) {
        if (!columnExists(column)) jdbcTemplate.execute("ALTER TABLE quartz_task_log ADD COLUMN " + column + " " + definition);
    }

    private boolean isApplied(int version) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM quartz_task_log_schema_history WHERE version = ?", Integer.class, version);
        return count != null && count > 0;
    }

    private void markApplied(int version) {
        jdbcTemplate.update("INSERT INTO quartz_task_log_schema_history(version, installed_at) VALUES (?, CURRENT_TIMESTAMP)", version);
        log.info("Co-Quartz log-schema migration V{} applied", version);
    }

    private String detectDialect() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String name = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (name.contains("mysql")) return "mysql";
            if (name.contains("postgresql")) return "postgresql";
            if (name.contains("h2")) return "h2";
            throw new IllegalStateException("Unsupported Co-Quartz log database dialect: " + name);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to identify Co-Quartz log database dialect", e);
        }
    }

    private boolean columnExists(String column) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (var columns = metadata.getColumns(connection.getCatalog(), null, "quartz_task_log", column)) {
                if (columns.next()) return true;
            }
            try (var columns = metadata.getColumns(connection.getCatalog(), null, "QUARTZ_TASK_LOG", column.toUpperCase(Locale.ROOT))) {
                return columns.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Co-Quartz log schema", e);
        }
    }

    private String loadSchema(String dialect) {
        String path = "schema-quartz-task-log-" + dialect + ".sql";
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Missing Co-Quartz schema migration resource " + path, e);
        }
    }

    private void executeV1Table(String script) {
        for (String statement : script.split(";")) {
            String sql = statement.replaceAll("(?m)^\\s*--.*$", "").trim();
            if (!sql.isEmpty() && !sql.toUpperCase(Locale.ROOT).startsWith("CREATE INDEX")) jdbcTemplate.execute(sql);
        }
    }

    private void createIndex(String index, String columns) {
        if (!indexExists(index)) jdbcTemplate.execute("CREATE INDEX " + index + " ON quartz_task_log (" + columns + ")");
    }

    private boolean indexExists(String index) {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String table : new String[] {"quartz_task_log", "QUARTZ_TASK_LOG"}) {
                try (var indexes = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
                    while (indexes.next()) {
                        if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect Co-Quartz log indexes", e);
        }
    }

    private static String booleanType(String dialect) { return "mysql".equals(dialect) ? "TINYINT(1)" : "BOOLEAN"; }
    private static String booleanLiteral(String dialect, boolean value) { return "mysql".equals(dialect) ? (value ? "1" : "0") : Boolean.toString(value); }
}
