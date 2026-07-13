package io.github.cococzl.coquartz.jdbc.schema;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.jdbc.config.CoQuartzJdbcAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaInitializerTest {

    @Test
    void migrationUpgradesLegacyTableWithoutLosingRows() {
        JdbcTemplate jdbc = jdbc();
        jdbc.execute("CREATE TABLE quartz_task_log (id VARCHAR(36) PRIMARY KEY, job_key VARCHAR(200) NOT NULL, trigger_key VARCHAR(200) NOT NULL, start_time TIMESTAMP NOT NULL, end_time TIMESTAMP, execution_time_ms BIGINT, exec_state INT NOT NULL, error_message VARCHAR(500), stack_trace VARCHAR(4000), execute_time TIMESTAMP NOT NULL)");
        jdbc.execute("INSERT INTO quartz_task_log(id, job_key, trigger_key, start_time, exec_state, execute_time) VALUES ('legacy', 'DEFAULT.legacy', 'DEFAULT.trigger', CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP)");
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);

        new SchemaInitializer(jdbc, properties).initialize();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quartz_task_log WHERE id = 'legacy'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attempt FROM quartz_task_log WHERE id = 'legacy'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quartz_task_log_schema_history", Integer.class)).isEqualTo(2);
    }

    @Test
    void automaticDdlIsDisabledByDefault() {
        JdbcTemplate jdbc = jdbc();
        new SchemaInitializer(jdbc, new CoQuartzProperties()).initialize();

        assertThat(jdbc.query("SELECT table_name FROM information_schema.tables WHERE table_name = 'QUARTZ_TASK_LOG'", (rs, row) -> rs.getString(1))).isEmpty();
    }

    @Test
    void configuredLogDatasourceIsIsolatedFromApplicationDatasource() {
        JdbcTemplate application = jdbc();
        CoQuartzProperties properties = new CoQuartzProperties();
        properties.getLog().setAutoCreateTable(true);
        properties.getLog().getDatasource().setUrl("jdbc:h2:mem:isolated_log_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        properties.getLog().getDatasource().setDriverClassName("org.h2.Driver");
        CoQuartzJdbcAutoConfiguration configuration = new CoQuartzJdbcAutoConfiguration();
        DataSource logDataSource = configuration.coQuartzLogDataSource(application.getDataSource(), properties);
        JdbcTemplate log = configuration.coQuartzJdbcTemplate(logDataSource);

        new SchemaInitializer(log, properties).initialize();

        assertThat(log.queryForObject("SELECT COUNT(*) FROM quartz_task_log_schema_history", Integer.class)).isEqualTo(2);
        assertThat(application.query("SELECT table_name FROM information_schema.tables WHERE table_name = 'QUARTZ_TASK_LOG'", (rs, row) -> rs.getString(1))).isEmpty();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("schema_" + System.nanoTime()).build());
    }
}
