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
    enabled: true
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

默认日志模式异步尽力写入，不影响业务任务。对审计完整性有要求时可启用可靠审计：

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
| `AsyncTaskLogService` | core (接口) | 异步批量日志写入接口 |
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
└── co-quartz-starter/    # 面向应用接入的 Starter 聚合模块
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
