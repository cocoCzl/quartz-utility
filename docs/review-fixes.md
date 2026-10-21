# 审查修复记录

每项按回归测试 RED → 最小修复 GREEN 执行；未运行的数据库/集群场景不计为通过。

## R01

确认成立。H2 JDBC 声明式任务失败、关闭上下文和 Scheduler 后重新注册，修复前因内部重试被判冲突而失败（1 error）；修复后 `mvn -q -pl co-quartz-core -Dtest=QuartzJdbcRetryPersistenceTest,QuartzJobReconciliationTest test`：14 通过。

合法重试要求一次性 SimpleTrigger、匹配 JobKey、owner、metadataVersion、executionId、attempt>=2。相同定义保留；定义改变取消旧重试并记录；禁用删除 Job 及其所有 Trigger。旧版无完整内部元数据的重试和无法验证来源的外部/手工 Trigger 均拒绝迁移，需运维显式清理后重启。命名前缀不能授予内部身份。

未验证：MySQL/PostgreSQL、多节点恢复、Quartz recovery Trigger 与运行中手工注册；不声称支持运行中重新扫描。

## R02

确认成立。真实 RAM Scheduler 的不响应中断业务，修复前在 300ms 内观察到第二个业务执行（回归 1 failure）；修复后 `mvn -q -pl co-quartz-core -Dtest=QuartzConcurrencyTest,EnhancedJobTest,QuartzJdbcRetryPersistenceTest test` 通过。

业务直接运行于 Quartz 工作线程；独立 daemon 定时器仅请求中断，直到业务真正退出前 execute 不返回，不释放 JobStore 非并发约束，也不安排重试。增加未确认终止活动数与中断请求计数。正常异常不再跨 Future 包装；关闭中断直接到达业务线程。超时完成事件在确认业务退出后报告。代价：永久忽略中断的业务可能永久占用 Quartz 工作线程，shutdown(wait=true) 同样等待；不能强制终止。timeoutExecutor 旧构造参数暂保留兼容，业务不再提交给它。

未验证：共享 JDBC 多节点超时时序与关闭竞争；不以 JVM 锁宣称集群保证，非并发仍由 Quartz JobStore 持有。

## R03

确认成立。RAM Scheduler 原生非并发 Job 的开始/结束审计故障回归，修复前 2 failures（完成监听器未获通知）；修复后 `mvn -q -pl co-quartz-core -Dtest=NativeAuditExecutionTest,CoQuartzJobListenerTest,CoQuartzJobFactoryTest test` 通过。

CoQuartzJobFactory 在 reliable-audit 模式下也装饰原生 Job。开始失败不执行业务；完成失败作为 JobExecutionException 返回，绝不自动重跑已成功业务。Listener 不写可靠审计、不抛异常控制执行，且增强判断统一解析 true，enhanced=false 不再被跳过。未采用该工厂的自定义 Scheduler 不在可靠审计支持范围，启动校验在后续生命周期项处理。

未验证：真实 JDBC 审计数据库断连、外部自定义 JobFactory。RAM 已验证 Quartz 正常清理并将周期 Trigger 恢复 NORMAL。

## R04

确认成立。真实 RAM 默认非 durable 更新回归修复前 1 error（Jobs added with no trigger must be durable）。改用 `scheduleJobs(Map<JobDetail,Set<Trigger>>, replace)`，Job 和主 Trigger 在一次 JobStore 存储事务中替换，不强制 durable，也无需处理 rescheduleJob 的 null 分支。补 RAM 丢失 Trigger、暂停状态、数据更新及 H2 JDBC 更新回归，并将原先验证错误 API 调用的 mock 测试改为真实 Scheduler 行为断言。

`mvn -q -pl co-quartz-core -Dtest=BuilderScheduleRegressionTest,QuartzTaskBuilderTest,QuartzJdbcRetryPersistenceTest test`：21 通过。暂停恢复仍是后续独立 API，运行中跨节点暂停重调度的窗口不在本项宣称解决（见 R14）。

## R05

确认成立。新增 4 个真实 RAM 场景修复前为 3 failures、1 error。共享 TaskIdentityPolicy 统一 code-owned 保护、Builder 动态所有权、保留组及 Trigger 目标校验；冲突校验在写入前完成。默认 Trigger group 跟随 Job group。executeNow 明确为创建一次任务，已存在时拒绝并指引使用 Scheduler.triggerJob/管理 API 触发存储定义，默认一次性 Trigger 使用 UUID，避免固定身份冲突。

