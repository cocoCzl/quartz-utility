# R01–R20 修复后验收

审查范围：`be91643c6b99bb4f71d574cf604c4318dcd3a632` 至本文件所在 HEAD 的完整差异；原修复收尾点为 `036f95f`。逐项对照实现、测试、原报告附录及 `review-fixes.md`，没有以修复记录代替验收。未增加产品功能，未扩大重构；提交仅保留本地。

结论：**在本文明确的部署和 API 限制下通过；不支持自动集群更新协调、混合版本滚动发布或网络分区下的定义发布。** 本轮发现并补修六处实现缺陷，另补齐两类测试证据。历史完整矩阵与本轮定向结果分开记录，不能把它们拼成当前 HEAD 的完整矩阵结果。

## 新发现，按严重程度排序

| 编号/级别 | 触发条件与影响 | 当前定位 | 回归与处理 |
|---|---|---|---|
| A01 / P1 | 业务抛原生 unschedule/refire，随后完成审计失败；审计异常覆盖控制标记，Quartz 继续计划或丢失 refire | `EnhancedJob.java:227` | 真实 Scheduler 三种标记回归，RED 3 failures；保留标记及 suppressed 业务异常，断言实际 Trigger COMPLETE/NORMAL、refire 次数。`3857a64` |
| A02 / P1 | standby 后本节点业务尚在运行仍协调/退休定义；旧执行可写回 JobDataMap 或安排旧重试 | `QuartzJobAnnotationProcessor.java:82`、`DefinitionMigrationPlan.java:29` | 真实 Scheduler 两个入口，RED 2 failures；写入前拒绝本节点未排空，验证无副作用且排空后成功。`fc430fd`。本地快照不能替代集群发布屏障 |
| A03 / P1 | 同 ID/执行身份/状态/耗时，但时间或 origin 被改变，错误接受为幂等完成/重复批写 | `JdbcTaskLogRepository.java:170` | start/end/execute/originTrigger/originTime 五种输入，RED 5 failures；比较 origin 和按实际列精度转换的时间，不改表、不用任意时间容差。H2、MySQL 8.4、PostgreSQL 16（含秒精度旧时间列）均验证重复提交成功、冲突失败。`b1dca74` |
| A04 / P1 | 基线 Builder 的非默认 Job group 使用 DEFAULT Trigger；升级后默认组跟随 Job，再更新会保留旧 Trigger 并增加新计划 | `QuartzTaskBuilder.java:162` | 真实 Scheduler RED 1 failure；检测同名 DEFAULT Trigger 已属于本任务则拒绝隐式迁组，保留原定义；显式 `triggerGroup("DEFAULT")` 可继续更新且仍非 durable。`94b998d` |
| A05 / P2 | 首批日志写入阻塞期间，新到记录再次达到 batchSize；旧请求标记合并掉刷新信号，新批次等整个 interval | `JdbcAsyncTaskLogService.java:59` | 阻塞首批、再入队整批，RED 实际仅 2/4 条；容量信号保留后自动写完 4 条。没有新生产者时不因失败重排队形成无限刷新。`0399e35` |
| A06 / P2 | 开始/完成可靠审计失败；Quartz 已判失败，执行结果计数却没有 success/fail | `EnhancedJob.java:155`、`:227` | 原生/增强 × start/complete 四种组合 RED 4 failures；每次只计一次 fail、success=0、active=0，完成错误仍包含业务结果。`ba5f879` |

A01–A06 均已修复。A02、A04 涉及调用前置条件收紧，修复前已说明影响。A03 首次真实 MySQL 验证发现 Connector/J 的 DATETIME `DECIMAL_DIGITS` 为 NULL，改用当前库 `information_schema.COLUMNS.DATETIME_PRECISION`；没有吞掉元数据错误或退回宽松比较。

