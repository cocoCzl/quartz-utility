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
