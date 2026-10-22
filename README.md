# Co-Quartz

Co-Quartz 是简化 Spring Boot 中 Quartz 使用的封装：给 Spring Bean 方法加上 `@QuartzTask`，即可注册并运行定时任务。

## 核心能力

- **方法任务**：用 `@QuartzTask` 定义 Cron 或固定间隔任务，默认自动扫描注册。
- **多种接入方式**：支持传统 Quartz `Job`、动态 Builder，以及任务查询、暂停、恢复和手动触发。
- **执行策略**：可配置非并发执行、失败重试和协作式超时取消，无需 JDBC。
- **执行日志与审计（可选）**：需添加 `co-quartz-jdbc`、配置数据源、迁移表并开启日志；可进一步启用同步生命周期审计。
- **告警与观测**：内置 Spring 告警事件；指标需 Micrometer 和 `MeterRegistry`，只读运维端点需 Actuator 并显式暴露。
- **调度存储**：默认内存模式；共享调度需配置 Quartz JDBC JobStore，集群还需统一配置和受控部署。

## 环境要求

- Java **17** 为编译目标（`--release 17`）；使用 JDK 17 或 21 构建、运行。
- 默认依赖组合为 **Spring Boot 3.3.7 / Quartz 2.3.2**。
- 仓库 CI 测试矩阵为 **JDK 17、21 × Spring Boot 3.3.7、3.3.13**，Quartz 固定为 2.3.2；其他组合不在当前测试覆盖范围。
- 源码构建使用 Maven Wrapper **3.9.9**；独立示例需本机 Maven，或已有 Spring Boot 项目的 Wrapper。

## 快速开始：运行第一个任务

此示例使用内存调度，不需要数据库，也不需要 `application.yml`。

### 1. 获取当前开发版并添加依赖

当前源码版本为 **`2.1.0-SNAPSHOT`**。这里不假定该版本已发布到 Maven Central；请先在源码根目录安装到本地 Maven 仓库：

```bash
git clone https://github.com/cococzl/quartz-utility.git
cd quartz-utility
./mvnw -DskipTests install
```

已克隆项目时直接执行最后一条命令即可。此命令不发布到远程仓库。已发布版本的版本号和获取地址需另行核实，不能直接把这里的 SNAPSHOT 当成 Central 可下载版本。

已有 Spring Boot 应用只需添加下面 POM 中的 `co-quartz-starter` 依赖。
从零体验时，在源码目录之外新建 `co-quartz-demo` 目录，保存以下 `pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.7</version>
    <relativePath/>
  </parent>
  <groupId>com.example</groupId>
  <artifactId>co-quartz-demo</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <properties>
    <java.version>17</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>io.github.cococzl</groupId>
      <artifactId>co-quartz-starter</artifactId>
      <version>2.1.0-SNAPSHOT</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### 2. 定义每 10 秒执行一次的方法

创建 `src/main/java/com/example/quartzdemo/DemoApplication.java`：

```java
package com.example.quartzdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

在同一包下创建 `HelloTasks.java`：

```java
package com.example.quartzdemo;

import io.github.cococzl.coquartz.annotation.QuartzTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HelloTasks {
    private static final Logger log = LoggerFactory.getLogger(HelloTasks.class);

    @QuartzTask(name = "hello", intervalSeconds = 10)
    public void hello() {
        log.info("[hello] Quartz task executed");
    }
}
```

任务类必须处于 Spring Boot 的组件扫描范围内：放在启动类同包或子包中，或显式配置扫描。
任务方法必须是 `public void`、无参、非静态方法，`name` 必须显式指定且在组内唯一。

Starter 自动引入 Quartz 并启用注解扫描，无需 `@EnableScheduling` 或额外启用注解。
默认使用内存 JobStore，重启后由代码重新注册任务；本例不引入 JDBC 模块。

### 3. 启动并观察输出

在示例目录运行（已有 Wrapper 的应用可使用 `./mvnw`）：

```bash
mvn spring-boot:run
```

启动后即可看到首次执行，随后约每 10 秒输出一行：

```text
... HelloTasks : [hello] Quartz task executed
... HelloTasks : [hello] Quartz task executed
```

这里的输出是业务控制台日志；数据库执行记录默认关闭。
保持进程运行即可观察后续执行，按 `Ctrl+C` 停止。
如没有输出，先检查任务是否被扫描、是否关闭了 `co-quartz.annotation.enabled` 或 `spring.quartz.auto-startup`，以及控制台中的启动异常。

## 可选：记录执行日志

需要查询执行历史时，再完成以下步骤：

1. 添加同版本的 `io.github.cococzl:co-quartz-jdbc` 和所用数据库的 JDBC 驱动。
2. 配置应用数据源或专用日志数据源，并按数据库类型完成 [日志表迁移](docs/upgrade.md#日志表迁移)。
3. 设置 `co-quartz.log.enabled: true`；默认使用异步尽力写入，队列溢出、进程退出或数据库故障时可能丢失记录。

日志库与 Quartz JDBC JobStore 是两种独立用途：开启执行日志不要求把调度器改成 JDBC 模式。
可靠审计需另外开启；它同步记录开始和结束，并对写入失败作失败处理，但不保证业务与日志原子提交或绝对不丢失。
数据源选择、同步日志和可靠审计见 [执行日志指南](docs/usage.md#执行日志) 与 [可靠审计](docs/usage.md#可靠审计)。

## 使用前需要了解

- **幂等**：重试、故障恢复等场景可能重复执行，业务需自行保证幂等。见 [执行语义](docs/usage.md#执行语义)。
- **时区**：默认 `UTC`；Cron 可显式设置 `timeZone = "Asia/Shanghai"`，或配置 `co-quartz.scheduling.default-time-zone`。见 [调度参数](docs/usage.md#调度参数)。
- **超时**：仅请求协作式取消，不保证强制停止或释放资源；忽略中断的任务仍可能占用线程。见 [超时与取消](docs/usage.md#超时与取消)。
- **集群更新**：定义更新需全节点排空、阻止并发启动并保持单写者；不支持混合版本滚动更新。见 [集群部署与定义更新](docs/upgrade.md#集群部署与定义更新)。

更多内容：

- [使用指南](docs/usage.md)：传统 Job、动态任务、管理 API、参数与配置、日志、告警、指标及 Actuator。
- [升级指南](docs/upgrade.md)：数据库迁移、默认行为与接口兼容变化、旧 Trigger group 和集群部署。
- [贡献指南](CONTRIBUTING.md)：源码构建、普通测试、真实数据库及集群测试。
- [变更记录](CHANGELOG.md)。

## License

[Apache License 2.0](LICENSE)
