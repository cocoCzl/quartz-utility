# Co-Quartz

<div align="center">

**Spring Boot Quartz 定时任务增强工具库 — 自动日志、重试、超时、告警**

[![JDK](https://img.shields.io/badge/JDK-17+-green.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Quartz](https://img.shields.io/badge/Quartz-2.3.2-blue.svg)](http://www.quartz-scheduler.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

## 核心特性

- **注解式任务定义** — `@QuartzJob` 一行注解，自动注册 + 自动增强
- **失败自动重试** — 支持重试次数、间隔、指数退避
- **超时控制** — 防止任务长时间占用资源，超时自动中断
- **自动执行日志** — 每次执行（含每次重试）自动记录到 `quartz_task_log`
- **异步批量写入** — 不阻塞任务执行，大幅降低延迟
- **告警事件** — 基于 Spring `ApplicationEvent`，支持失败/超时/慢任务/连续失败告警
- **任务管理 API** — `TaskAdminService`、`TaskQueryService`、`QuartzTaskBuilder`
- **自动建表** — jdbc 模块启动时自动创建日志表（可关闭）
- **Micrometer 集成** — 可选，classpath 存在时自动注册指标

## 快速开始

### 1. 添加依赖

```xml
<dependency>
  <groupId>io.github.cococzl</groupId>
  <artifactId>co-quartz-starter</artifactId>
  <version>2.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 定义任务

只需实现 `org.quartz.Job` 并加上 `@QuartzJob`，无需 `@Component`：

```java
@QuartzJob(name = "myJob", cron = "0 0 12 * * ?",
           retryTimes = 2, retryInterval = 2000, timeout = 10000)
public class MyJob implements Job {
    @Override
    public void execute(JobExecutionContext context) {
        // 业务逻辑
    }
}
```

### 3. 动态创建任务

```java
@Autowired
private CoQuartzScheduler scheduler;

QuartzTaskBuilder.newBuilder()
    .jobClass(MyJob.class)
    .jobName("dynamicJob")
    .cron("0 0/5 * * * ?")
    .retryTimes(2)
    .timeout(10000L)
    .schedule(scheduler);
```

### 4. 管理任务

```java
@Autowired TaskAdminService admin;   // pause, resume, delete, triggerNow, rescheduleCron/Interval
@Autowired TaskQueryService query;    // listJobs, getJobDetail, getRunningJobs, ...
@Autowired TaskLogService logService;  // pageLogs, statistics, cleanup
```

### 5. 订阅告警

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
  log:
    enabled: true
    retention-days: 30
    cleanup-cron: "0 0 2 * * ?"
    auto-create-table: true
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
  timeout-pool:
    core-size: 2
    max-size: 5
  annotation:
    enabled: true
```

Quartz 本身使用 Spring Boot 标准配置：

```yaml
spring:
  quartz:
    job-store-type: memory
    auto-startup: true
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

启动时自动创建（`co-quartz.log.auto-create-table: true`），也可手动执行 DDL：

- MySQL: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-mysql.sql`
- PostgreSQL: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-postgresql.sql`
- H2: `co-quartz-jdbc/src/main/resources/schema-quartz-task-log-h2.sql`

## @QuartzJob 注解参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | 类名 | 任务名称 |
| `group` | String | "DEFAULT" | 任务组 |
| `description` | String | "" | 任务描述 |
| `cron` | String | "" | Cron 表达式 |
| `intervalSeconds` | int | 0 | 间隔秒数（与 cron 二选一） |
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

## 告警事件

| 事件 | 触发条件 |
|------|---------|
| `TaskFailureEvent` | 任务执行失败 |
| `TaskTimeoutEvent` | 任务超时 |
| `TaskSlowEvent` | 执行时间超过阈值 |
| `TaskConsecutiveFailureEvent` | 连续 N 次失败（N 为 `consecutive-failure-threshold`） |

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
│                         # 告警事件、服务接口、异常体系、自定义 JobFactory
├── co-quartz-jdbc/       # TaskLogRepository / AsyncTaskLogService 的 JDBC 实现、
│                         # DDL、自动建表、日志清理 Job、TaskLogService
└── co-quartz-starter/    # 依赖聚合 POM（core + jdbc）
```

## 异常体系

- `CoQuartzException`（基类）
  - `CoQuartzConfigurationException` — 配置错误
  - `CoQuartzSchedulingException` — 调度失败
  - `CoQuartzExecutionException` — 执行失败

## 技术栈

- Java 17+ / Spring Boot 3.3.7 / Quartz 2.3.2 / Spring Data JDBC

## License

[Apache License 2.0](https://opensource.org/licenses/Apache-2.0)