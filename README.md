# Co-Quartz

<div align="center">

面向 Spring Boot 的 Quartz Starter：以声明式任务为首选，并提供可靠审计、重试、超时、指标与受控运维能力。

[![JDK](https://img.shields.io/badge/JDK-17+-green.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quartz](https://img.shields.io/badge/Quartz-2.3.2-blue.svg)](http://www.quartz-scheduler.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

Co-Quartz 适用于需要在单机或 Quartz JDBC 集群中稳定运行定时任务的 Spring Boot 业务系统。它保留原生 Quartz `Job` 的高级能力，同时为常见任务提供更少配置、更明确的运行语义和可观测性。

## 核心特性

- **声明式任务定义** — 普通 Spring Bean 方法添加 `@QuartzTask`，自动注册 + 自动增强
- **失败自动重试** — 支持重试次数、间隔、指数退避
- **超时控制** — 防止任务长时间占用资源，超时自动中断
- **自动执行日志** — 每次执行（含每次重试）自动记录到 `quartz_task_log`
- **异步批量写入** — 不阻塞任务执行，大幅降低延迟
- **告警事件** — 基于 Spring `ApplicationEvent`，支持失败/超时/慢任务/连续失败告警
- **任务管理 API** — `TaskAdminService`、`TaskQueryService`、`QuartzTaskBuilder`
- **版本化迁移** — 生产默认不执行 DDL；本地可显式启用 V1/V2 日志迁移
- **Micrometer 集成** — 可选，classpath 存在时自动注册指标

## 适用范围

- 支持单机内存调度与共享 JDBC JobStore 集群。
- 代码拥有声明式任务的定义；Quartz 保留暂停、恢复等运行时状态。
- 调度与重试遵循至少一次交付语义，业务操作必须自行保证幂等。
- 超时是协作式取消：会请求中断，但不会强制终止忽略中断的业务线程。
- 不提供 Web UI、认证、授权或 exactly-once 语义；这些边界由接入应用负责。

## 快速开始

### 1. 添加依赖

```xml
<dependency>
  <groupId>io.github.cococzl</groupId>
  <artifactId>co-quartz-starter</artifactId>
  <version>2.1.0-SNAPSHOT</version>
</dependency>
```

发布版本请以 Maven Central 中的最新稳定版本替换 `2.1.0-SNAPSHOT`。

Starter 默认只包含调度核心，不要求应用提供数据库。需要执行日志时再添加 JDBC 模块：

```xml
<dependency>
  <groupId>io.github.cococzl</groupId>
  <artifactId>co-quartz-jdbc</artifactId>
  <version>2.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义声明式任务（推荐）

在普通 Spring Bean 的 `public void` 无参方法上添加 `@QuartzTask`：

```java
@Component
public class ReportTasks {

    @QuartzTask(name = "dailyReport", cron = "0 0 9 * * ?", timeZone = "Asia/Shanghai")
    public void generateDailyReport() {
        // 业务逻辑
    }
}
```

任务名称必须显式提供，并在任务组内唯一。

### 3. 使用传统 Quartz Job（高级模式）

需要访问 `JobExecutionContext` 时，可以继续实现 `org.quartz.Job` 并添加 `@QuartzJob`：

```java
@QuartzJob(name = "myJob", cron = "0 0 12 * * ?",
           timeZone = "Asia/Shanghai",
           retryTimes = 2, retryInterval = 2000, timeout = 10000)
@Component
public class MyJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        // 业务逻辑
    }
}
```

### 4. 动态创建任务

```java
@Autowired
private CoQuartzScheduler scheduler;

QuartzTaskBuilder.newBuilder()
    .jobClass(MyJob.class)
    .jobName("dynamicJob")
    .cron("0 0/5 * * * ?")
    .timeZone("Asia/Shanghai")
    .retryTimes(2)
    .timeout(10000L)
    .schedule(scheduler);
