# 升级与部署指南

本指南描述升级到当前源码 `2.1.0-SNAPSHOT` 时需要处理的兼容影响。
首次接入请先看 [README](../README.md)，功能配置见 [使用指南](usage.md)。当前开发版的本地获取方式见 [源码构建](../CONTRIBUTING.md#源码构建)。

## 升级步骤

1. 备份 Quartz 调度库与日志库，核对旧版本的时区、Trigger 身份、数据表列及索引。
2. 使用日志时，先按下节迁移表结构，再部署依赖当前 JDBC 模块的应用；不使用日志时无需迁移日志表。
3. 对照 [默认行为变化](#默认行为变化) 与 [接口与兼容变化](#接口与兼容变化) 调整配置和调用方。
4. 共享 JDBC JobStore 的定义变更按 [全节点排空与单写者流程](#集群部署与定义更新) 执行；阻止旧版本自动重启和扩容。

## 日志表迁移

执行日志和 Quartz JobStore 分别管理：下列脚本只处理 `quartz_task_log`，不会创建 `QRTZ_*` 调度表。
生产默认 `co-quartz.log.auto-create-table=false`，请交给 Flyway、Liquibase 或部署系统的单个迁移执行者管理，应用无需 DDL 权限。
JDBC 模块本身不引入或自动启用 Flyway/Liquibase。

版本脚本按数据库类型选择：

| 数据库 | 脚本目录 |
|---|---|
| H2 | [db/migration/h2](../co-quartz-jdbc/src/main/resources/db/migration/h2) |
| MySQL | [db/migration/mysql](../co-quartz-jdbc/src/main/resources/db/migration/mysql) |
| PostgreSQL | [db/migration/postgresql](../co-quartz-jdbc/src/main/resources/db/migration/postgresql) |

三个目录都有以下版本，**仅执行所选方言的脚本**：

| 版本 | 内容 | 使用方式 |
|---|---|---|
| `V1__initial.sql` | 基础执行记录表 | 仅新库 |
| `V2__execution_correlation.sql` | attempt、is_final_attempt、execution_id、fire_instance_id、scheduler_instance_id、definition_version 及查询索引 | 基础表升级 |
| `V3__audit_ownership.sql` | scheduler_name、application_namespace、origin_trigger_key、origin_scheduled_time 及清理索引 | V2 后执行 |

- **全新日志库**：按 V1 → V2 → V3 顺序执行。
- **已有旧基线表**：先备份并核对 V1 列，设置迁移工具基线后跳过 V1，执行尚未完成的 V2、V3。
- **已有 V2 列和索引**：核对实际 schema，仅执行 V3；已有 V3 结构不要再跑相同 ALTER。

应用可从 JDBC jar 中提取对应脚本，或从源码目录交给部署系统。
若现有应用已占用 V1/V2/V3 版本号，应纳入自己的迁移版本序列或独立管理，不要不加检查地把整个路径加入全局 Flyway locations。
这些脚本不维护应用自己的迁移工具历史，需由所选工具记录版本。

脚本不是任意重复执行安全的。MySQL DDL 可能隐式提交，部分失败时应先核对实际列/索引，完成缺失步骤，再修复迁移工具的版本记录，禁止盲目重跑。
V3 的历史记录归属保持 NULL，不会据此推断原应用或自动恢复。
审计幂等比较需要读取列精度元数据；运行时改 schema 后应重建 repository 或重启应用。

`schema-quartz-task-log-*.sql` 是完整安装快照，**不能当作旧表升级脚本**，也不要与 V1→V3 重复执行。
本地可显式启用 `auto-create-table=true` 使用 Java 初始化器，但只支持单迁移者启动，不支持多节点同时自动迁移。

### 历史时间与清理

当前执行记录以 UTC LocalDateTime 映射，耗时使用单调时钟。
旧版按服务器当地时间写入的数据，需先明确原部署时区并离线转换时间列，再按 UTC 查询和清理；不能把历史当地时间直接视作 UTC。

清理任务重启时会协调 retention-days、cron 和全局时区；关闭日志时会删除已持久化的内部清理任务。
每次最多删除 1000 条过期终态记录，STARTED 保留；有积压可调高清理频率。

## 默认行为变化

| 项目 | 当前行为 | 升级时要做什么 |
|---|---|---|
| Starter 依赖 | 只聚合 core，不再传递引入日志 JDBC 模块 | 需要日志时显式添加同版本 co-quartz-jdbc 和数据库驱动 |
| 执行日志 | 默认关闭 | 完成迁移、配置数据源后显式设置 log.enabled=true |
| 日志写入 | 日志开启后默认异步尽力；async.enabled=false 选择同步尽力写入 | 关闭日志只需 log.enabled=false；无需联动关闭 async |
| DDL | 默认不自动建表/升级 | 生产采用单执行者版本迁移 |
| 时区 | 默认 UTC，不读取 JVM/服务器默认时区 | 如需保留旧触发时刻，显式设置原部署时区 |
| SMART_POLICY | 使用 Quartz 的 SMART 行为，不再映射成 IGNORE_MISFIRES | 需保留追赶行为时显式选 IGNORE_MISFIRES，评估密集触发 |
| 超时 | 协作式取消，业务留在 Quartz 工作线程直到退出 | 业务需响应中断，检查关闭等待与资源超时策略 |
| 监控开关 | monitoring.enabled=false 关闭指标、慢任务、连续失败告警 | 基础失败/超时事件仍保留 |
| 可靠审计恢复 | 不再按 STARTED 超龄自动恢复 | 使用带已停机节点证据的 recoverConfirmedStopped |

重试保持有限延迟 Trigger 语义；JDBC 重启保留通过校验的待执行重试及 payload/origin 关联，原生 unschedule 优先。
Quartz `useProperties=true` 被明确拒绝，需保持 false。方法任务也不接受方法/类级 `@Async`。
任务策略边界与 jobData 支持类型见 [调度参数](usage.md#调度参数) 和 [动态任务](usage.md#在运行时创建动态任务)，RAM 模式也执行同样的可持久化约束。

## 旧 Trigger group

Builder 的默认 Trigger group 现在跟随 Job group。
旧版本为非默认 Job group 创建的任务，可能仍有同名、同任务的 `DEFAULT` Trigger。
此时直接使用新的隐式 group 更新会被拒绝，避免留下两套计划。

可选择：

- **保留原身份**：Builder 显式调用 `.triggerGroup("DEFAULT")`，继续更新原 Trigger。
- **迁移身份**：在全节点排空、standby、单写者条件下核对旧 Trigger 所属任务，显式移除旧计划并创建新 group 的计划，核对只保留一个主 Trigger，再恢复调度。

不要只改 group 并同时留下旧计划；Trigger 身份属于其他任务时也不得覆盖。

## executeNow 与已有任务

`QuartzTaskBuilder.executeNow(Scheduler)` 只创建尚不存在的一次性任务，连同非 durable Job 一起原子注册；已有同身份 Job 时会抛出异常。
它不再承担“覆盖已有任务再立即执行”的含义。

触发已有存储定义请迁移到 `TaskAdminService.triggerNow(name, group)` 或 `Scheduler.triggerJob(JobKey)`。
更改动态计划用 Builder 的 `schedule`；更改声明式任务应更新代码定义并受控部署。

## 接口与兼容变化

- `TaskExecutionLogWriter` 是同步/异步无关的日志写入扩展点。旧 `AsyncTaskLogService` 接口保留兼容，新实现优先使用 writer；不要将 async.enabled=false 解释为关闭日志。
- `ReliableAuditService.recoverInterruptedBefore(cutoff, instanceId, clustered)` 保留签名但 JDBC 实现不再更新记录。迁移到 `recoverConfirmedStopped(cutoff, AuditRecoveryScope)`，提供 namespace、Scheduler 名称、已确认停机的旧 instanceId 集合与证据编号，cutoff 按 UTC 传入。
- `reliable-audit-recovery-threshold-ms` 保留绑定与正数校验，但不再驱动启动恢复。`timeout-pool.*` 保留兼容执行器配置，已不承载业务执行。
- DTO、告警事件增加执行关联/归属字段。`AsyncLogPipelineStatus` 增加 accepted/persisted/in-flight，保留五参数构造器；JSON 输出及 record 的 equals/hashCode/toString 会变化，严格反序列化或快照比较的调用方需调整。
- `getRunningJobs()` 仅代表当前 Scheduler 节点，另提供语义明确的 `getLocalRunningJobs()`。不能将其当作集群排空证明。
- `listJobs()` 最多 1000 条，日志分页 page>=1、size=1..1000；详情包含主/重试/手动/恢复/外部 Trigger 用途。调用方不要再假设首个 Trigger 就是主计划。
- 可靠审计要求可用服务与受控 JobFactory，缺少服务启动失败；完成记录的身份、时间或 origin 冲突会报错，不能静默覆盖。

原生 Job 注解、Spring 实例生命周期与 refire/unschedule 的配合见 [扩展与原生能力](usage.md#扩展与原生能力)。

## 集群部署与定义更新

### 准备共享调度存储

内存模式仅适合不需要共享调度状态的应用。集群需所有节点使用同一套 Quartz JDBC JobStore 表和同一 schedulerName，每个节点 instanceId 唯一，连接池、驱动、数据库及节点时钟由部署环境保障。
Quartz 表按所用 Quartz 版本与数据库方言单独安装，生产不要让每个节点自动初始化或重建调度表。
日志库可与调度库独立；多应用共享日志库时须使用不同 application-namespace。

以下仅是 **Quartz 集群配置片段**，前提是已提供正确的 DataSource、数据库驱动、Quartz 表及数据库方言对应的 delegate：

```yaml
spring:
  quartz:
    scheduler-name: reports-scheduler
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    properties:
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 10000
      org.quartz.jobStore.useProperties: false
co-quartz:
  scheduling:
    application-namespace: reports-service
```

这个片段不替代 DataSource、数据库方言与线程池配置；多个 DataSource 时还需明确 Boot 的 Quartz 数据源选择。
同一套 JobStore 的节点应使用一致的任务代码与元数据配置。

### 发布或退休代码定义

定义协调仅支持 Scheduler 未启动或处于 standby，并拒绝本节点仍有执行任务时修改/退休定义。
`standby` 和当前执行列表都是节点本地状态，standby 不终止正在执行的业务；本地检查只是点时快照，不是原子发布屏障。

共享 JobStore 的定义变更必须由部署系统完成：

1. 停止或 standby **所有**旧节点，阻止并发 start、自动重启与扩容。
2. 确认所有业务执行和在途分派已经排空。超时请求已发出但业务未退出不算排空，应等待退出或中止部署。
3. 保持单个定义写入者，由尚未启动的新 Scheduler 发布或退休定义；推荐先让所有旧节点完成 `shutdown(true)`。
4. 核对新定义、Trigger 和暂停状态，再启动同一版本的其余节点。

库没有全节点协调、发布锁或 fencing，**不支持混合版本滚动更新**。
同 owner/namespace/兼容元数据的指纹不含发布先后顺序，旧版本重新启动可能覆盖新定义或重建已退休任务。
同版本节点收敛不能证明跨版本发布安全；暂停后换计划同样需要上述部署协调，本地 pause-restore 标记不能代替分布式原子更新。

### 删除或重命名任务

方法删除/重命名不自动删除未被本节点扫描到的旧定义；指纹包含 beanName/methodName，改绑定也属于定义更新。
需显式创建 `io.github.cococzl.coquartz.core.DefinitionMigrationPlan(namespace, retiredJobKeys)`：

- 先 `preview(scheduler)` 核对要退休的 JobKey 和所有权。
- 在全节点排空、standby、单写者条件下执行 `apply(scheduler)`。
- 只允许移除匹配 namespace 且由代码拥有的任务；旧定义缺少 namespace 时，先通过同版本受控协调补充元数据。

也可先保留同身份 `enabled=false` 的声明作为 tombstone，让禁用协调移除持久化旧任务，再按部署计划删除声明。