另发现两处覆盖不足：原自动阈值测试调用了 `flushLogsImmediately()`，现移除手工刷新；原 R01 H2 用例关闭了手工注册上下文后才执行，不能单独证明应用上下文重建。新增 `DeclarativeRestartAcceptanceTest` 使用 Boot 自动配置、真实 SchedulerFactoryBean/Hikari/H2、可靠审计，关闭整个上下文再创建新上下文，验证新依赖实例、初始化、pending retry、参数、executionId、attempt、原始 Trigger、不同 fireInstanceId。未关闭增强/审计，未强制 durable。`3880185`。

原超时非并发测试虽配置 retry，第二次业务也可能来自人工触发，不能证明第二次必为 retry。新增实际周期与实际重试两种来源，断言超时未退出时无并发/无重试，释放后分别观察主 Trigger 或 attempt=2 的 retry Trigger。`f6cf885`。

## 测试有效性与重点组合

原报告附录 `ReviewFindingsTest` 是“缺陷存在”断言，未原样纳入成功标准。当前对应断言方向如下：

| 行为 | 当前有效证据 | 证明范围 |
|---|---|---|
| 声明式 + JDBC + pending retry + 重启 | `QuartzJdbcRetryPersistenceTest`、新增 `DeclarativeRestartAcceptanceTest` | H2 Scheduler 重启及真实 Boot 上下文关闭/重建；参数和关联连续。尚未对 MySQL/PostgreSQL 做整应用重启或崩溃恢复 |
| concurrent=false + 忽略 timeout 中断 + 后续触发 | `QuartzConcurrencyTest` 5 项、既有 MySQL 集群两种 timeout 调用 | 实际业务 maxActive=1；直到业务退出 Quartz execute 才结束；新增区分周期/重试来源。永久不退出仍占工作线程 |
| 原生 Job + 可靠审计故障 | `NativeAuditExecutionTest` 8 项、`NativeJdbcAuditFailureTest` 2 项 | 真 Scheduler 收到失败、重复计划恢复 NORMAL；真实 H2 删表模拟开始/完成 SQL 故障，修表后后续触发成功；新增控制标记与指标组合。未模拟物理断网 |
| Builder 非 durable、所有权、不同组同名 | `BuilderScheduleRegressionTest` 6、`QuartzTaskBuilderTest` 18、H2 更新 | 明确断言 `isDurable=false`、更新数据/计划/暂停，冲突无副作用；旧 DEFAULT Trigger 更新有显式迁移边界 |
| 重试关联与原生异常 | `EnhancedJobTest`、H2 restart、原生审计控制测试 | 保留业务参数/原触发/执行 ID，attempt 递增；unschedule 阻止框架 retry；增强 refire 转为有限延迟 retry，仅审计装饰的原生 refire 维持 Quartz 原义 |
| Spring/原生能力 | `JobSpringLifecycleTest` 4、`QuartzNativeCapabilitiesTest` 2、H2 持久化重启 | 构造器/字段注入、绑定先于初始化、BPP 代理、独立执行实例和销毁；注入 service 的真实事务代理；原生 JobDataMap 持久化与 interrupt 委托。QuartzJobBean 继承 final execute 的类级事务不作保证 |
| 审计零行及冲突 | `JdbcTaskLogRepositoryTest` 23、`JdbcReliableAuditServiceTest` 3、容器测试 2 | 缺失、身份、终态、时间/origin 冲突明确失败；重复相同完成通过；STARTED 条件更新后零行重新读取。正常转换、冲突不会靠吞 SQL 异常伪装成功 |
| 异步日志关闭/慢写/脱敏 | `JdbcAsyncTaskLogServiceTest` 11、repository batch、sanitizer/事件/执行测试 | 关闭拒收、有界等待、queued/in-flight 可见；真实 batch、回滚隔离、重复 ID；新测试不手工 flush。库控制路径脱敏、capture-stack-trace=false 省略堆栈 |

