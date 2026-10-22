# Changelog

All notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic versioning.
Current source version: `2.1.0-SNAPSHOT`. Entries below are unreleased; migration actions are documented in the [upgrade guide](docs/upgrade.md).

## [Unreleased]

### Dependency and configuration changes

- Execution logging is opt-in. `co-quartz-starter` only includes core; add `co-quartz-jdbc`, a driver, a DataSource and schema migrations before enabling logs. Scheduling, retries, timeouts and basic failure events work without JDBC.
- `co-quartz.async.enabled=false` selects synchronous best-effort execution-log writes; disabling logs only requires `co-quartz.log.enabled=false`.
- `co-quartz.monitoring.enabled=false` disables metrics, slow-task alerts and consecutive-failure alerts; basic failure and timeout events remain available.
- Task policy values are bounded consistently across annotations, Builder and RetryContext. Dynamic jobData uses bounded portable types in both RAM and JDBC stores. Method tasks reject method/class-level `@Async`; Quartz `useProperties=true` is unsupported.

### Scheduling and execution

- Preserve pending retries that pass validation and their payload/origin correlation across declarative JDBC restarts; native unschedule flags take precedence over framework retries.
- Keep timed business execution on the Quartz worker until business exit, retaining JobStore nonconcurrency. Timeout only requests interruption; uncooperative work may delay shutdown indefinitely. The legacy timeout executor API remains but no longer runs business work.
- Register immediate dynamic jobs atomically, including non-durable jobs. Replace non-durable dynamic schedules atomically and recreate missing triggers. Protect code/external ownership and trigger identities across management entry points.
- Default Builder trigger groups follow job groups. Legacy DEFAULT-group triggers require an explicit group or a drained migration. `executeNow` only creates a new task and rejects existing jobs; trigger existing definitions through the management API or native Scheduler.
- Preserve native persistence/interruption markers and isolated Spring Job construction, data binding, initialization, proxies and destruction. Audit-only native jobs retain Quartz refire, execution correlation and final-invocation destruction; enhanced jobs use bounded framework retry.
- Add explicit definition retirement previews/tombstones and method binding fingerprints. Definition changes require all cluster nodes drained and stopped/standby with a single writer. Mixed-version rolling updates are unsupported; old nodes can overwrite new definitions if restarted.

### Execution logs and audit

- Move reliable native-job audit into the execution decorator. Start-write failure prevents business execution; completion-write failure fails the attempt without rerunning successful business work. A CoQuartz JobFactory and audit service are required.
- Validate audit completion identity, affected rows and idempotent outcomes, including typed nullable PostgreSQL parameters and database timestamp precision. Replace automatic age-only recovery with evidence-scoped recovery. The deprecated recovery API retains its signature but no longer updates records.
- Provide standalone V1/V2/V3 SQL for H2/MySQL/PostgreSQL. V3 adds nullable application/Scheduler ownership and origin fields. Production migrations require one migration writer; historical local timestamps need explicit UTC conversion.
- Bound asynchronous JDBC batches, failure retries and shutdown waits; expose accepted/persisted/in-flight/drop/unflushed state. Best-effort logging can lose records. Drivers that ignore cancellation may continue writing after the shutdown deadline.
- Internal cleanup jobs no longer persist Spring beans in JobDataMap. Preserve STARTED records during bounded UTC cleanup, reconcile cleanup configuration changes and remove the internal cleanup job when logging is disabled.
- Extend best-effort credential redaction in library-controlled records/events/diagnostics. Business logs, native Quartz logs and third-party listeners remain outside that redaction boundary.

### Queries, alerts and metrics

- Distinguish primary/retry/manual/recovery/external triggers and expose local running scope. Bound task lists and validate stably ordered log pagination; concurrent inserts can still move offset pages.
- Bound asynchronous alert delivery and consecutive-failure caches. Expose dropped/coalesced counts and execution correlation. Consecutive failures reflect a local completion-order window, not a cluster aggregate.
- Cache database metric samples off the scrape path and expose sample age/refresh failures. Bound job-tag admission and count executions per attempt, including retries.
- Provide an optional read-only Actuator summary; endpoint exposure and application security remain application responsibilities.

### Build and testing

- Pin Maven Wrapper 3.9.9 and Surefire/Failsafe 3.5.4, and use subclass Mockito configuration.
- Cover JDK 17/21 with Spring Boot 3.3.7/3.3.13 in the CI matrix. Enable real MySQL cluster tests explicitly in database CI and run MySQL 8.4/PostgreSQL 16 container log tests through the integration-tests profile.
