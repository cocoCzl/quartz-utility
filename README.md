## 信息
### 介绍
针对SpringBoot项目定时任务，封装了quartz。

记录定时任务执行日志，Trigger、Job统一构建入口。

### 自动注入各个依赖版本
JDK：17

quartz：2.3.2

springBoot：3.3.7

spring-boot-starter-data-jdbc：3.3.7

## 使用方式
### 依赖引入
```xml
<dependency>
  <groupId>com.coco</groupId>
  <artifactId>quartz-utility-starter</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

### 日志表
#### MYSQL：
```sql
DROP TABLE IF EXISTS quartz_task_log;
create table quartz_task_log (
  id int primary key auto_increment,
  job_key varchar(64) not null comment 'job标识',
  trigger_key varchar(64) not null comment 'trigger标识',
  exec_state tinyint not null comment '0 失败, 1 成功',
  error_message text comment '错误信息',
  execute_time datetime not null default current_timestamp on update current_timestamp comment '执行时间'
);
ALTER TABLE quartz_task_log
ADD UNIQUE INDEX unique_log_idx (job_key, trigger_key);
```

PG：

```sql
DROP TABLE IF EXISTS quartz_task_log;
create table quartz_task_log (
id SERIAL PRIMARY KEY,
job_key varchar(64) not null,
trigger_key varchar(64) not null,
exec_state SMALLINT NOT NULL,
error_message TEXT,
execute_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX unique_log_idx ON quartz_task_log (job_key, trigger_key);  
```

### 数据源配置
在自己的SpringBoot项目中按照平常配置quartz数据源的方式配置。

<font style="color:#DF2A3F;">quartz需要单独配置数据源，不能使用SpringBoot项目默认的数据源！</font>

```plain
spring:
  quartz:
    properties:
      org:
        quartz:
          jobStore:
            dataSource: quartzDataSource   # 👈 数据源名比如叫：quartzDataSource
          
          dataSource:
            quartzDataSource:              # 👈 必须在这里配置这个名字对应的数据源
              driver: ...
              URL: ...
              user: ...
              password: ...
              provider: hikaricp
```

### 属性
#### <font style="color:rgba(0, 0, 0, 0.85);">JobBuilder属性</font>
##### <font style="color:rgb(0, 0, 0);">1. 任务类设置</font>
+ `**newJob(Class<? extends Job> jobClass)**`：
    - **用途**：指定要执行的任务类，该类必须实现 `org.quartz.Job` 接口。Quartz 在调度任务时，会实例化这个类并调用其 `execute` 方法来执行具体的任务逻辑。

##### <font style="color:rgb(0, 0, 0);">2. 任务标识设置</font>
+ `**withIdentity(JobKey jobKey)**`：
    - **用途**：为任务设置唯一的标识，`JobKey` 由任务名称和所属组名组成。在 Quartz 中，每个任务都需要有一个唯一的 `JobKey`，这样可以方便地对任务进行管理和调度。

##### <font style="color:rgb(0, 0, 0);">3. 任务描述设置</font>
+ `**withDescription(String description)**`：
    - **用途**：为任务添加描述信息，该描述信息可以在管理界面或日志中查看，有助于理解任务的用途和功能。

##### <font style="color:rgb(0, 0, 0);">4. 任务恢复设置</font>
+ `**requestRecovery(boolean requestRecovery)**`：
    - **用途**：设置任务是否请求恢复。当 `Quartz` 节点在任务执行过程中发生故障并重启后，如果该任务设置了请求恢复（`requestRecovery` 为 `true`），且任务实现了 `StatefulJob` 接口或有相应的恢复逻辑，那么该任务会被重新执行。

##### <font style="color:rgb(0, 0, 0);">5. 任务持久化设置</font>
+ `**storeDurably()**`：
    - **用途**：设置任务是否持久化存储。若设置为持久化（调用此方法），即使没有 `Trigger` 关联该任务，任务也会保留在 `Quartz` 中，直到显式地删除它。这对于一些长期运行的任务或需要在特定条件下手动触发的任务很有用。

###### <font style="color:rgb(0, 0, 0);">6. 任务数据设置</font>
+ `**usingJobData(JobDataMap dataMap)**`：
    - **用途**：为任务添加参数数据，`JobDataMap` 是一个键值对的集合，可以存储任意类型的数据。在任务执行时，可以通过 `JobExecutionContext` 获取这些数据。