没有新增 skip/disable/assumption，也没有设置 testFailureIgnore。既有条件测试的原因见后表。`.durable(true)` 只用于需要无 Trigger 存活的 orphan/所有权隔离夹具；更新默认任务、旧组升级和 Boot 重启均断言 false。JobFactory 每次创建业务实例，未复用 singleton 避开生命周期问题。`useProperties=true`、`@Async` 是明确拒绝的组合；不是通过关闭这些功能证明其“已支持”。

异常捕获也作了区分：观察 Listener 不能阻断 Quartz，故捕获观察失败；异步尽力日志捕获存储故障后增加计数、有限重试；可靠审计存储故障继续抛出。旧 age-only recovery 的安全 no-op 属于移除不可靠行为，不是证明自动恢复成功。

## 集群更新契约

1. `Scheduler.standby()` 和 `getCurrentlyExecutingJobs()` 都只作用于当前 Scheduler 实例。没有全节点协调器、分布式发布锁、发布代次或 fencing。Quartz JobStore 单次 SQL 锁不等于整个定义发布的单写者机制。
2. standby 停止后续获取，**不会终止正在执行的任务**。新增检查只拒绝观察到的本节点在执行任务；它是点时快照，不与其他线程的 start/分派原子互斥，不能据此宣布集群已排空。
3. 推荐部署顺序：阻止旧版本自动重启/扩容 → 所有旧 Scheduler 完成 `shutdown(true)` → 确认业务及在途分派终止 → 完成单执行者数据库迁移 → 由尚未启动的新 Scheduler 单写者协调/退休定义 → 验证定义/暂停状态 → 启动同版本节点。若采用 standby，部署系统必须另行确认所有节点和在途执行均静止，并禁止并发 start/写入。
4. 超时但未确认终止的业务仍算活动。不能因 deadline 到期就发布新定义，也不能把 interrupt 返回当终止证明；等待实际退出或中止此次部署。进程永久不退出时 shutdown(true) 可永久等待；本库不强杀线程。
5. 单写者必须由部署系统的互斥操作、停机窗口和访问控制保证，本库没有自动实现。Builder 所有权检查也是读后写，不承诺多写者竞争时的原子 CAS。
6. **旧节点若重新启动，仍可能把新定义覆盖回旧定义，甚至重新创建退休任务。** 同 owner/namespace 且元数据版本兼容时，fingerprint 只是内容摘要，没有版本先后判断；系统不会识别并拒绝这种回退。namespace/所有权/不支持的元数据或未知 Trigger 冲突会失败；本地非 standby/未排空会失败；“远端旧版本还活着”则不能检测。
7. 四个跨 JVM 同版本注册/更新测试只是特定并发写入时序的收敛证据，不授予任意多写者或混合版本发布支持。网络分区、旧新版本交错重启、发布者故障中途恢复和全部 failover 竞争尚未验证。

## R01–R20 逐项结论

分类针对原问题及本文支持边界；“已修复”不代表所有数据库、故障模型和第三方扩展的笛卡尔积都已验证。