`mvn -q -pl co-quartz-core -Dtest=BuilderScheduleRegressionTest,QuartzTaskBuilderTest,TaskAdminServiceTest,QuartzJobReconciliationTest test` 通过。原生 Scheduler 是明确低层 API，不接管其权限。多个写者同时改变同一 JobKey 的所有权不支持，需应用级单写者；不会通过删除他人任务实现幂等。

## R06

确认成立。3 个新增回归修复前 3 failures；`mvn -q -pl co-quartz-core -Dtest=EnhancedJobTest,QuartzJdbcRetryPersistenceTest test` 修复后 21 通过，包含 H2 JDBC 重启后的 orderId、executionId、attempt 与初始 Trigger 关联。

触发业务数据快照排除 co-quartz 保留键，仅支持内建标量、UUID、部分 java.time、BigDecimal/BigInteger、Date 和 byte[]；最多 128 键、键 128 字符、String 16384 字符、序列化合计 64KiB，不支持运行时 Bean/连接/任意 Serializable 图。重试 Trigger 持久化 originTriggerKey/originScheduledTime；任务日志 triggerKey 采用原始 Trigger，实际尝试仍以 fireInstanceId 区分。R02 的同步线程模型消除了 Future/CompletionException 包装导致的原生标记丢失。

unschedule 优先且不安排重试；增强任务 refireImmediately 归一到框架有限重试，不保留原生立即循环；普通原生未增强任务维持 Quartz 行为。调度重试先于结果持久化，调度失败记录最终失败和独立诊断，保留业务异常作为 suppressed，不再写虚假的非终态。调度与日志数据库不是同一事务，不承诺 exactly-once。

补充组合回归：超时失败后重试调度再失败，原实现在发布超时事件时将终态调度异常强转为 TaskTimeoutException，RED 1 failure。保留独立终止确认状态后 `./mvnw -q -Dmaven.repo.local=/usr/local/repo -pl co-quartz-core -Dtest=EnhancedJobTest test` 全部通过，终态异常与超时事件均保留。

## R07

确认成立。真实 RAM 持久化更新与中断回归修复前 1 failure、1 error；`mvn -q -pl co-quartz-core -Dtest=QuartzNativeCapabilitiesTest,QuartzConcurrencyTest,QuartzJobReconciliationTest test`：18 通过。

只为声明 @PersistJobDataAfterExecution 的非并发业务选择 PersistentNonConcurrentJobWrapper，未给全部 wrapper 加持久化。EnhancedJob 实现 InterruptableJob，向业务接口委托并请求实际工作线程中断。原生 DisallowConcurrentExecution 优先于 concurrent=true，README 加入兼容表。测试验证下一次执行读取到递增 JobDataMap、业务真正收到接口中断信号。

未验证：此持久化组合跨 JDBC 重启、Quartz 集群中断（Quartz interrupt 本身是节点本地操作）。

## R08

确认成立。普通/非并发路径的 Spring 生命周期回归修复前 2 errors；修复后 `mvn -q -pl co-quartz-core -Dtest=JobSpringLifecycleTest,CoQuartzJobFactoryTest,QuartzNativeCapabilitiesTest,NativeAuditExecutionTest test`：16 通过。

统一构造器自动装配、字段装配、三层数据绑定、initializeBean，保留 BPP 生成的代理；每次执行独立实例，ManagedJob 幂等销毁目标，开始审计失败也释放实例。真实 Spring Context 验证 setter 在 PostConstruct 前生效、代理拦截执行、两次实例隔离、PreDestroy 各执行一次。原来只验证 mock autowireBean 调用的测试改为实例隔离行为断言。

边界：自定义 JobFactory/自定义 Scheduler 必须主动采用本工厂；自动检测任意第三方 Scheduler 实际安装的 JobFactory 无公共 Quartz 查询 API，尚未实现通用启动校验；QuartzJobBean 与事务代理组合需后续集成补测。不声称仅凭装配调用保证所有第三方代理语义。

## R09

