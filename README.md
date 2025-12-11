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
  stack_trace text comment '错误堆栈信息',
  execution_time_ms bigint comment '任务执行时间(毫秒)',
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
stack_trace TEXT,
execution_time_ms BIGINT,
execute_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX unique_log_idx ON quartz_task_log (job_key, trigger_key);  
```

### 数据源配置
写日志操作的jdbcTemplate使用的数据源

<font style="color:#DF2A3F;">quartz可以单独配置数据源，也可以使用SpringBoot项目默认的数据源！</font>

## 新增功能特性

### 1. Cron 表达式支持
现在支持使用 Cron 表达式来定义复杂的调度规则。

### 2. 更多配置选项
- Misfire 策略配置
- 更详细的任务属性配置

### 3. 任务监控服务
提供 `TaskMonitoringService` 来监控任务执行情况。

## 使用示例

### 1. 使用高级任务构建器 (推荐)

```java
@Component
public class TaskSchedulerExample {
    
    @Autowired
    private CoQuartzScheduler scheduler;
    
    public void scheduleTasks() {
        try {
            // 简单间隔任务
            QuartzTaskBuilder.newBuilder()
                .jobClass(SampleJob.class)
                .jobName("sampleJob")
                .jobGroup("myGroup")
                .description("这是一个示例任务")
                .intervalInSeconds(30) // 每30秒执行一次
                .durable(true)
                .recoverable(false)
                .schedule(scheduler);
                
            // Cron 表达式任务
            QuartzTaskBuilder.newBuilder()
                .jobClass(ScheduledJob.class)
                .jobName("scheduledJob")
                .description("Cron任务示例")
                .cron("0 0 12 * * ?") // 每天中午12点执行
                .schedule(scheduler);
                
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }
}
```

### 2. 使用 QuartzComponent 配置 (传统方式)

#### QuartzComponent类 (新增属性)
```java
// 新增的属性
private final String cronExpression;        // Cron 表达式
private final boolean useCronTrigger;       // 是否使用 Cron 触发器
private final int misfireInstruction;       // Misfire 策略
private final int priority;                 // 任务优先级

// 使用示例：
QuartzComponent quartzComponent = new QuartzComponent.Builder()
        .setTimeInterval(1)
        .setTimeEnum(TimeEnum.MINUTES)
        .setDescription("test")
        .setDurability(true)
        .setShouldRecover(true)
        .setPriority(7)  // 设置优先级
        .setMisfireInstruction(1)  // 设置 misfire 策略
        .build();
        
// 使用 Cron 表达式
QuartzComponent cronComponent = new QuartzComponent.Builder()
        .setCronExpression("0 0/5 * * * ?")  // 每5分钟执行一次
        .setDescription("Cron trigger example")
        .setDurability(true)
        .build();
```

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

##### <font style="color:rgb(0, 0, 0);">6. 任务数据设置</font>
+ `**usingJobData(JobDataMap dataMap)**`：
  - **用途**：为任务添加参数数据，`JobDataMap` 是一个键值对的集合，可以存储任意类型的数据。在任务执行时，可以通过 `JobExecutionContext` 获取这些数据。

### 3. 任务监控和管理

```java
@Service
public class TaskMonitoringExample {
    
    @Autowired
    private TaskMonitoringService monitoringService;
    
    @Autowired
    private CoQuartzScheduler scheduler;
    
    public void monitorTasks() {
        // 获取任务统计信息
        TaskMonitoringService.TaskStatistics stats = monitoringService.getTaskStatistics();
        System.out.println("总执行次数: " + stats.getTotalExecutions());
        System.out.println("成功执行次数: " + stats.getSuccessfulExecutions());
        System.out.println("失败执行次数: " + stats.getFailedExecutions());
        System.out.println("成功率: " + stats.getSuccessRate() + "%");
        
        // 获取指定任务的执行历史
        List<TaskMonitoringService.TaskExecutionLog> logs = 
            monitoringService.getTaskExecutionHistory("DEFAULT.sampleJob", 10);
            
        // 获取最近的失败任务
        List<TaskMonitoringService.TaskExecutionLog> failedLogs = 
            monitoringService.getRecentFailedTasks(5);
            
        // 清理30天前的日志
        int deletedCount = monitoringService.cleanupLogs(30);
        System.out.println("清理了 " + deletedCount + " 条历史日志");
        
        // 暂停/恢复/删除任务
        JobKey jobKey = scheduler.getJobKey("sampleJob", "myGroup");
        try {
            scheduler.pauseJob(jobKey);   // 暂停任务
            scheduler.resumeJob(jobKey);  // 恢复任务
            scheduler.deleteJob(jobKey);  // 删除任务
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }
}
```

### 使用
原本Quartz业务代码的Job类需要实现org.quartz.Job接口，现在改为继承com.coco.core.BaseAbstractQuartzJob类。重写executeQuartz方法，实际的定时任务实现代码就写在executeQuartz方法中。

#### BaseAbstractQuartzJob
会执行详细日志落库代码（包括执行时间、错误堆栈等），实际定时任务执行代码在子类的executeQuartz实现中。

#### CoQuartzScheduler
代替原始的Scheduler，有多种重载方法，按需使用。新增了 Cron 任务调度、任务管理等功能。

#### JobKey、TriggerKey获取
实际获取在SchedulerCore中，可以自定义name和group，如果不传将使用默认值。

```java
@Slf4j
@Component
public class DollarPenguinStarter {

    @Autowired
    private CoQuartzScheduler coQuartzScheduler;

    public void start() {
        try {
            initAndStart();
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            System.exit(-1);
        }
    }

    private void initAndStart() {
        // 连接信息定时采集任务
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("testJobDataMap", "testJobDataMap");
        // 获取job key
        JobKey jobKey = coQuartzScheduler.getJobKey("test", "test_group");
        // 获取trigger key
        TriggerKey triggerKey = coQuartzScheduler.getTriggerKey("test", "test_group");
        TestJob testJob = new TestJob();
        try {
            // 使用 Cron 表达式调度任务
            QuartzComponent cronComponent = new QuartzComponent.Builder()
                    .setCronExpression("0 0/1 * * * ?") // 每分钟执行一次
                    .setDescription("test cron job")
                    .setDurability(true)
                    .setShouldRecover(true)
                    .build();
            coQuartzScheduler.scheduleCronJob(TestJob.class, jobKey, triggerKey,
                    jobDataMap, null, cronComponent);
                    
            // 或使用简单间隔调度任务
            QuartzComponent quartzComponent = new QuartzComponent.Builder()
                    .setTimeInterval(1)
                    .setTimeEnum(TimeEnum.MINUTES)
                    .setDescription("test")
                    .setDurability(true)
                    .setShouldRecover(true)
                    .build();
            coQuartzScheduler.scheduleSimpleIntervalJob(TestJob.class, jobKey, triggerKey,
                    jobDataMap, null, quartzComponent);
        } catch (SchedulerException e) {
            log.error("initAndStart error:{}", e.getMessage(), e);
        }
    }
}
```