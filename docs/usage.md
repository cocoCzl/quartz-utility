# 使用指南

先运行 [README 的无数据库示例](../README.md#快速开始运行第一个任务)，再按需要选择下面的功能。
本指南对应当前源码 `2.1.0-SNAPSHOT`；升级已有应用前请阅读 [升级指南](upgrade.md)。

- [定义任务](#定义任务)：方法、传统 Job、动态任务。
- [查询和管理任务](#查询和管理任务)。
- [调度参数](#调度参数)、[执行语义](#执行语义)、[超时与取消](#超时与取消)。
- [执行日志](#执行日志)、[可靠审计](#可靠审计)。
- [告警](#告警)、[指标](#指标)、[Actuator](#actuator)。
- [配置参考](#配置参考)、[扩展与原生能力](#扩展与原生能力)。

## 定义任务

### 方法任务

在 Spring Bean 上定义 `@QuartzTask` 方法，代码示例见 [快速开始](../README.md#快速开始运行第一个任务)。
方法必须是可通过 Spring Bean 调用的 `public void` 无参实例方法，名称必须显式给出。
`name + group` 是任务身份，不同方法及类任务之间也不能重复。

注解扫描默认开启；`co-quartz.annotation.enabled=false` 会关闭 `@QuartzTask` 和 `@QuartzJob` 的自动注册。
方法任务调用原 Spring Bean，可使用 Spring 代理支持的事务；不支持方法或类上的 `@Async`，注册时会拒绝。
`cron`、`timeZone` 当前按字面值解析，不支持 `${...}` 占位符。

### 需要 JobExecutionContext 时使用传统 Job

以下类放在组件扫描范围内，即可自动注册。每次执行会创建独立的 Job 实例，方法任务则继续使用原 Spring Bean。

```java
package com.example.quartzdemo;

import io.github.cococzl.coquartz.annotation.QuartzJob;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@QuartzJob(name = "report", intervalSeconds = 60, retryTimes = 2,
           retryInterval = 2000, timeout = 10000)
public class ReportJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ReportJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("report fired: {}", context.getFireInstanceId());
    }
}
```

Job 中可通过构造器或字段注入 Spring service。事务建议放到注入的 service，尤其是继承 `QuartzJobBean` 时：其 `execute` 是继承的 final 方法，仅在子类标注 `@Transactional` 不保证拦截该方法。
超时、重试和原生注解的配合见 [执行语义](#执行语义) 与 [扩展与原生能力](#扩展与原生能力)。

### 在运行时创建动态任务

使用 `QuartzTaskBuilder`，不要再给同一任务加声明式注解。下例定义一个无 `@Component`、无 `@QuartzJob` 的业务 Job，随后在 Spring 启动时注册：

```java
package com.example.quartzdemo;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DynamicReportJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(DynamicReportJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.info("report for tenant={}", context.getMergedJobDataMap().getString("tenant"));
    }
}
```

```java
package com.example.quartzdemo;

import io.github.cococzl.coquartz.core.CoQuartzScheduler;
import io.github.cococzl.coquartz.core.QuartzTaskBuilder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DynamicTasks {
    @Bean
    ApplicationRunner registerReport(CoQuartzScheduler scheduler) {
        return args -> QuartzTaskBuilder.newBuilder()
                .jobClass(DynamicReportJob.class)
                .jobName("tenantReport")
                .jobGroup("REPORTS")
                .jobData("tenant", "demo")
                .cron("0 0/5 * * * ?")
                .timeZone("Asia/Shanghai")
                .retryTimes(2)
                .timeout(10000L)
                .schedule(scheduler);
    }
}
```

Builder 默认 Trigger 名为 `TRIGGER_` 加 Job 名，Trigger group 跟随 Job group，可用 `triggerName`、`triggerGroup` 显式指定。
重复 `schedule` 可更新 Builder 拥有的同身份任务，并重建缺失的 Trigger；不允许覆盖代码拥有或外部拥有的任务，也不允许占用其他任务的 Trigger。
更新同身份计划会保留目标 Trigger 的暂停状态；涉及共享 JobStore 时需由应用协调写入，不能把它当作集群发布屏障。
旧版 DEFAULT Trigger 的处理见 [升级指南](upgrade.md#旧-trigger-group)。

创建一个尚不存在的一次性任务，可用 `executeNow(scheduler.getScheduler())`，此时不需要 cron/interval。
它拒绝同身份的已有任务；执行已有任务请用 `TaskAdminService.triggerNow` 或原生 `Scheduler.triggerJob`。

`jobData` 在 RAM 和 JDBC 模式下都只接受可持久化的有限类型：

- `null`、String、基本类型的包装类、UUID、Instant、LocalDate、LocalDateTime、BigDecimal、BigInteger。
- `byte[]` 和精确的 `java.util.Date` 类型；不接受任意 Serializable、集合、Spring Bean 或运行时服务。
- 最多 128 个键，每个键最多 128 字符；字符串最多 16384 字符，字节数组最多 64 KiB，序列化后的整个快照最多 64 KiB。
- 不得使用 `co-quartz.` 前缀的保留键。重试快照复制业务数据，并保留执行关联及原始触发信息。

## 查询和管理任务

以下服务由 core 自动配置，可通过构造器注入，不需要日志模块：

| 服务 | 常用方法 | 适用场景 |
|---|---|---|
| `io.github.cococzl.coquartz.service.TaskQueryService` | `listJobs()`、`listJobs(limit)`、`getJobDetail(name, group)` | 查询定义、来源与 Trigger |
| 同上 | `getLocalRunningJobs()`、`getRunningJobs()` | 当前 Scheduler 节点的运行任务 |
| 同上 | `getTriggerState(name, group)`、`getNextFireTime(name, group)`、`getPreviousFireTime(name, group)` | 查询某个 Trigger |
| `io.github.cococzl.coquartz.service.TaskAdminService` | `pauseJob(name, group)`、`resumeJob(name, group)`、`triggerNow(name, group)`、`exists(name, group)` | 操作运行状态或手动触发 |
| 同上 | `deleteJob(name, group)`、`rescheduleCron(...)`、`rescheduleInterval(...)` | 更改非代码拥有任务的定义 |
| `io.github.cococzl.coquartz.core.CoQuartzScheduler` | `getScheduler()` | 需要原生 Quartz API 时 |

`rescheduleCron` 的基础签名为 `(jobName, jobGroup, triggerName, triggerGroup, cronExpression)`，另有末尾增加 `timeZone, MisfirePolicy` 的重载；`rescheduleInterval` 将 cron 换为 `int intervalSeconds`，另有末尾增加 `MisfirePolicy` 的重载。

声明式任务由代码拥有：可暂停、恢复、手动触发；管理 API 拒绝删除和重调度，需修改代码并按 [定义更新流程](upgrade.md#集群部署与定义更新) 部署。
直接使用原生 Scheduler 会绕过这些管理保护，调用方负责保持所有权和定义一致性。

`listJobs()` 默认最多 1000 条，显式 limit 为 1..1000。
详情包含业务 delegate 类或方法来源，以及 `PRIMARY / RETRY / MANUAL / RECOVERY / EXTERNAL` 用途的 Trigger 列表；主字段优先采用持久化主 Trigger 身份。
运行中列表只覆盖当前节点，不能用于判断其他集群节点已经排空。

## 调度参数

下表同时适用于 `@QuartzTask` 与 `@QuartzJob`，除非另有说明。参数可省略，按默认值生效。

| 参数 | 默认值 | 说明 |
|---|---|---|
| `name` | 空 | 方法任务必须显式指定；类任务留空时使用类简单名 |
| `group` | `DEFAULT` | 任务组；与 name 共同决定身份 |
| `description` | 空 | 描述 |
| `cron` | 空 | Quartz cron 表达式，含秒字段；与 intervalSeconds 二选一 |
| `intervalSeconds` | `0` | 正整数固定间隔，单位秒；与 cron 二选一 |
| `timeZone` | 空 | 使用全局默认时区；可显式指定 `Asia/Shanghai` 等 ZoneId |
| `concurrent` | `false` | 默认同 JobKey 非并发；原生非并发注解仍优先 |
| `enabled` | `true` | 禁用声明式定义；持久化旧定义会参与禁用协调 |
| `misfirePolicy` | `SMART_POLICY` | 错过计划后的处理策略，见下表 |
| `retryTimes` | `0` | 初次执行之后的最大重试次数，0..1000 |
| `retryInterval` | `1000` | 重试间隔毫秒，0..60000 |
| `exponentialBackoff` | `false` | 逐次增加重试延迟，最多 60000 毫秒 |
| `backoffMultiplier` | `1.5` | 有限数值，1..100 |
| `timeout` | `0` | 毫秒，0 表示不限制，最大 604800000；协作式取消 |
| `durable` | `false` | 仅 @QuartzJob：无 Trigger 时是否保留 Job |
| `recoverable` | `false` | 仅 @QuartzJob：请求 Quartz 故障恢复，业务仍需幂等 |

Builder 提供对应链式方法；间隔方法名为 `intervalInSeconds`，另有 `startAt(Date)`、`endAt(Date)`、`triggerName`、`triggerGroup` 和 `jobData`，不提供注解式 `enabled`。
Builder 的 `schedule(CoQuartzScheduler)` 使用全局默认时区，直接 `schedule(Scheduler)` 未指定时区则使用 UTC。

时区优先级：任务显式 `timeZone` → `co-quartz.scheduling.default-time-zone` → 默认 `UTC`，不读取服务器或 JVM 默认时区。
固定间隔按绝对时长运行；Cron 的当地时刻受时区和夏令时规则影响。
非法 cron、非法时区、同时配置或都未配置 cron/interval，以及越界执行策略会导致注册失败。

| MisfirePolicy | Cron | 固定间隔（无限重复） |
|---|---|---|
| `SMART_POLICY` | 补执行一次后继续正常计划 | 跳过错过次数，从下个未来时间继续 |
| `FIRE_NOW` | 立即补执行一次后继续正常计划 | 立即执行一次后继续计划 |
| `IGNORE_MISFIRES` | 保留原始触发时刻，可能密集追赶 | 保留原始触发时刻，可能密集追赶 |

枚举 import 为 `io.github.cococzl.coquartz.enums.MisfirePolicy`。是否被认定为 misfire 还受 Quartz 的 misfire threshold 等原生配置影响。

## 执行语义

重试、Quartz 故障恢复等场景可能重复执行业务，应按至少一次交付考虑业务幂等；这不保证每个错过的时刻都补执行，具体取决于 misfire 策略和存储模式。
RAM JobStore 的计划与在途重试不会跨进程重启保留。

框架重试使用延迟 Trigger，等待重试时不占用业务执行线程。
`retryTimes=2` 表示初次尝试之外最多再执行两次；各次尝试通过 `executionId` 关联，`attempt` 从 1 开始。
JDBC JobStore 可保留已持久化且通过校验的待执行重试。声明式定义变化可能使旧重试失效，不应依赖旧重试承载跨版本业务迁移。

默认非并发约束以 JobKey 为单位，不同 JobKey 不互斥；共享 JDBC JobStore 使用 Quartz 的非并发机制，不依赖 JVM 本地锁。
业务自己发起的异步工作不受方法返回后的执行周期跟踪，因此不应把异步提交当作该任务的业务完成。

## 超时与取消

业务始终在 Quartz 工作线程执行。达到 `timeout` 时，独立定时器向该线程请求中断。
通过 Quartz 的 `interrupt` API 请求取消时，增强层还会向实现 `InterruptableJob` 的业务 Job 委托 `interrupt()`。
这是协作式取消，不保证强制终止、释放数据库连接或其他资源。业务应响应中断并在 `finally` 中释放资源，同时配置客户端连接、读写和事务超时。

业务实际退出之前仍保留该次执行以及 JobStore 的非并发占用，退出后才记录超时终态或安排重试。
永久忽略中断的业务可能长期占用工作线程，并阻止等待式关闭完成。
`timeout-pool` 只为旧 API 兼容保留，已不承载业务执行；新应用无需配置它。

## 执行日志

### 开启数据库记录

1. 在 Starter 之外显式添加 JDBC 模块，并提供数据库驱动：

   ```xml
   <dependency>
     <groupId>io.github.cococzl</groupId>
     <artifactId>co-quartz-jdbc</artifactId>
     <version>2.1.0-SNAPSHOT</version>
   </dependency>
   ```

   模块引入 `spring-boot-starter-jdbc`，不携带生产数据库驱动。驱动版本可由应用的 Spring Boot 依赖管理提供。
2. 配置数据源。日志优先选择名为 `coQuartzLogDataSource` 的 Bean；也可通过 `co-quartz.log.datasource.url` 等配置创建此专用数据源。没有专用数据源时，仅在容器中恰有一个 DataSource 时复用它；多个候选即使有 Primary 也需显式指定。
3. 执行 [日志表迁移](upgrade.md#日志表迁移)，再开启：

   ```yaml
   co-quartz:
     log:
       enabled: true
   ```

默认 `auto-create-table=false`，生产由迁移工具管理 DDL，应用无需 DDL 权限。
仅本地单实例开发可显式设置 `co-quartz.log.auto-create-table=true` 让初始化器建表/补迁移；不能多节点同时执行自动 DDL。

日志模块支持 H2、MySQL、PostgreSQL。开启日志不要求 Quartz 使用 JDBC JobStore；Quartz 的 `QRTZ_*` 表与日志表 `quartz_task_log` 分别管理。
默认不添加 JDBC 模块即可无数据库运行。若应用已引入 JDBC starter，其自身的数据源自动配置要求仍需由应用满足。

### 选择写入方式

| 模式 | 配置前提 | 行为 |
|---|---|---|
| 关闭 | `log.enabled=false`（默认） | 无数据库执行记录；无需设置 async.enabled=false |
| 异步尽力日志 | `log.enabled=true`，`async.enabled=true`（默认） | 有界队列、单消费者，容量或时间触发 JDBC batch |
| 同步尽力日志 | `log.enabled=true`，`async.enabled=false` | 执行路径同步写入；不等于可靠审计 |
| 可靠审计 | `log.enabled=true`，`log.reliable-audit=true` | 同步写 STARTED 和终态，见下节 |

异步日志在队列拒收、有限重试耗尽、进程崩溃或关闭未完成时可能丢失，不能承诺绝对不丢失。
批次事务失败会回滚并隔离逐行失败；同身份同结果的重复 ID 可幂等接受，身份、时间或 origin 冲突会报错，时间比较按数据库列精度进行。
默认日志 JdbcTemplate 的 SQL 超时为 5 秒，连接获取和 socket 超时仍需配置 DataSource/驱动。

关闭先停止接收，最多等待 `shutdown-flush-timeout-ms`；未解决的 queued/in-flight 计入 unflushed。
驱动忽略取消时后台写入可能继续，unflushed 是关闭返回时的快照，晚到写入不回改它。
`flushLogsImmediately()` 会同步等待驱动，不提供相同的关闭时限保证。

`TaskExecutionLogWriter.getPipelineStatus()` 可查看 accepted、persisted、queueSize、inFlight、dropped、writeFailure、permanentFailure、unflushed。
accepted 不含拒收；dropped 也包括已接受但重排队失败的记录；writeFailure 是尝试次数，这些计数不能直接相加作为严格守恒等式。

### 查询、保留与脱敏

开启 JDBC 日志后，可注入 `io.github.cococzl.coquartz.jdbc.service.TaskLogService`，调用 `pageLogs(TaskLogQuery)`、`statistics()`、`statistics(jobKey)`、`cleanup(daysToKeep)`。
`TaskLogQuery` 位于 `io.github.cococzl.coquartz.dto`，通过 `setJobKey`、`setPage`、`setSize` 等设置过滤与分页。
page 从 1 开始，size 为 1..1000，按 `start_time DESC, id DESC` 稳定排序；并发插入时 offset 分页仍会移动，精确历史遍历需固定时间窗口。

执行记录时间使用 UTC LocalDateTime 映射，耗时使用单调时钟；旧版非 UTC 数据需 [离线转换](upgrade.md#历史时间与清理)。
清理计划默认在全局时区每天 02:00 执行，保留 30 天；每次最多删除 1000 条 SUCCESS/FAIL/INTERRUPTED 终态记录，保留 STARTED。
清理任务禁止并发；可提高调度频率消化积压。重启协调 retention-days、cron 与时区，关闭日志时移除持久化的内部清理任务。

默认 `LogSanitizer` 尽力脱敏常见 Authorization、JSON 凭据字段、URL 参数和 URI/JDBC 凭据，但不是通用秘密检测器；特殊格式可自行提供 `LogSanitizer` Bean。
`log.capture-stack-trace=false` 禁止库控制的记录/事件保存或发布堆栈。
业务自己的日志、Quartz 原生日志、第三方监听器仍可能接收到原始业务异常，需在应用日志管道处理。

## 可靠审计

先完成 JDBC 模块、数据源、V1→V3 迁移和日志启用，再设置：

```yaml
co-quartz:
  log:
    enabled: true
    reliable-audit: true
```

可靠审计使用独立的同步生命周期写入，不经过默认异步终态日志队列，也无需修改 `async.enabled`：

- 业务开始前同步写入 STARTED，开始写入失败则不执行业务。
- 业务完成后同步更新同一条尝试记录；结束写入失败使该次 Quartz 执行失败，发布审计失败事件，不因该写入错误重新运行已成功的业务。
- 每次重试各有生命周期记录，以 executionId 与 attempt 关联；业务事务与审计事务没有原子提交保证，进程故障可能留下 STARTED。

此模式增强审计故障的可见性，不提供 exactly-once 或绝对不丢失保证。缺少 `ReliableAuditService` 时启动失败；必须使用安装了 `CoQuartzJobFactory` 的受控执行装饰层，普通 JobListener 无法承担执行前阻断。

共享日志库的不同应用应设置不同的 `co-quartz.scheduling.application-namespace`（默认 `default`）。
记录归属同时包含 namespace、schedulerName、instanceId、fireInstanceId 与 executionId。

### 恢复已确认停机节点的 STARTED

不会仅凭记录超龄自动标记中断，也不会在启动时自动恢复。
运维先确认节点已经停止，再调用 `ReliableAuditService.recoverConfirmedStopped(LocalDateTime cutoff, AuditRecoveryScope scope)`；cutoff 使用 UTC。
`AuditRecoveryScope` 位于 `io.github.cococzl.coquartz.service`，构造参数依次是应用 namespace、Scheduler 名称、已确认停机的 instanceId 集合、运维证据编号。

时间阈值只筛选候选记录，不是节点死亡证据。AUTO instanceId 变化后要使用旧节点身份；归属不明、其他应用或未确认停机节点不应恢复。
该操作采用 STARTED 条件更新，允许重复调用和多个恢复者竞争。
旧恢复接口与配置的变化见 [兼容变化](upgrade.md#接口与兼容变化)。

## 告警

告警通过 Spring 事件交给应用处理，推送邮件、Webhook 等渠道由应用实现。例如在扫描范围内增加：

```java
package com.example.quartzdemo;

import io.github.cococzl.coquartz.event.TaskFailureEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class TaskAlerts {
    private static final Logger log = LoggerFactory.getLogger(TaskAlerts.class);

    @EventListener
    public void onFailure(TaskFailureEvent event) {
        log.warn("task failed: job={}, error={}", event.getJobKey(), event.getErrorMessage());
    }
}
```

事件均位于 `io.github.cococzl.coquartz.event`：

| 事件 | 条件与依赖 |
|---|---|
| `TaskFailureEvent` | 任务失败；不依赖 JDBC |
| `TaskTimeoutEvent` | 超时尝试完成后的通知；不代表定时器触发时业务已退出 |
| `TaskSlowEvent` | monitoring.enabled=true、阈值 >0，且成功尝试耗时超过 slow-task-threshold-ms |
| `TaskConsecutiveFailureEvent` | monitoring.enabled=true 且达到连续失败阈值 |
| `TaskLogPipelineEvent` | 日志管道异常；需日志写入路径 |
| `ReliableAuditFailureEvent` | 审计开始/完成写入失败；需可靠审计 |

执行事件的 `getCorrelation()` 可关联 executionId、attempt、schedulerInstanceId、finalAttempt；非执行事件可为空。
`monitoring.enabled=false` 关闭指标、慢任务和连续失败告警，保留基础失败/超时事件。

自动配置使用单线程、容量 256 的异步告警队列，满时拒绝而不在任务线程运行监听器，拒收可通过 `AlertEventPublisher.getDroppedCount()` 观察。
相同 job/type 的普通失败、日志管道和审计失败事件在 1 秒内合并，计数见 `getCoalescedCount()`；不能把事件数当成精确执行数。
连续失败仅是本节点完成回调顺序的窗口；增强路径在最终失败尝试时计入，成功回调清除。
最多跟踪 1024 个任务、1 小时 TTL，容量淘汰后可能重新计数，不是集群聚合。
手工使用不传 executor 的旧 publisher 构造器仍同步执行，可能受监听器阻塞。

## 指标

应用需引入 Micrometer 并提供 `MeterRegistry` Bean，且保持 `co-quartz.monitoring.enabled=true`；Starter 不强制携带指标后端。
通常通过 `spring-boot-starter-actuator` 及所需 registry 接入；Prometheus 抓取还需对应 registry 和端点暴露。

| 注册的指标名 | 含义/前提 |
|---|---|
| `co_quartz_job_executions_total` | 每次尝试的成功/失败计数，含重试，标签 job、state |
| `co_quartz_job_execution_duration` | 每次尝试耗时 Timer，单位秒 |
| `co_quartz_job_retries_total`、`co_quartz_job_timeouts_total` | 重试和超时计数 |
| `co_quartz_job_active` | 本节点正在执行的尝试，含原生观察与增强路径 |
| `co_quartz_job_interrupt_requests_total`、`co_quartz_job_termination_unconfirmed` | 中断请求和尚未确认退出状态 |
| `co_quartz_alert_dropped`、`co_quartz_alert_coalesced` | 告警拒收/合并状态 |
| `co_quartz_log_queue_size`、`co_quartz_log_dropped`、`co_quartz_log_write_failures` | 需日志 writer 的队列及失败状态 |
| `co_quartz_log_permanent_failures`、`co_quartz_log_unflushed` | 永久失败与关闭未解决状态 |
| `co_quartz_audit_started`、`co_quartz_audit_sample_age_seconds`、`co_quartz_audit_refresh_failures` | 需日志 repository 的 STARTED 采样、样本年龄与刷新失败 |

这些是注册名，导出格式以所用 registry 为准。任务 job 标签最多接纳 `max-metric-job-tags` 个，额外任务归入 `job=other`。
逻辑执行用 executionId 关联，不能把含 retry 的尝试计数当成去重后的业务次数。
审计 gauge 每 5 秒在后台刷新，首次可为 NaN；抓取指标本身不访问日志数据库。

## Actuator

应用自行添加 `org.springframework.boot:spring-boot-starter-actuator`。通过 HTTP 查看时还需 Web 应用环境，并显式暴露：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,coquartz
```

默认路径为 `/actuator/coquartz`，提供只读任务和日志健康摘要，最多列 100 个任务，运行范围标为 `LOCAL_SCHEDULER`。
日志关闭时相应数据不可用；端点不提供暂停、恢复、触发、删除和重调度操作。
认证与授权由应用配置；端点读取日志摘要可能访问数据库，和指标后台缓存采样不同。

## 配置参考

**下列选项都不要求全部配置。** 首次接入可省略整个 `co-quartz` 配置块；按功能启用相应选项即可。
下表使用完整属性名；时间后缀 ms 表示毫秒，未写出的配置均有默认值。

### 核心与调度

| 属性 | 默认值 | 生效条件/用途 |
|---|---|---|
| `co-quartz.annotation.enabled` | `true` | 自动扫描两种任务注解 |
| `co-quartz.scheduling.default-time-zone` | `UTC` | 未显式指定时区的 Cron，以及清理计划 |
| `co-quartz.scheduling.application-namespace` | `default` | 声明式定义与审计归属，1..200 字符；共享日志库的不同应用应区分 |
| `co-quartz.monitoring.enabled` | `true` | 指标、慢任务、连续失败告警总开关；指标仍需 MeterRegistry |
| `co-quartz.monitoring.slow-task-threshold-ms` | `30000` | 非负，0 禁用慢任务告警；monitoring 开启时使用 |
| `co-quartz.monitoring.consecutive-failure-threshold` | `3` | 正数；本节点连续失败窗口 |
| `co-quartz.monitoring.max-metric-job-tags` | `100` | 非负；指标 job 标签准入上限 |

Quartz 本身遵循 Spring Boot 配置，如 `spring.quartz.job-store-type` 默认 `memory`、`spring.quartz.auto-startup` 默认 `true`。
共享存储、表初始化与集群配置见 [部署指南](upgrade.md#集群部署与定义更新)。
必须保持 `spring.quartz.properties.org.quartz.jobStore.useProperties=false`（Quartz 默认值）；内部元数据需要类型值，自动配置拒绝 true，自建 Scheduler 也必须遵守。

### 日志与数据源

这些属性需要 JDBC 模块与可用数据源才有数据库功能。全局配置校验仍要求填写的数值、cron 和时区有效。

| 属性 | 默认值 | 生效条件/用途 |
|---|---|---|
| `co-quartz.log.enabled` | `false` | 开启日志数据库自动配置 |
| `co-quartz.log.auto-create-table` | `false` | 日志开启时，本地单迁移者开发便利开关 |
| `co-quartz.log.retention-days` | `30` | 正数；终态日志保留天数 |
| `co-quartz.log.cleanup-cron` | `0 0 2 * * ?` | 有效 Quartz cron；使用全局默认时区 |
| `co-quartz.log.capture-stack-trace` | `true` | 库控制的执行记录及失败事件堆栈 |
| `co-quartz.log.reliable-audit` | `false` | 同步生命周期审计；要求 log.enabled=true 和 ReliableAuditService |
| `co-quartz.log.datasource.url` | 未设置 | 设置后创建名为 coQuartzLogDataSource 的专用日志数据源 |
| `co-quartz.log.datasource.username` | 未设置 | 专用数据源凭据 |
| `co-quartz.log.datasource.password` | 未设置 | 专用数据源凭据，建议外部注入 |
| `co-quartz.log.datasource.driver-class-name` | 未设置 | 可选 JDBC 驱动类；未设时由 DataSourceBuilder 推断 |

专用日志库示例（需自行提供 PostgreSQL 驱动、建库并迁移）：

```yaml
co-quartz:
  log:
    enabled: true
    datasource:
      url: jdbc:postgresql://localhost:5432/co_quartz_log
      username: ${LOG_DB_USER}
      password: ${LOG_DB_PASSWORD}
      driver-class-name: org.postgresql.Driver
```

这里的凭据占位符由 Spring Boot 配置绑定解析，与任务注解不支持占位符的限制不同。

### 尽力日志写入

以下选项仅在日志开启且选用相应 writer 时影响写入，不影响调度和业务线程池。

| 属性 | 默认值 | 条件与约束 |
|---|---|---|
| `co-quartz.async.enabled` | `true` | true 选择异步 writer；false 选择同步 writer，均不表示启用/关闭日志 |
| `co-quartz.async.log-queue-capacity` | `1000` | 异步队列容量，正数 |
| `co-quartz.async.log-batch-size` | `100` | 异步批量上限，1..queue-capacity |
| `co-quartz.async.log-flush-interval-ms` | `1000` | 异步刷新间隔，正数 |
| `co-quartz.async.shutdown-flush-timeout-ms` | `10000` | 异步关闭等待上限，非负 |
| `co-quartz.async.log-write-max-retries` | `3` | 异步失败重试上限，非负 |

### 旧配置兼容

以下配置保留绑定与校验，但不推荐新应用设置：

| 属性 | 默认值 | 当前含义 |
|---|---|---|
| `co-quartz.timeout-pool.core-size` | `2` | 兼容执行器参数，已不承载业务执行；必须 >0 |
| `co-quartz.timeout-pool.max-size` | `5` | 同上，必须 >=core-size |
| `co-quartz.timeout-pool.shutdown-await-ms` | `10000` | 兼容执行器等待参数，必须 >=0；不是业务强制终止时限 |
| `co-quartz.log.reliable-audit-recovery-threshold-ms` | `60000` | 保留正数校验，不再触发启动时超龄恢复 |

## 扩展与原生能力

`co-quartz-core` 提供任务注解、Scheduler/Builder、查询管理服务、事件和扩展接口；`co-quartz-starter` 只聚合 core。
`co-quartz-jdbc` 提供日志持久化、写入、查询统计、可靠审计和清理。

| 扩展点（core 的 service 包） | 用途 |
|---|---|
| `TaskLogRepository` | 自定义日志存储 |
| `TaskExecutionLogWriter` | 与同步/异步无关的执行记录写入；默认自动配置会让位于自定义 Bean |
| `AsyncTaskLogService` | 旧异步 API 兼容接口，新接入优先使用 TaskExecutionLogWriter |
| `ReliableAuditService` | 同步生命周期审计与证据恢复 |
| `TaskMonitoringService` | 日志统计/健康查询 |
| `LogSanitizer` | 自定义异常文本脱敏 |

可靠审计幂等比较会读取数据库列精度元数据，无需 DDL 权限；运行期修改 schema 后应重建 repository 或重启。

传统 Job 由 `CoQuartzJobFactory` 每次创建独立实例，支持构造器/字段注入、SchedulerContext → JobDataMap → Trigger 数据属性绑定、初始化、BeanPostProcessor 代理与最终销毁，不复用扫描到的 singleton Job。
自定义 JobFactory 不自动继承这些能力；自建 Scheduler 必须显式安装 CoQuartzJobFactory，不能直接执行持久化 marker wrapper。

| 原生 Quartz 能力 | 与增强层的配合 |
|---|---|
| `@PersistJobDataAfterExecution` | 仅业务 Job 声明时保留；非并发路径使用对应持久化 wrapper |
| `@DisallowConcurrentExecution` | 原生限制优先，concurrent=true 不会取消它 |
| `InterruptableJob` | 委托 interrupt 并向实际工作线程请求中断，仍为协作式取消 |
| `JobExecutionException` 的 unschedule 标记 | 优先于框架重试，保留原生取消调度语义 |
| 增强任务的 refireImmediately | 使用框架有限延迟重试，避免两套循环 |
| 普通未增强原生任务的 refireImmediately | 包括仅可靠审计装饰的任务，保持 Quartz 原义 |

框架异常位于 `io.github.cococzl.coquartz.exception`：`CoQuartzException` 为基类，配置、调度、执行分别使用 `CoQuartzConfigurationException`、`CoQuartzSchedulingException`、`CoQuartzExecutionException`。原生任务仍可能抛出 Quartz 异常。
