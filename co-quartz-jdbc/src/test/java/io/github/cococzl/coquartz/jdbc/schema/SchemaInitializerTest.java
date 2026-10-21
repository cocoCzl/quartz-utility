package io.github.cococzl.coquartz.jdbc.schema;

import io.github.cococzl.coquartz.config.CoQuartzProperties;
import io.github.cococzl.coquartz.jdbc.config.CoQuartzJdbcAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quartz_task_log_schema_history", Integer.class)).isEqualTo(3);
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
        DataSource logDataSource = configuration.coQuartzLogDataSource(properties);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("coQuartzLogDataSource", logDataSource);
        JdbcTemplate log = configuration.coQuartzJdbcTemplate(
                beanFactory.getBeanProvider(DataSource.class), beanFactory.getBeanProvider(DataSource.class));

        new SchemaInitializer(log, properties).initialize();

        assertThat(log.queryForObject("SELECT COUNT(*) FROM quartz_task_log_schema_history", Integer.class)).isEqualTo(3);
        assertThat(application.query("SELECT table_name FROM information_schema.tables WHERE table_name = 'QUARTZ_TASK_LOG'", (rs, row) -> rs.getString(1))).isEmpty();
    }

    @Test
    void migrationDoesNotMistakeAnotherSchemasColumnsForCurrentTable() {
        var jdbc = jdbc();
        jdbc.execute("CREATE TABLE quartz_task_log (id VARCHAR(36) PRIMARY KEY, job_key VARCHAR(200), trigger_key VARCHAR(200), start_time TIMESTAMP, end_time TIMESTAMP, execution_time_ms BIGINT, exec_state INT, error_message VARCHAR(500), stack_trace VARCHAR(4000), execute_time TIMESTAMP)");
        jdbc.execute("CREATE SCHEMA other");
        jdbc.execute("CREATE TABLE other.quartz_task_log (id VARCHAR(36), attempt INT, is_final_attempt BOOLEAN, execution_id VARCHAR(36), fire_instance_id VARCHAR(200), scheduler_instance_id VARCHAR(200), definition_version VARCHAR(100), scheduler_name VARCHAR(200), application_namespace VARCHAR(200), origin_trigger_key VARCHAR(400), origin_scheduled_time BIGINT)");
        var properties = new CoQuartzProperties(); properties.getLog().setAutoCreateTable(true);
        new SchemaInitializer(jdbc, properties).initialize();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='PUBLIC' AND table_name='QUARTZ_TASK_LOG' AND column_name='ORIGIN_TRIGGER_KEY'", Integer.class)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"h2", "mysql", "postgresql"})
    void standaloneVersionedScriptsUpgradeLegacyRows(String dialect) throws Exception {
        var jdbc = jdbc();
        if (dialect.equals("mysql")) jdbc.execute("SET MODE MySQL");
        if (dialect.equals("postgresql")) jdbc.execute("SET MODE PostgreSQL");
        try (var connection = jdbc.getDataSource().getConnection()) {
            for (String version : java.util.List.of("V1__initial.sql", "V2__execution_correlation.sql", "V3__audit_ownership.sql")) {
                var resource = new org.springframework.core.io.ClassPathResource("db/migration/" + dialect + "/" + version);
                org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection, resource);
                if (version.startsWith("V1")) jdbc.execute("INSERT INTO quartz_task_log(id,job_key,trigger_key,start_time,exec_state,execute_time) VALUES ('legacy','job','trigger',CURRENT_TIMESTAMP,1,CURRENT_TIMESTAMP)");
            }
        }
        assertThat(jdbc.queryForObject("SELECT attempt FROM quartz_task_log WHERE id='legacy'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quartz_task_log", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT application_namespace FROM quartz_task_log WHERE id='legacy'", String.class)).isNull();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("schema_" + System.nanoTime()).build());
    }
}
