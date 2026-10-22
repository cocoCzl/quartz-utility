# 贡献指南

本文介绍如何构建源码、运行普通测试，以及按需运行真实数据库和集群测试。
使用项目请先看 [README](README.md) 与 [使用指南](docs/usage.md)，数据表和部署变更见 [升级指南](docs/upgrade.md)。

## 环境

- JDK 17 或 21，编译使用 `--release 17`。
- Maven Wrapper 固定为 3.9.9，首次使用需下载 Maven 和依赖；Windows 使用 `mvnw.cmd`。
- Python 3 用于构建约定和测试报告检查脚本。
- 默认依赖是 Spring Boot 3.3.7 / Quartz 2.3.2；CI 普通测试矩阵为 JDK 17、21 × Boot 3.3.7、3.3.13。
- 普通测试无需 Docker；容器日志测试需可用的 Docker daemon，真实 MySQL 集群测试需单独提供可丢弃的 MySQL 实例。

Surefire/Failsafe 固定为 3.5.4；Mockito 使用 subclass MockMaker，不要求 JVM 动态自附加。
数据库集成 CI 使用 JDK 17、MySQL 8.4 服务，并通过 Testcontainers 启动 MySQL 8.4 和 PostgreSQL 16；不代表其他数据库版本组合也在当前测试覆盖内。

## 源码构建

在仓库根目录执行：

```bash
./mvnw -DskipTests install
```

这会构建父 POM、core、jdbc、starter，并把 `2.1.0-SNAPSHOT` 安装到本地 Maven 仓库，供独立应用引用；不会发布远程制品。
已有 Spring Boot 应用按需添加 Starter，日志功能另加同版本 JDBC 模块。
无需修改个人 Maven settings；若环境使用企业镜像或代理，确保其能解析所需公开依赖。

## 普通测试

```bash
python3 scripts/check_build_contract.py
./mvnw clean verify
python3 scripts/check_test_reports.py
```

普通测试覆盖 RAM 调度、H2 JDBC、自动配置、重试、日志和审计等路径。
真实 MySQL 集群用例由系统属性控制，未提供地址时跳过；JDBC 的外部数据库与容器测试源码只在 `integration-tests` profile 下加入。
core 已将 `*IT` 纳入 Surefire，因此启用集群并不依赖该 profile；profile 的 Failsafe 用于 JDBC 集成测试。

脚本会分模块打印 executed、passed、failures、errors、skipped；统计失败、错误或缺失模块报告时返回非零。
建议 clean 后运行，避免旧 Surefire/Failsafe XML 混入统计。普通测试通过不表示外部数据库或集群用例已执行。

需要在另一个 Boot 版本运行同样的测试时，可显式覆盖属性；JDK 通过本机工具链选择：

```bash
./mvnw clean verify -Dspring.boot.version=3.3.13
```

## 真实数据库与集群测试

只连接可丢弃的测试环境。以下用例会创建专用库/schema 并修改其中的表，集群用例每次会重置 Quartz 表；不要指向业务数据库。
MySQL 集群测试会启动独立 JVM 进程，环境需允许子进程与数据库网络连接。
数据库端需要创建库/schema 和 DDL/DML 权限。

### 容器日志测试

先启动 Docker，确认能够拉取 `mysql:8.4` 和 `postgres:16`，再执行：

```bash
./mvnw clean verify -Pintegration-tests
python3 scripts/check_test_reports.py
```

`ContainerDatabaseLogIT` 用 Testcontainers 运行两种数据库的日志迁移、异步写入、审计、查询和清理路径。
这条命令**不会自动启用 core 的真实 MySQL 集群用例**：未传地址时它仍跳过。
Profile 增加的 MySQL/PostgreSQL 驱动和 Testcontainers 为测试依赖，不改变公共 jar 的运行时依赖。

### MySQL 集群与完整集成测试

在可丢弃的 MySQL 8.4 实例上准备账户，提供**服务器根 URL**，不带库名和查询参数：

```bash
./mvnw clean verify -Pintegration-tests \
  -Dco.quartz.test.mysql.url=jdbc:mysql://127.0.0.1:3306/ \
  -Dco.quartz.test.mysql.user=root \
  -Dco.quartz.test.mysql.password=quartz-test
python3 scripts/check_test_reports.py --require-cluster
```

示例口令仅用于测试实例。此组合既要求外部 MySQL，也要求 Docker 来运行 profile 中的容器用例。
core 的 `QuartzJdbcClusterReconciliationIT` 使用 `co_quartz_task4_it` 库，JDBC 的 `RealDatabaseLogIT` 使用 `co_quartz_task15_it` 库。
`--require-cluster` 要求六项 MySQL 集群用例均执行且无跳过、失败或错误；CI 用这一方式避免集群测试静默跳过。

只运行集群相关用例、不启动 JDBC 容器测试，可用：

```bash
./mvnw -pl co-quartz-core -am clean test \
  -Dtest=QuartzJdbcClusterReconciliationIT -Dsurefire.failIfNoSpecifiedTests=false \
  -Dco.quartz.test.mysql.url=jdbc:mysql://127.0.0.1:3306/ \
  -Dco.quartz.test.mysql.user=root \
  -Dco.quartz.test.mysql.password=quartz-test
```

此时只生成所选模块报告，应直接检查 core 的 Surefire 报告，不使用要求全模块报告的汇总脚本。

### 指定外部 PostgreSQL

`RealDatabaseLogIT` 还接受以下参数（`CO_QUARTZ_TEST_PG_URL` 等由本机环境提供）：

```bash
./mvnw clean verify -Pintegration-tests \
  "-Dco.quartz.test.postgresql.url=${CO_QUARTZ_TEST_PG_URL}" \
  "-Dco.quartz.test.postgresql.user=${CO_QUARTZ_TEST_PG_USER}" \
  "-Dco.quartz.test.postgresql.password=${CO_QUARTZ_TEST_PG_PASSWORD}"
```

URL 指向可丢弃的 PostgreSQL 数据库，如 `jdbc:postgresql://127.0.0.1:5432/quartz_test`。
用例在库内创建并使用 `co_quartz_task15_it` schema；这不是 PostgreSQL 集群调度测试。
上述命令仍会运行容器测试；MySQL 集群与外部 MySQL 日志测试未提供 URL 时跳过。

## 提交变更

- 行为变化同步更新 [使用指南](docs/usage.md)、[升级指南](docs/upgrade.md) 和 [CHANGELOG](CHANGELOG.md)，说明使用者需要采取的动作。
- 涉及数据表时提供版本迁移路径，避免要求生产应用自动执行 DDL。
- 选择与改动相关的测试；仅修改入门文档时，编译并运行相应最小示例、检查 API 与链接即可，无需重跑完整数据库和集群测试。
- 不提交个人配置、密钥、审查报告、验收记录或临时过程文件。