```

### 5. 管理任务

```java
@Autowired TaskAdminService admin;   // pause, resume, delete, triggerNow, rescheduleCron/Interval
@Autowired TaskQueryService query;    // listJobs, getJobDetail, getRunningJobs, ...
@Autowired TaskLogService logService;  // pageLogs, statistics, cleanup
```

### 6. 订阅告警

```java
@EventListener
public void onFailure(TaskFailureEvent event) {
    // event.getJobKey(), event.getErrorMessage(), event.getStackTrace()
}

@EventListener
public void onTimeout(TaskTimeoutEvent event) { /* ... */ }

@EventListener
public void onSlowTask(TaskSlowEvent event) { /* ... */ }

@EventListener
public void onConsecutiveFailure(TaskConsecutiveFailureEvent event) { /* ... */ }
```

## 配置

```yaml
co-quartz:
  scheduling:
    default-time-zone: UTC
  log:
    enabled: false # 默认关闭；执行数据库迁移后再开启
    retention-days: 30
    cleanup-cron: "0 0 2 * * ?"
    auto-create-table: false # production default; enable only for local development
    capture-stack-trace: true
    reliable-audit: false
    reliable-audit-recovery-threshold-ms: 60000
    datasource: # optional; when set, logs use this datasource instead of Quartz/application datasource
      url: jdbc:postgresql://log-db:5432/co_quartz_log
      username: co_quartz
      password: change-me
      driver-class-name: org.postgresql.Driver
  async:
    enabled: true
    log-queue-capacity: 1000
    log-batch-size: 100
    log-flush-interval-ms: 1000
    shutdown-flush-timeout-ms: 10000
  monitoring:
    enabled: true
    slow-task-threshold-ms: 30000
    consecutive-failure-threshold: 3
    max-metric-job-tags: 100
  timeout-pool:
    core-size: 2
    max-size: 5
    shutdown-await-ms: 10000
  annotation:
    enabled: true
```

`co-quartz.async.enabled: false` 表示同步写执行日志，不表示关闭日志。关闭日志只需设置
`co-quartz.log.enabled: false`。`co-quartz.monitoring.enabled: false` 会关闭 Micrometer 指标、慢任务告警和连续失败告警，但保留基础失败/超时事件。

时区规则：任务显式 `timeZone` 优先，否则使用 `co-quartz.scheduling.default-time-zone`；默认值为 `UTC`，不会读取 JVM 或服务器默认时区。固定间隔按绝对时长运行，不受时区或夏令时影响。

超时是协作式取消：达到阈值时 Co-Quartz 请求中断，并报告任务已超时。业务代码必须响应中断；忽略中断的任务会被明确报告为“中断已请求、终止未确认”，不会被宣称为强制终止。超时执行器没有等待队列，饱和时立即拒绝并作为任务失败记录和事件公开。

Quartz 触发与失败恢复按至少一次语义交付；业务任务必须自行保证幂等性。

设置 `co-quartz.log.reliable-audit: true` 后，Co-Quartz 会在业务任务开始前同步写入一条 `STARTED` 生命周期记录，并在结束后同步更新同一条记录。开始或结束的审计写入失败会使该次 Quartz 执行失败，避免将其错误报告为已完成；默认异步模式不受此设置影响。异常信息进入任一日志模式前都会经 `LogSanitizer` 脱敏；`capture-stack-trace: false` 可禁止保存和发布堆栈。

启动时会将超过 `reliable-audit-recovery-threshold-ms` 仍为 `STARTED` 的单节点记录收敛为 `INTERRUPTED`。在 Quartz 集群模式，当前节点不会收敛其他节点归属的记录，以免误关闭仍在运行的远端任务。

Quartz 本身使用 Spring Boot 标准配置：

```yaml
spring:
  quartz:
    job-store-type: memory
    auto-startup: true