| 项 | 结论 | 独立核对后的依据/限制 |
|---|---|---|
| R01 | 已修复 | 合法内部 retry 身份验证并保留；H2 + Boot 真上下文重建补证。旧不完整元数据和未知外部 Trigger 拒绝，需人工迁移 |
| R02 | 已修复 | 业务留在 Quartz 线程直到退出；RAM 周期/retry、MySQL 共享存储非并发证据有效；不能强制终止 |
| R03 | 已修复 | 审计进入执行层而非 Listener；故障返回 JobExecutionException，Quartz 正常收尾，后续任务可执行 |
| R04 | 已修复 | Job/Trigger 通过 JobStore 同次存储替换；真实 RAM/H2 验证非 durable、数据和暂停 |
| R05 | 通过明确限制解决 | 所有权/组/Trigger 目标保护有效；本轮补旧组升级拒绝；原生 Scheduler 是低层 API，且多写者所有权竞争不支持 |
| R06 | 已修复 | 参数及执行关联持久化，原生异常控制保留；补审计失败组合。retry 与审计不在同一事务，无 exactly-once |
| R07 | 已修复 | 按业务注解选择持久化 wrapper，跨 H2 重启数据继续递增；interrupt 真委托，节点本地范围 |
| R08 | 通过明确限制解决 | 标准工厂生命周期、普通 Job 代理、注入事务 service 已验证；第三方工厂需显式采用该契约，不自动识别任意实际安装工厂 |
| R09 | 已修复 | 零行重读、身份/结果/时间/origin 冲突区分及三方言重复完成；审计失败指标补齐 |
| R10 | 通过明确限制解决 | 库受控日志/事件/数据库副本最佳努力脱敏；原始 Quartz/业务/第三方日志不保证，保留原生异常语义 |
| R11 | 通过明确限制解决 | 归属完整、只凭显式停机证据恢复、STARTED 条件更新；受控人工工具可重入，不提供自动死亡判定或周期扫描 |
| R12 | 通过明确限制解决 | 真 batch、有限重试、有界关闭和容量竞态已修；状态是观察快照，unflushed 是关闭时快照，不是持久化承诺或严格守恒台账，手工 flush 可等待驱动 |
| R13 | 已修复 | 生产告警有界拒绝、合并/丢弃可见、缓存容量/TTL；旧手工同步构造器仍同步，不能对它承诺隔离 |
| R14 | 通过明确限制解决 | 方法绑定指纹、显式退休和本地排空检查有效；集群安全依赖停机/单写者，旧节点版本回退没有技术防线 |
| R15 | 已修复 | 配置更新/关闭清理、UTC、终态每批最多 1000、保留 STARTED/索引；旧非 UTC 数据须运维转换 |
| R16 | 通过明确限制解决 | 主 Trigger/用途/delegate 稳定、分页校验及复合排序、本地范围和条数上限；listJobs 仍先取全量 key，非可扩展游标/集群全景 |
| R17 | 通过明确限制解决 | 策略边界和类型/大小统一；Boot 明确拒绝 useProperties=true/@Async；占位符仍为字面值，新增配置能力不在范围 |
| R18 | 通过明确限制解决 | 三方言独立 V1/V2/V3、旧数据升级、当前 schema 元数据；单迁移者，非幂等 DDL/部分失败由迁移工具处置 |
| R19 | 已修复 | 标签准入受控、后台审计采样、关联透传、原生 active；补 start/complete 审计故障 fail 指标。连续失败仍节点本地完成顺序 |
| R20 | 已修复 | Wrapper/插件版本/CI 启用条件核对，补真实组合而非 mock 调度语义；两套历史矩阵已对账，本轮只补风险测试 |

按上述范围为 11 项已修复、9 项通过明确限制解决；没有留下本轮已确认且未修的实现缺陷。尚未验证的子场景单列，不能将其归入已支持能力。

## 历史完整结果对账

完整构建来源为 `036f95f` 的两次 clean verify，本轮复用而未重跑。Java 21 原始 XML 已在补测前复制到 `/tmp/quartz-acceptance-original-reports`；Java 17 原 XML 已被原 Java 21 clean 覆盖，只保留原日志与当时计数文件，因此 Java 17 历史结果不冒充本轮独立重跑。机器证据路径及精简 XML 索引见 `review-acceptance-results.json`，不提交含完整环境属性的 Surefire XML。