确认成立。H2 缺失/冲突/身份不符回归修复前失败；缺少 ReliableAuditService 的启动回归在 log.enabled=true 下确认修复前启动成功而失败。修复后 `mvn -q -pl co-quartz-jdbc -am -Dtest=JdbcTaskLogRepositoryTest,JdbcReliableAuditServiceTest,EnhancedJobTest,NativeAuditExecutionTest,CoQuartzAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：40 通过。

完成先核对执行身份，STARTED 条件更新同时限定 executionId/fireInstanceId/jobKey/attempt；更新零行重新读取，仅同身份、同状态/结果/耗时/终态标记才接受幂等，否则明确报 missing/identity/conflict。未吞数据库异常。可靠审计缺少服务在启动时失败，直接构造增强层也不再静默退化。完成失败异常保留业务结果、executionId、attempt，不重跑已成功业务。修复 retry STARTED 与完成日志都复用原始 Trigger 身份。

## R10

确认成立。新增结构化/传输凭据及基础设施异常日志回归，修复前 11 项中 8 failures。修复后 `mvn -q -pl co-quartz-core -Dtest=DefaultLogSanitizerTest,EnhancedJobTest,AlertEventPublisherTest,CoQuartzJobListenerTest test` 通过。

补 Bearer/Basic、JSON、URL 查询、JDBC/URI 凭据和大小写/cause 格式；普通诊断保持可读，扩展点保留。库受控基础设施日志不再把原 Throwable 直接传给 SLF4J；失败事件再次应用 sanitizer 并遵守 capture-stack-trace。README 明确最佳努力规则与业务/Quartz 原生日志边界，未提供改变异常类型的全局脱敏适配，不破坏原生控制标记。

## R11

确认成立。无停机证据的 local/null 归属恢复回归修复前失败。`mvn -q -pl co-quartz-jdbc -am -Dtest=JdbcReliableAuditServiceTest,JdbcTaskLogRepositoryTest,SchemaInitializerTest,QuartzJdbcRetryPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false test`：22 通过。

新增 V3 nullable 归属/原始 Trigger 字段及三方言升级 SQL。记录写入 applicationNamespace/schedulerName/instance/fire/execution，增强 STARTED/complete 复用同一身份。移除启动时 age-only 状态改写；旧 recoverInterruptedBefore 保留 API 但安全 no-op。新受控恢复入口要求 AuditRecoveryScope 明确停机节点与证据编号，仅恢复完全匹配归属；可重入并发恢复用 STARTED 条件更新。运维负责验证停机证据，不能把超龄或本机同 instanceId 视为已停止。

未验证：真实多节点、AUTO 换号、外部运维证据系统。未引入靠时间判死的自动周期扫描。调用者可在获得证据后周期重试工具，短重启不会永久错过阈值。

## R12

确认成立。关闭后拒收及忽略中断数据库回归修复前 1 failure、1 timeout。修复后 `mvn -q -pl co-quartz-jdbc -am -Dtest=JdbcAsyncTaskLogServiceTest,JdbcTaskLogRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`：25 通过。

新增兼容默认 insertBatch 接口，JDBC 实现事务 batchUpdate、失败回滚后逐行隔离，重复 ID 核对身份与结果。异步写统一单消费者，容量阈值立即提交、每轮继续多批，重试延至下一轮，retry 次数随有界队列项保存，无废弃 ID 缓存。shutdown 停止接收并仅在独立消费者执行排空，调用方有界 await，queued+in-flight 在超时后仍可见。计数包含 accepted/persisted/in-flight/dropped/permanent-failed。已验证阈值触发、部分失败/重复 ID、慢数据库与有界关闭；驱动不响应取消时不会宣称写入已完成。

## R13

确认成立。阻塞告警消费者、提交 2000 条后的容量回归修复前失败。`mvn -q -pl co-quartz-jdbc -am -Dtest=AlertEventPublisherAsyncTest,AlertEventPublisherTest,JdbcReliableAuditServiceTest,CoQuartzAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`：17 通过。

生产线程池队列 256，AbortPolicy，发布层捕获拒绝并计 dropped；job/type 合并 1 秒重复故障，计 coalesced。failureWindows 最大 1024、1 小时 TTL；成功清理，缓存容量通过 5000 个删除任务场景验证。JDBC 自动配置审计失败事件转入同一异步 publisher，保留原始数据库异常，不使用 CallerRunsPolicy。

## R14

确认方法绑定 fingerprint 缺失、live rescan 不安全与删除迁移缺口。2 个回归修复前失败；`mvn -q -pl co-quartz-core -Dtest=QuartzJobReconciliationTest,QuartzJobAnnotationProcessorTest test`：31 通过（包括新增迁移预览、跨 namespace 拒绝、幂等 tombstone）。H2 声明式重启此前单独验证通过。

fingerprint v2 加入 beanName/methodName 并存储绑定元数据、应用 namespace。拒绝 active Scheduler live rescan；显式 DefinitionMigrationPlan 支持预览与受控删除，仅自己 namespace/code-owned，未扫描任务不自动删除。明确不支持共享 JDBC 混合版本多写者，必须所有节点 standby/停止和单写者发布；不宣称已解决暂停重调度的跨节点窗口。

未验证：真实 MySQL/PostgreSQL 多节点更新与暂停竞争。采用明确支持边界，未引入只靠摘要或 JVM 锁的虚假版本协调。

## R15

确认成立。更新清理配置、保留 STARTED 回归修复前 2 failures。`mvn -q -pl co-quartz-jdbc -am -Dtest=LogCleanupRegistrarTest,JdbcTaskLogRepositoryTest,SchemaInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test`：20 通过。

内部清理定义协调更新/禁用删除，额外自动配置在日志关闭时仍执行移除；冲突身份拒绝覆盖。执行时间 UTC、耗时 nanoTime，终态每次限 1000 条、STARTED 不删，增加索引及清理非并发标记。旧非 UTC 历史记录需按旧部署时区离线迁移，不能猜测历史时区自动改写。

未验证：真实数据库大表锁/IO 压测、非 UTC 历史迁移；禁用迁移自动配置仍待最终 Boot 全量验证。

## R16

确认成立。非法分页和 retry 优先于 cron 的回归修复前失败。`mvn -q -pl co-quartz-jdbc -am -Dtest=TaskQueryServiceTest,JdbcTaskLogRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false test`：19 通过。

存储主 Trigger 身份并提供用途列表，展示 delegate/方法来源；运行查询明确 local。分页校验和 long offset、复合排序，已有时间窗口过滤保留；listJobs 与 Actuator 限量。未引入游标分页或集群运行全景，不宣称动态插入下 offset 分页快照一致。

## R17

确认参数校验和 Async 组合问题；新增策略/Async 回归修复前失败，useProperties 原先只产生 Quartz 不可读配置错误，现由工厂 Customizer 先给明确拒绝原因。`mvn -q -pl co-quartz-core -Dtest=TaskPolicyValidationTest,QuartzJobReconciliationTest,QuartzJobAnnotationProcessorTest,QuartzTaskBuilderTest,RetryContextTest,CoQuartzAutoConfigurationTest test`：64 通过。

共享验证器限制负数、NaN/Infinity、次数和延迟上界，首退避也受边界约束；Builder 持久数据类型/大小/保留键验证。拒绝 Async 和 Boot useProperties=true，namespace 长度校验。占位符/Profile 扩展属于报告第三批未承诺功能，未伪装成回归实现；专用 DataSource/JobFactory 完整 Boot 组合在最终 E2E 检查，不声称所有第三方配置已兼容。

## R18

确认成立。跨 schema 元数据回归修复前失败，独立版本脚本回归修复前缺资源。补各方言 V1 旧基线建表、V2 增列/索引及已有 V3；自动 metadata 限定当前 catalog/schema。`mvn -q -pl co-quartz-jdbc -am -Dtest=SchemaInitializerTest -Dsurefire.failIfNoSpecifiedTests=false test`：7 通过，包含 H2 原生及 MySQL/PostgreSQL 兼容模式脚本升级并保留旧数据。

README 明确单迁移者、非幂等脚本、部分失败恢复、Flyway/Liquibase/部署工具、无应用 DDL 权限路径；未声称兼容模式等于真实三方言验证，真实 MySQL/PostgreSQL 待集成环境。

## R19

确认成立。慢数据库指标抓取回归修复前耗时 305ms、失败；并发标签预算场景也加入验证。`mvn -q -pl co-quartz-core -Dtest=CoQuartzMetricsTest,CoQuartzJobListenerTest,AlertEventPublisherTest,AlertEventPublisherAsyncTest,EnhancedJobTest test`：41 通过。

标签同步准入；原生观察路径增加 active 成对计数、连续失败和慢任务事件；审计指标独立后台刷新、缓存/失败计数/样本年龄，不在 scrape 访问数据库。告警关联跨异步 dispatch 保留并清理 ThreadLocal，补节点/attempt/finalAttempt。自动配置绑定告警丢弃和合并 gauge。文档明确尝试计数、节点本地连续失败完成顺序以及非集群聚合。

未验证：真实网络故障/无限 JDBC 驱动挂起；采样线程 daemon 且 close 请求取消，不强制终止第三方驱动。
