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

## R07

确认成立。真实 RAM 持久化更新与中断回归修复前 1 failure、1 error；`mvn -q -pl co-quartz-core -Dtest=QuartzNativeCapabilitiesTest,QuartzConcurrencyTest,QuartzJobReconciliationTest test`：18 通过。

只为声明 @PersistJobDataAfterExecution 的非并发业务选择 PersistentNonConcurrentJobWrapper，未给全部 wrapper 加持久化。EnhancedJob 实现 InterruptableJob，向业务接口委托并请求实际工作线程中断。原生 DisallowConcurrentExecution 优先于 concurrent=true，README 加入兼容表。测试验证下一次执行读取到递增 JobDataMap、业务真正收到接口中断信号。

未验证：此持久化组合跨 JDBC 重启、Quartz 集群中断（Quartz interrupt 本身是节点本地操作）。

## R08

确认成立。普通/非并发路径的 Spring 生命周期回归修复前 2 errors；修复后 `mvn -q -pl co-quartz-core -Dtest=JobSpringLifecycleTest,CoQuartzJobFactoryTest,QuartzNativeCapabilitiesTest,NativeAuditExecutionTest test`：16 通过。

统一构造器自动装配、字段装配、三层数据绑定、initializeBean，保留 BPP 生成的代理；每次执行独立实例，ManagedJob 幂等销毁目标，开始审计失败也释放实例。真实 Spring Context 验证 setter 在 PostConstruct 前生效、代理拦截执行、两次实例隔离、PreDestroy 各执行一次。原来只验证 mock autowireBean 调用的测试改为实例隔离行为断言。

边界：自定义 JobFactory/自定义 Scheduler 必须主动采用本工厂；自动检测任意第三方 Scheduler 实际安装的 JobFactory 无公共 Quartz 查询 API，尚未实现通用启动校验；QuartzJobBean 与事务代理组合需后续集成补测。不声称仅凭装配调用保证所有第三方代理语义。