| Java / Boot | 模块 | Profile/条件 | 通过 | 失败/错误 | 跳过 | 发现条目 |
|---|---|---|---:|---:|---:|---:|
| 21 / 3.3.7 | core | 默认 Surefire + mysql.url，含集群 | 208 | 0/0 | 0 | 208 |
| 同上 | jdbc | 默认 Surefire | 47 | 0/0 | 0 | 47 |
| 同上 | jdbc | integration-tests / Failsafe + Docker、mysql/postgresql URL | 4 | 0/0 | 0 | 4 |
| 同上 | starter | 默认 Surefire | 11 | 0/0 | 0 | 11 |
| **21 总计** | | | **270** | **0/0** | **0** | **270** |
| 17 / 3.3.13 | core | 默认 Surefire，未提供 mysql.url | 202 | 0/0 | 5 | 207 |
| 同上 | jdbc | 默认 Surefire，无 integration-tests | 47 | 0/0 | 0 | 47 |
| 同上 | starter | 默认 Surefire | 11 | 0/0 | 0 | 11 |
| **17 总计** | | | **260** | **0/0** | **5** | **265** |

总发现数 `270 - (260 + 5) = 5`：JDBC Profile 增加 **4** 次调用；core 集群类有 4 个普通测试及 1 个参数化方法，禁用时 JUnit 计 5 个跳过条目，启用时参数化方法展开 2 次，因此执行 6 次，额外增加 **1** 次。通过数差 10 = 原 5 个跳过条目被启用 + 新展开 1 次 + Profile 4 次。**不是 Java/Boot 版本本身少了五项测试。**

### 真实数据库和双节点证据

| 测试类 | 条件/真实场景 | 原完整运行证据 |
|---|---|---|
| `QuartzJdbcClusterReconciliationIT` | core Surefire 默认包含 *IT；需 mysql.url/user/password。MySQL 8.4 共享 JobStore：同版本双进程初建、缺失恢复、计划更新、暂停保持 4 项；双独立 Scheduler 对象的正常/30ms 忽略中断非并发 2 项 | 原 XML 6/6，0 skip；前四项确实 ProcessBuilder 启两个 JVM，后二项同 JVM 但移除注册表绑定并断言 Scheduler 对象不同，不是 singleton 假双节点 |
| `ContainerDatabaseLogIT` | integration-tests 加源码、Failsafe；Testcontainers 启 MySQL 8.4/PostgreSQL 16 各一次。V1 旧数据→V2→V3、async、审计空身份完成/幂等、查询/cleanup | 原 XML 2/2；本轮补时间/origin 冲突后再次 2/2，并再次 2/2 补验旧秒精度列；容器启动日志含数据库 JDBC 地址 |
| `RealDatabaseLogIT` | 同 Profile；两个方法分别需 mysql.url / postgresql.url 及凭据。外部真实实例、自动建表迁移、异步日志、审计、查询和清理 | 原 XML 2/2，0 skip；本轮未重复 |

历史证据：`/tmp/quartz-final-java21.log`、`/tmp/quartz-final-java17.log`、`/tmp/quartz-final-java17-counts.txt`。本轮真实 DB 日志 `/tmp/quartz-acceptance-idempotency-realdb-green.log`、`/tmp/quartz-acceptance-idempotency-legacy-realdb.log`，后一次还在真实两库验证秒精度旧列的幂等及冲突。本地仅因 Ryuk 辅助镜像不可达设置 `TESTCONTAINERS_RYUK_DISABLED=true`，数据库镜像实际运行、由测试关闭；没有以 H2 兼容模式代替真实库。没有重跑网络分区/远端 kill/failover。

CI 的普通四组合会跳过 core 集群 5 条目；独立 database-integration job 开启 MySQL 集群及容器测试，但未提供 PostgreSQL 外部 URL，故 `RealDatabaseLogIT` PostgreSQL 方法在该 CI job 仍会跳过。`--require-cluster` 只强制 core 六项，不等于全部数据库测试零跳过。本地原 270/0 还明确提供了 PostgreSQL URL，不能把该数字写成所有 CI job 的共同结果。

## 本轮实际定向验证

以下是按测试类/参数调用去重后的最终通过证据；跨命令重复执行不累加为新覆盖。没有机械重跑完整矩阵。

