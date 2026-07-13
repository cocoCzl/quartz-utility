package io.github.cococzl.coquartz.core;

import io.github.cococzl.coquartz.annotation.QuartzTask;
import io.github.cococzl.coquartz.config.CoQuartzProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.context.support.GenericApplicationContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real shared-MySQL verification for TASK-004. It is opt-in so normal builds do not
 * require an external database. Run it with co.quartz.test.mysql.url/user/password.
 */
@EnabledIfSystemProperty(named = "co.quartz.test.mysql.url", matches = ".+")
class QuartzJdbcClusterReconciliationIT {

    private static final String DATABASE = "co_quartz_task4_it";

    private final List<Scheduler> schedulers = new ArrayList<>();
    private String databaseUrl;
    private String username;
    private String password;

    @BeforeEach
    void setUp() throws Exception {
        String rootUrl = System.getProperty("co.quartz.test.mysql.url");
        username = System.getProperty("co.quartz.test.mysql.user");
        password = System.getProperty("co.quartz.test.mysql.password");
        databaseUrl = databaseUrl(rootUrl, DATABASE);
        try (Connection connection = DriverManager.getConnection(rootUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE IF NOT EXISTS " + DATABASE);
        }
        resetQuartzSchema();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (Scheduler scheduler : schedulers) {
            scheduler.shutdown(true);
        }
        schedulers.clear();
    }

    @Test
    void concurrentNodesConvergeOnOneCodeOwnedJobAndTrigger() throws Exception {
        runNodeProcessesTogether("initial");
        Scheduler verifier = newClusterScheduler("verifier");
        try {
            JobKey jobKey = JobKey.jobKey("clusterTask", "DEFAULT");
            TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_clusterTask", "DEFAULT");
            assertThat(verifier.getJobKeys(GroupMatcher.anyJobGroup())).containsExactly(jobKey);
            assertThat(verifier.getTriggersOfJob(jobKey)).hasSize(1);
            assertThat(verifier.getTrigger(triggerKey)).isNotNull();
            assertThat(verifier.getJobDetail(jobKey).getJobDataMap()
                    .getString(CoQuartzConstants.DEFINITION_VERSION)).isNotBlank();
        } finally {
            verifier.shutdown(true);
            schedulers.remove(verifier);
        }
    }

    @Test
    void concurrentRecoveryOfDeletedTaskCreatesOneConsistentDefinition() throws Exception {
        Scheduler first = newClusterScheduler("node-a");
        try (GenericApplicationContext firstContext = context()) {
            processor(first, firstContext).registerJobs();
            first.deleteJob(JobKey.jobKey("clusterTask", "DEFAULT"));
        }
        first.shutdown(true);
        schedulers.remove(first);
        runNodeProcessesTogether("initial");
        Scheduler verifier = newClusterScheduler("verifier");
        try {
            JobKey jobKey = JobKey.jobKey("clusterTask", "DEFAULT");
            assertThat(verifier.getJobKeys(GroupMatcher.anyJobGroup())).containsExactly(jobKey);
            assertThat(verifier.getTriggersOfJob(jobKey)).hasSize(1);
        } finally {
            verifier.shutdown(true);
            schedulers.remove(verifier);
        }
    }

    @Test
    void concurrentDefinitionChangeConvergesOnChangedSchedule() throws Exception {
        runNodeProcessesTogether("initial");
        runNodeProcessesTogether("changed");
        Scheduler verifier = newClusterScheduler("verifier");
        try {
            Trigger trigger = verifier.getTrigger(TriggerKey.triggerKey("TRIGGER_clusterTask", "DEFAULT"));
            assertThat(((org.quartz.CronTrigger) trigger).getCronExpression()).isEqualTo("0 0 10 * * ?");
        } finally {
            verifier.shutdown(true);
            schedulers.remove(verifier);
        }
    }

    @Test
    void clusterReconciliationPreservesPausedState() throws Exception {
        runNodeProcessesTogether("initial");
        Scheduler controller = newClusterScheduler("controller");
        JobKey jobKey = JobKey.jobKey("clusterTask", "DEFAULT");
        TriggerKey triggerKey = TriggerKey.triggerKey("TRIGGER_clusterTask", "DEFAULT");
        controller.pauseJob(jobKey);
        controller.shutdown(true);
        schedulers.remove(controller);

        runNodeProcessesTogether("changed");
        Scheduler verifier = newClusterScheduler("verifier");
        try {
            assertThat(verifier.getTriggerState(triggerKey)).isEqualTo(Trigger.TriggerState.PAUSED);
            assertThat(((org.quartz.CronTrigger) verifier.getTrigger(triggerKey)).getCronExpression())
                    .isEqualTo("0 0 10 * * ?");
        } finally {
            verifier.shutdown(true);
            schedulers.remove(verifier);
        }
    }

    @Test
    void nonConcurrentTaskDoesNotOverlapWhenBothClusterNodesTriggerIt() throws Exception {
        Scheduler first = newClusterScheduler("node-a");
        Scheduler second = newClusterScheduler("node-b");
        first.setJobFactory(new CoQuartzJobFactory());
        second.setJobFactory(new CoQuartzJobFactory());
        ClusterBlockingJob.reset();

        QuartzTaskBuilder.newBuilder()
                .jobClass(ClusterBlockingJob.class)
                .jobName("nonConcurrentClusterTask")
                .intervalInSeconds(3600)
                .startAt(new java.util.Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                .concurrent(false)
                .schedule(first);
        first.start();
        second.start();

        JobKey jobKey = JobKey.jobKey("nonConcurrentClusterTask", "DEFAULT");
        first.triggerJob(jobKey);
        assertThat(ClusterBlockingJob.firstStarted.await(10, TimeUnit.SECONDS)).isTrue();
        second.triggerJob(jobKey);

        Thread.sleep(500);
        assertThat(ClusterBlockingJob.starts.get()).isEqualTo(1);
        assertThat(ClusterBlockingJob.maxActive.get()).isEqualTo(1);

        ClusterBlockingJob.release.countDown();
        assertThat(ClusterBlockingJob.secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(ClusterBlockingJob.maxActive.get()).isEqualTo(1);
    }

    private void runNodeProcessesTogether(String definition) throws Exception {
        Process first = nodeProcess("node-a", definition);
        Process second = nodeProcess("node-b", definition);
        assertThat(first.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(second.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(first.exitValue()).isZero();
        assertThat(second.exitValue()).isZero();
    }

    private Process nodeProcess(String instanceId, String definition) throws IOException {
        String java = System.getProperty("java.home") + "/bin/java";
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        ProcessBuilder builder = new ProcessBuilder(java, "-cp", classPath,
                NodeRunner.class.getName(), databaseUrl, username, password, instanceId, definition);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private Scheduler newClusterScheduler(String instanceId) throws Exception {
        Scheduler scheduler = createClusterScheduler(databaseUrl, username, password, instanceId);
        schedulers.add(scheduler);
        return scheduler;
    }

    private static Scheduler createClusterScheduler(String databaseUrl, String username, String password,
                                                    String instanceId) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "coQuartzTask4Cluster");
        properties.setProperty("org.quartz.scheduler.instanceId", instanceId);
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.threadPool.makeThreadsDaemons", "true");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass",
                "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        properties.setProperty("org.quartz.jobStore.isClustered", "true");
        properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", "1000");
        properties.setProperty("org.quartz.jobStore.dataSource", "coQuartzClusterDataSource");
        properties.setProperty("org.quartz.dataSource.coQuartzClusterDataSource.driver", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("org.quartz.dataSource.coQuartzClusterDataSource.URL", databaseUrl);
        properties.setProperty("org.quartz.dataSource.coQuartzClusterDataSource.user", username);
        properties.setProperty("org.quartz.dataSource.coQuartzClusterDataSource.password", password);
        properties.setProperty("org.quartz.dataSource.coQuartzClusterDataSource.maxConnections", "5");
        return new StdSchedulerFactory(properties).getScheduler();
    }

    private QuartzJobAnnotationProcessor processor(Scheduler scheduler, GenericApplicationContext context) {
        return new QuartzJobAnnotationProcessor(new CoQuartzScheduler(scheduler), context,
                new CoQuartzProperties(), new MethodTaskRegistry(context));
    }

    private GenericApplicationContext context() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("clusterTaskBean", ClusterTaskBean.class);
        context.refresh();
        return context;
    }

    private void resetQuartzSchema() throws SQLException, IOException {
        String sql;
        try (var input = StdSchedulerFactory.class.getResourceAsStream(
                "/org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql")) {
            if (input == null) {
                throw new IOException("Quartz MySQL schema resource is unavailable");
            }
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = DriverManager.getConnection(databaseUrl, username, password);
             Statement statement = connection.createStatement()) {
            for (String statementSql : sql.split(";")) {
                String trimmed = statementSql.replaceAll("(?m)^\\s*--.*$", "").trim();
                if (!trimmed.isEmpty()) {
                    statement.execute(trimmed);
                }
            }
        }
    }

    private String databaseUrl(String rootUrl, String database) {
        String separator = rootUrl.contains("?") ? "&" : "?";
        String base = rootUrl.endsWith("/") ? rootUrl + database : rootUrl + "/" + database;
        return base + separator + "useSSL=false&allowPublicKeyRetrieval=true";
    }

    static class ClusterTaskBean {
        @QuartzTask(name = "clusterTask", cron = "0 0 9 * * ?", timeZone = "UTC")
        public void run() {
        }
    }

    static class ChangedClusterTaskBean {
        @QuartzTask(name = "clusterTask", cron = "0 0 10 * * ?", timeZone = "Asia/Shanghai")
        public void run() {
        }
    }

    public static class ClusterBlockingJob implements org.quartz.Job {
        static final AtomicInteger starts = new AtomicInteger();
        static final AtomicInteger active = new AtomicInteger();
        static final AtomicInteger maxActive = new AtomicInteger();
        static CountDownLatch firstStarted;
        static CountDownLatch secondStarted;
        static CountDownLatch release;

        static void reset() {
            starts.set(0);
            active.set(0);
            maxActive.set(0);
            firstStarted = new CountDownLatch(1);
            secondStarted = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        @Override
        public void execute(org.quartz.JobExecutionContext context) {
            int nowActive = active.incrementAndGet();
            maxActive.accumulateAndGet(nowActive, Math::max);
            int execution = starts.incrementAndGet();
            if (execution == 1) {
                firstStarted.countDown();
            } else if (execution == 2) {
                secondStarted.countDown();
            }
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        }
    }

    /** Runs one scheduler node in its own JVM; Quartz's SchedulerRepository is JVM-local. */
    public static class NodeRunner {
        public static void main(String[] args) throws Exception {
            Scheduler scheduler = createClusterScheduler(args[0], args[1], args[2], args[3]);
            GenericApplicationContext context = new GenericApplicationContext();
            Class<?> taskType = "changed".equals(args[4]) ? ChangedClusterTaskBean.class : ClusterTaskBean.class;
            context.registerBean("clusterTaskBean", taskType);
            context.refresh();
            try {
                new QuartzJobAnnotationProcessor(new CoQuartzScheduler(scheduler), context,
                        new CoQuartzProperties(), new MethodTaskRegistry(context)).registerJobs();
            } finally {
                context.close();
                scheduler.shutdown(true);
            }
        }
    }
}