```

单机内存模式适用于本地开发或无共享调度需求的应用。集群部署必须使用共享 JDBC JobStore，例如：

```yaml
spring:
  quartz:
    job-store-type: jdbc
    properties:
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 10000
```

执行日志默认关闭，不影响重试、超时和基础失败事件。添加 `co-quartz-jdbc`、执行迁移并设置
`co-quartz.log.enabled: true` 后，默认使用异步尽力写入。对审计完整性有要求时可启用可靠审计：

```yaml
co-quartz:
  log:
    reliable-audit: true
    reliable-audit-recovery-threshold-ms: 60000
```

如暂无数据源，可关闭日志：

```yaml
co-quartz:
  log:
    enabled: false
  async:
    enabled: false
```

## 数据库表

生产环境默认不执行自动 DDL。请先执行对应方言的迁移脚本；仅在本地开发时显式设置 `co-quartz.log.auto-create-table: true`，由 Co-Quartz 记录并执行 V1/V2 迁移：

- MySQL: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-mysql.sql`
- PostgreSQL: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-postgresql.sql`
- H2: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-h2.sql`

### 升级说明

从旧日志表升级时，显式启用自动迁移一次或按 V1/V2 顺序执行迁移。V2 会保留现有数据并补齐重试次数、最终尝试、执行关联、节点和定义版本字段。生产升级完成后应恢复 `auto-create-table: false`。管理查询不再返回原始 `JobDataMap`；声明式任务的删除和重调度应改为修改代码定义。

### 支持矩阵

| 组件 | 支持版本 |
|---|---|
| Java | 17+ |
| Spring Boot | 3.3.x |
| Quartz | 2.3.2 |
| 日志数据库 | H2、MySQL 8+、PostgreSQL 14+ |

## @QuartzJob 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | 类名 | 任务名称 |
| `group` | String | "DEFAULT" | 任务组 |
| `description` | String | "" | 任务描述 |
| `cron` | String | "" | Cron 表达式 |
| `intervalSeconds` | int | 0 | 间隔秒数（与 cron 二选一） |
| `timeZone` | String | "" | Cron 时区；空值使用 `co-quartz.scheduling.default-time-zone` |
| `concurrent` | boolean | false | 是否允许并发 |
| `durable` | boolean | false | 是否持久化 |
| `recoverable` | boolean | false | 是否可恢复 |
| `enabled` | boolean | true | 是否启用 |
| `retryTimes` | int | 0 | 重试次数 |
| `retryInterval` | long | 1000 | 重试间隔（毫秒） |
| `exponentialBackoff` | boolean | false | 指数退避 |
| `backoffMultiplier` | double | 1.5 | 退避乘数 |
| `timeout` | long | 0 | 超时（毫秒），0 不限制 |
| `misfirePolicy` | MisfirePolicy | SMART_POLICY | Misfire 策略 |

`@QuartzTask` 支持相同的调度、并发、重试、超时和 misfire 参数，但任务名称必须显式提供，且方法必须为 `public void` 无参实例方法。

### Misfire 语义

| 策略 | Cron 任务 | 固定间隔任务 |
|------|-----------|--------------|
| `SMART_POLICY` | 错过后补执行一次，再继续正常计划 | 跳过错过次数，从下一个未来时间继续 |
| `FIRE_NOW` | 立即补执行一次，再继续正常计划 | 立即执行一次 |
| `IGNORE_MISFIRES` | 保留原始触发时间，恢复后可能密集追赶 | 保留原始触发时间，恢复后可能密集追赶 |

`cron` 与 `intervalSeconds` 必须且只能配置一个；无效 cron、无效时区或二者冲突会导致应用启动失败。

> 升级提示：旧版本的 Cron 默认跟随服务器时区，且 `SMART_POLICY` 曾被错误映射为 `IGNORE_MISFIRES`。如需保持旧时刻，请显式将默认时区配置为原部署时区；如需保持旧 misfire 行为，请显式选择 `IGNORE_MISFIRES`。

## 告警事件