| 模块/测试类 | Java 21 / Boot 3.3.7 通过 | Java 17 / Boot 3.3.13 通过 |
|---|---:|---:|
| core / QuartzJobReconciliationTest | 19 | 19 |
| core / QuartzJobAnnotationProcessorTest | 15 | — |
| core / NativeAuditExecutionTest | 8 | 8 |
| core / EnhancedJobTest | 21 | — |
| core / CoQuartzMetricsTest | 3 | — |
| core / CoQuartzJobListenerTest | 6 | — |
| core / JobSpringLifecycleTest | 4 | — |
| core / QuartzConcurrencyTest | 5 | 5 |
| core / BuilderScheduleRegressionTest | 6 | 6 |
| core / QuartzTaskBuilderTest | 18 | — |
| core / QuartzJdbcRetryPersistenceTest | 3 | — |
| **core 小计** | **108** | **38** |
| jdbc / JdbcTaskLogRepositoryTest | 23 | 23 |
| jdbc / JdbcReliableAuditServiceTest | 3 | — |
| jdbc / JdbcAsyncTaskLogServiceTest | 11 | 11 |
| jdbc / DeclarativeRestartAcceptanceTest | 1 | 1 |
| jdbc / ContainerDatabaseLogIT（Profile） | 2 | — |
| **jdbc 小计** | **40** | **35** |
| **总计** | **148 通过 / 0 失败 / 0 错误 / 0 跳过** | **73 通过 / 0 失败 / 0 错误 / 0 跳过** |

另运行 Python 构建契约 6/6、`git diff --check` 通过。starter 本轮没有重跑，复用历史结果。最终 XML 分别保存于 `/tmp/quartz-acceptance-java21-reports`、`/tmp/quartz-acceptance-java17-reports`；摘要记录源 XML 的 SHA-256、Java 版本、逐类计数。

过程失败如实保留：六组最终 RED 回归分别为 2、1、4、3、5、1 次失败（共 16；指标用例在扩成四种组合前还先跑了两种、2 failures）；修复中的第一次控制标记 GREEN 为 31 通过/2 失败，原因是测试误将 Quartz unschedule 解释为删除，查实际 2.3.2 AbstractTrigger 后改为精确断言 COMPLETE，未降低断言；第一次真实 DB 验证单元 23 通过，集成 1 通过/1 error（MySQL 精度元数据），修正后单元 23 + 集成 2 全通过。故意故障注入产生的任务错误日志不计作测试失败。

复跑入口（常规 `-Dtest` 选择上表相关类即可；测试选择无匹配时仅对 reactor 上游允许空选择，不跳过被选测试）：

```bash
./mvnw -q -Dmaven.repo.local=/usr/local/repo -pl co-quartz-jdbc -am \
  -Dtest=QuartzJobReconciliationTest,NativeAuditExecutionTest,QuartzConcurrencyTest,JdbcTaskLogRepositoryTest,JdbcAsyncTaskLogServiceTest,DeclarativeRestartAcceptanceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
  ./mvnw -q -Dmaven.repo.local=/usr/local/repo -Dspring.boot.version=3.3.13 \
  -pl co-quartz-core -Dtest=BuilderScheduleRegressionTest test

TESTCONTAINERS_RYUK_DISABLED=true ./mvnw -q -Dmaven.repo.local=/usr/local/repo \
  -pl co-quartz-jdbc -am -Pintegration-tests -Dtest=JdbcTaskLogRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ContainerDatabaseLogIT verify
```

## 公共兼容性、发布条件与边界

相对基线的兼容影响：

- Builder `executeNow` 只创建一次性任务，已存在需 `Scheduler.triggerJob`；默认 Trigger group 改为 Job group，旧 DEFAULT 组记录须显式保留或停机迁移，不能无意增加计划。所有权/保留组/保留键冲突现在失败。
- Builder jobData 即使 RAM 也受可持久化内建类型、128 键/64KiB 等限制，任意 Serializable/运行期 Bean 不再接受。参数范围新增校验，useProperties=true/@Async 不支持；不把 `${...}` 解析当已实现功能。
- 超时不再使业务脱离 Quartz worker；永久忽略中断可阻塞线程和 shutdown(true)。原构造参数仍保留；不能再用旧 timeout pool 参数控制业务线程池。
- reliable audit 服务缺失启动失败；完成的身份/时间/origin 冲突失败，不再静默接受。审计失败尝试计 fail，开始拒绝业务时 duration=0，完成失败时 duration 仍为业务耗时；不会为已成功业务凭空生成 refire。
- `recoverInterruptedBefore` 保留签名但安全 no-op；改用带停机证据的 `recoverConfirmedStopped`。新增 DTO/事件关联、AsyncLogPipelineStatus 分量（保留五参数构造器）；JSON 输出、record equals/hashCode/toString 可变化，不能将新增字段误当旧版完全透明。
- `listJobs()` 最多 1000，Actuator 最多 100；running 是节点本地。分页固定时间窗口可改善遍历稳定性，不提供并发插入期间 offset 不移动的保证。
- V3 增加 nullable scheduler/application/origin 四列及 cleanup 索引；V1/V2/V3 脚本只执行尚未完成的版本，旧行归属保持 NULL。本轮验收没有再新增数据库迁移。手动脚本和自动 Java DDL 均需单执行者；应用可关闭自动 DDL、仅持 DML 权限。新审计幂等比较需要读取列精度元数据，不需 DDL 权限；运行期改变 schema 后应重建 repository/重启。

日志关闭边界：`shutdown()` 停止接收并有界等待；驱动忽略取消时后台 in-flight 可能继续，`unflushedCount` 是返回时的未确认快照，晚到写入不回改该值；当前 queued/inFlight/persisted 可继续变化。`flushLogsImmediately()` 是同步等待，不承诺同一关闭时限。accepted 不含拒收，dropped 同时含拒收和已接受但重排队失败，writeFailure 是尝试数，因此不能直接用这几个字段作严格守恒等式。生产自动配置告警异步；手工仅传同步 ApplicationEventPublisher 的旧构造路径可受监听器阻塞，不属于有界外部回调隔离保证。库的脱敏不承诺清除内存中业务 payload 或原生 Quartz 日志。

发布前必须落实：本轮六项修复须随发布交付；完成必要的 V3/旧 Trigger/旧 retry 元数据迁移；证明所有旧节点停机排空、单写者且不会被旧发布自动重建。无法保证这些部署条件时，**不得按“集群定义更新已支持”发布**。当前 POM 为 SNAPSHOT，真正发布需稳定版号与 tag 一致；本轮没有发布或推送。

可以接受的已知边界是上述受控停机部署、人工停机证据恢复、尽力日志/告警、本地观察视图、有限调度与审计一致性。网络分区、永久 JDBC 挂起的全面行为、大表压力、Windows wrapper 执行、任意第三方 JobFactory/代理组合、另外两组本机 Java/Boot 全矩阵均尚未验证。需要这些能力的部署不能据此验收报告推定已获支持。

## 历史矩阵逐测试类索引

下表原 Java 21 完整 XML 均为 0 失败/错误/跳过；Java 17 列是原日志/计数与相同发现规则的对账，不是保存有原 XML 的独立复验。S=Surefire，F=Failsafe；F 只在 integration-tests Profile，core 集群类还需 mysql.url。