| 事件 | 触发条件 |
|------|---------|
| `TaskFailureEvent` | 任务执行失败 |
| `TaskTimeoutEvent` | 任务超时 |
| `TaskSlowEvent` | 执行时间超过阈值 |
| `TaskConsecutiveFailureEvent` | 连续 N 次失败（N 为 `consecutive-failure-threshold`） |
| `ReliableAuditFailureEvent` | 可靠审计的开始或完成写入失败 |

## 指标

Micrometer 存在时自动注册以下指标：`co_quartz_job_executions_total`（标签 `job`、`state`）、`co_quartz_job_execution_duration`、`co_quartz_job_retries_total`、`co_quartz_job_timeouts_total`、`co_quartz_job_active`、`co_quartz_log_queue_size`、`co_quartz_log_dropped`、`co_quartz_log_write_failures`、`co_quartz_log_permanent_failures`、`co_quartz_log_unflushed` 与 `co_quartz_audit_started`。耗时单位为秒（Micrometer Timer 标准）。任务标签最多保留 `max-metric-job-tags` 个，额外动态任务聚合为 `job=other`。

## Actuator 运维端点

应用自行引入 Spring Boot Actuator 且显式暴露 `coquartz` 后，可通过 `/actuator/coquartz` 查看只读任务与日志健康摘要。端点不提供暂停、恢复、触发、删除或重调度操作；认证与授权由应用自行配置。

## 服务一览

| Bean | 所在模块 | 用途 |
|------|---------|------|
| `CoQuartzScheduler` | core | 调度器封装，提供便捷方法 |
| `TaskAdminService` | core | 任务管理：暂停/恢复/删除/触发/重调度 |
| `TaskQueryService` | core | 任务查询：列表/详情/运行中/触发状态 |
| `QuartzTaskBuilder` | core | 流式构建动态任务 |
| `TaskLogRepository` | core (接口) | 日志持久化接口 |
| `TaskExecutionLogWriter` | core (接口) | 同步/异步无关的执行日志写入扩展点 |
| `AsyncTaskLogService` | core (兼容接口) | 旧版异步日志 API，后续版本将逐步废弃 |
| `TaskMonitoringService` | core (接口) | 监控统计接口 |
| `TaskLogService` | jdbc | 分页查询、统计、清理 |
| `AlertEventPublisher` | core | 告警事件发布 |

## 模块结构

```
co-quartz/
├── co-quartz-core/       # 注解、EnhancedJob、CoQuartzScheduler、QuartzTaskBuilder、
│                         # 告警、指标、Actuator 端点、服务接口与 JobFactory
├── co-quartz-jdbc/       # TaskLogRepository / AsyncTaskLogService 的 JDBC 实现、
│                         # 数据库迁移、可靠审计、日志清理与查询服务
└── co-quartz-starter/    # 默认只聚合 core；日志场景显式添加 JDBC 模块
```

## 异常体系

- `CoQuartzException`（基类）
  - `CoQuartzConfigurationException` — 配置错误
  - `CoQuartzSchedulingException` — 调度失败
  - `CoQuartzExecutionException` — 执行失败

## 技术栈

- Java 17+ / Spring Boot 3.3.7 / Quartz 2.3.2 / Spring Data JDBC

## 测试

默认测试不依赖 Docker，也不会解析 Testcontainers、MySQL 或 PostgreSQL 驱动：

```bash
mvn test
```

维护者或 CI 可显式开启数据库集成测试 Profile；它才会下载测试专用依赖，并通过 Testcontainers 执行 MySQL、PostgreSQL 容器验证：

```bash
mvn verify -Pintegration-tests
```

该命令需要可用的 Docker 守护进程。Profile 只影响测试阶段；Testcontainers、Docker Java 和数据库驱动均不会成为公共 jar 的运行时或传递依赖。

## License