| 模块/Runner | 测试类 | Java21 通过 | Java17 通过/跳过 |
|---|---|---:|---:|
| co-quartz-core/S | CoQuartzAutoConfigurationTest | 4 | 4 / 0 |
| co-quartz-core/S | CoQuartzPropertiesTest | 9 | 9 / 0 |
| co-quartz-core/S | BuilderScheduleRegressionTest | 5 | 5 / 0 |
| co-quartz-core/S | CoQuartzJobFactoryTest | 10 | 10 / 0 |
| co-quartz-core/S | CoQuartzTimeoutExecutorTest | 1 | 1 / 0 |
| co-quartz-core/S | CoQuartzUtilsTest | 8 | 8 / 0 |
| co-quartz-core/S | EnhancedJobTest | 21 | 21 / 0 |
| co-quartz-core/S | JobSpringLifecycleTest | 4 | 4 / 0 |
| co-quartz-core/S | NativeAuditExecutionTest | 3 | 3 / 0 |
| co-quartz-core/S | NonConcurrentJobWrapperTest | 2 | 2 / 0 |
| co-quartz-core/S | QuartzConcurrencyTest | 3 | 3 / 0 |
| co-quartz-core/S | QuartzJdbcClusterReconciliationIT | 6 | 0 / 5 |
| co-quartz-core/S | QuartzJdbcRetryPersistenceTest | 3 | 3 / 0 |
| co-quartz-core/S | QuartzJobAnnotationProcessorTest | 15 | 15 / 0 |
| co-quartz-core/S | QuartzJobReconciliationTest | 17 | 17 / 0 |
| co-quartz-core/S | QuartzNativeCapabilitiesTest | 2 | 2 / 0 |
| co-quartz-core/S | QuartzTaskBuilderTest | 18 | 18 / 0 |
| co-quartz-core/S | QuartzTriggerFactoryTest | 8 | 8 / 0 |
| co-quartz-core/S | RetryContextTest | 9 | 9 / 0 |
| co-quartz-core/S | TaskPolicyValidationTest | 1 | 1 / 0 |
| co-quartz-core/S | TaskExecutionLogTest | 4 | 4 / 0 |
| co-quartz-core/S | LogTaskExecStateEnumTest | 8 | 8 / 0 |
| co-quartz-core/S | MisfirePolicyTest | 2 | 2 / 0 |
| co-quartz-core/S | AlertEventPublisherAsyncTest | 4 | 4 / 0 |
| co-quartz-core/S | AlertEventPublisherTest | 8 | 8 / 0 |
| co-quartz-core/S | ExceptionHierarchyTest | 9 | 9 / 0 |
| co-quartz-core/S | CoQuartzJobListenerTest | 6 | 6 / 0 |
| co-quartz-core/S | CoQuartzMetricsTest | 3 | 3 / 0 |
| co-quartz-core/S | DefaultLogSanitizerTest | 10 | 10 / 0 |
| co-quartz-core/S | TaskAdminServiceTest | 3 | 3 / 0 |
| co-quartz-core/S | TaskQueryServiceTest | 2 | 2 / 0 |
| co-quartz-jdbc/F | ContainerDatabaseLogIT | 2 | 未发现（未启 Profile） |
| co-quartz-jdbc/F | RealDatabaseLogIT | 2 | 未发现（未启 Profile） |
| co-quartz-jdbc/S | NativeJdbcAuditFailureTest | 2 | 2 / 0 |
| co-quartz-jdbc/S | BootQuartzConfigurationMatrixTest | 3 | 3 / 0 |
| co-quartz-jdbc/S | CoQuartzJdbcAutoConfigurationTest | 3 | 3 / 0 |
| co-quartz-jdbc/S | LogCleanupRegistrarTest | 1 | 1 / 0 |
| co-quartz-jdbc/S | JdbcTaskLogRepositoryTest | 17 | 17 / 0 |
| co-quartz-jdbc/S | SchemaInitializerTest | 7 | 7 / 0 |
| co-quartz-jdbc/S | JdbcAsyncTaskLogServiceTest | 10 | 10 / 0 |
| co-quartz-jdbc/S | JdbcReliableAuditServiceTest | 3 | 3 / 0 |
| co-quartz-jdbc/S | JdbcSynchronousTaskLogWriterTest | 1 | 1 / 0 |
| co-quartz-starter/S | CoQuartzE2eTest | 11 | 11 / 0 |