[Apache License 2.0](https://opensource.org/licenses/Apache-2.0)

### 增强执行兼容规则（审查修订）

| 原生能力 | 框架行为 |
|---|---|
| `@PersistJobDataAfterExecution` | 仅在业务 Job 声明时保留；非并发任务选择独立持久化 wrapper |
| `@DisallowConcurrentExecution` | 原生注解优先；`concurrent=true` 不会取消原生限制 |
| `InterruptableJob` | 增强层委托 `interrupt()`，并向实际 Quartz 工作线程请求中断；中断不是强制终止 |
| 非并发 + timeout | 业务仍在 Quartz 线程，未真正退出前保持 JobStore 的非并发占用；退出后才重试 |
| 原生 unschedule | 优先于框架重试；增强路径保留标记 |
| 增强任务 refireImmediately | 使用框架有限延迟重试，禁止两套重试循环；普通原生未增强任务维持 Quartz 原义 |

无限忽略中断的业务可能无限占用工作线程，并阻止 `shutdown(wait=true)` 完成。集群不通过 JVM 本地锁控制并发。可靠审计必须使用 `CoQuartzJobFactory` 的受控执行装饰层，JobListener 不负责阻断任务。

传统 Job 由 `CoQuartzJobFactory` 每次创建独立实例：构造器自动注入、字段注入、SchedulerContext → JobDataMap → Trigger 数据属性绑定、初始化与 BeanPostProcessor 代理，再执行并销毁目标实例。不会复用已扫描的 singleton Job。方法任务继续调用原 Spring bean。自定义 JobFactory 不会自动继承这些能力；自定义 Scheduler 必须显式安装 CoQuartzJobFactory，不能直接执行持久化 marker wrapper。

默认 `LogSanitizer` 尽力覆盖 Authorization Bearer/Basic、带引号 JSON 凭据字段、URL 查询参数与常见 URI/JDBC user:password 格式（password/passwd/pwd/secret/token/access_token/refresh_token/api_key 等，忽略大小写）。它不是通用秘密检测器；自定义格式请提供 `LogSanitizer`。库控制的执行记录、失败事件与基础设施诊断会脱敏；`capture-stack-trace=false` 不保存或发布库控制的堆栈。原始业务异常仍按 Quartz 语义抛出，业务自己的日志、Quartz 原生日志与第三方监听器不在库的脱敏边界内，应在部署日志管道另行治理。

可靠审计归属使用 `co-quartz.scheduling.application-namespace`（默认 `default`，共享日志库的不同应用必须配置不同值）、schedulerName、instanceId、fireInstanceId、executionId。V3 升级 SQL 位于 `co-quartz-jdbc/src/main/resources/db/migration/<dialect>/V3__audit_ownership.sql`，旧记录归属保持 NULL。

已取消仅凭超龄 STARTED 自动判定中断的启动恢复。请通过 `recoverConfirmedStopped(cutoff, AuditRecoveryScope)` 显式提供应用、Scheduler、已确认停机的 instanceId 集合和运维证据编号；该工具可由运维重复/周期调用，原子 STARTED 条件更新允许多个恢复者竞争。AUTO instanceId 变化须使用旧节点身份及真实停机证据；远端、归属不明、其他应用记录不自动恢复。时间阈值只筛选候选记录，不是死亡证据。

异步日志采用单消费者、容量或时间触发的 JDBC batch；批次事务失败后回滚，再隔离逐行失败，同身份同结果的重复 ID 可幂等接受。关闭先停止接收，最多等待 shutdown-flush-timeout-ms；超时后 queued 与 in-flight 均计入 unflushed，驱动忽略取消时后台写入仍可能继续。状态快照增加 accepted/persisted/in-flight，拒收和重排队失败计入 dropped，失败重试有上限且无额外无界 ID 缓存。默认日志 JdbcTemplate SQL 超时为 5 秒；连接获取/socket 超时仍需配置 DataSource/驱动。

生产告警执行器使用单线程和容量 256 的有界队列，满时拒绝并增加 `AlertEventPublisher.getDroppedCount()`；不会通过 CallerRunsPolicy 阻塞任务。相同 job/type 的普通失败、日志管道和审计失败事件在 1 秒内合并，可查询 getCoalescedCount()。连续失败状态限 1024 任务、1 小时 TTL，成功清除；仅是本节点窗口，容量淘汰可能重新开始计数。自动配置下审计失败事件也走这个异步通道，存储主异常不受监听器异常影响。手工构造 publisher 且不提供 executor 的旧构造器保持同步兼容。

定义协调仅支持启动或 Scheduler standby 阶段；共享 JDBC 集群更新必须停止/standby 所有节点并采用单写者部署，当前不支持混合版本节点同时协调定义，也不把内容 fingerprint 当发布顺序。暂停后换计划同样要求集群协调停机，单节点 pause-restore 标记不能代替分布式原子更新。定义指纹包含方法 beanName/methodName。

方法删除/重命名不会自动删除未扫描到的任务。使用显式 `DefinitionMigrationPlan(namespace, retiredJobKeys)`：先 preview 检查所有权，再在所有节点停止/standby、单执行者条件下 apply。仅匹配 namespace 且 code-owned 的任务可移除；legacy 无 namespace 需先同版本协调补元数据。可将旧任务保留为 enabled=false 的 tombstone 完成迁移。

清理任务会在重启时协调 retention-days、cron 和时区；关闭日志时移除已持久化的内部清理任务。执行记录时间按 UTC LocalDateTime 映射，耗时用单调时钟。旧版非 UTC 历史数据须按原部署时区离线转换后再按 UTC 清理。每次 cleanup 最多删除 1000 条终态记录（SUCCESS/FAIL/INTERRUPTED），保留 STARTED；积压可通过更密清理计划逐步排空。清理 Job 禁止并发，提供 exec_state/execute_time/id 索引。

查询详情展示业务 delegate/方法来源及带用途的 Trigger 列表（PRIMARY/RETRY/MANUAL/RECOVERY/EXTERNAL），主字段优先使用持久化主 Trigger 身份。`getRunningJobs`/`getLocalRunningJobs` 只代表当前 Scheduler 节点。`listJobs()` 最多 1000 条，可用 listJobs(limit)；Actuator 摘要最多 100 条并标记 LOCAL_SCHEDULER。日志分页 page>=1、size=1..1000，long offset，按 start_time DESC/id DESC 稳定排序；并发插入期间 offset 分页仍可能移动，精确历史遍历应固定时间窗口。

任务级策略统一边界：retryTimes=0..1000、retryInterval=0..60000ms、backoffMultiplier 为有限的 1..100、timeout=0..604800000ms。注解、Builder 和 RetryContext 一致校验；动态 jobData 采用重试快照的可持久化类型/大小约束且禁止 co-quartz 保留键。Boot 自动配置明确拒绝 Quartz useProperties=true（内部元数据需要类型值）；自建 Scheduler 也必须保持该选项为 false。方法任务不支持 @Async（包括类级），会在注册时拒绝；cron/timeZone 仍为字面值，`${...}` 环境配置扩展不在此次缺陷修复中。

独立生产迁移位于 `co-quartz-jdbc/src/main/resources/db/migration/{h2,mysql,postgresql}`：新库顺序 V1→V2→V3；已有旧基线表先备份和核对列，跳过 V1，再执行尚未完成的版本。已包含 V2 列的库只执行 V3。脚本交给 Flyway/Liquibase/部署系统单执行者管理，应用无需 DDL 权限，关闭 auto-create-table。脚本不是任意重复执行安全的；尤其 MySQL DDL 可能隐式提交，部分失败需核对实际列/索引、完成缺失步骤并由迁移工具修复版本记录，禁止盲目重跑。当前 schema-quartz-task-log-*.sql 是全量安装快照，不是旧表升级脚本。自动 Java DDL 只支持单迁移者启动，不支持多节点同时迁移。
