# Changelog

All notable changes follow [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and semantic versioning.

## [Unreleased]

### Changed

- Execution logging is opt-in; scheduling, retries, timeouts and basic failure events work without JDBC.
- `co-quartz-starter` no longer brings JDBC logging transitively; add `co-quartz-jdbc` explicitly when needed.
- `co-quartz.async.enabled=false` selects synchronous execution-log writes.
- `co-quartz.monitoring.enabled=false` disables metrics, slow-task alerts and consecutive-failure alerts.

### Fixed

- Internal cleanup jobs no longer persist Spring beans in Quartz `JobDataMap`.
- Immediate dynamic jobs are registered atomically, including non-durable jobs.
- Existing dynamic jobs recreate missing triggers instead of silently doing nothing.

### Review fixes (R01–R20)

- Preserve validated pending retries and trigger payload/origin correlation across declarative JDBC restarts; enforce bounded persisted payloads and native unschedule precedence.
- Keep timed business execution on the Quartz worker until business exit, retaining JobStore nonconcurrency. Timeout requests interruption only; uncooperative work may delay shutdown indefinitely. The legacy timeout executor API remains but no longer runs business work.
- Move reliable native-job audit into the execution decorator. Start failure prevents business execution; completion failure fails the attempt without rerunning successful work. Require the CoQuartz JobFactory and an audit service.
- Atomically replace non-durable dynamic schedules; protect code/external ownership and trigger identities across management entry points. Default trigger groups follow job groups; executeNow creates a new task and rejects existing jobs.
- Preserve native persistence/interruption markers and isolated Spring job construction, data binding, initialization, proxies and destruction. Audit-only native jobs retain Quartz refire, execution correlation and final-invocation destruction; enhanced jobs retain bounded framework retry.
- Validate audit completion identity, affected rows and idempotent outcomes, including typed nullable parameters on PostgreSQL. Remove automatic age-only recovery; the deprecated recovery API is a safe no-op, with evidence-scoped recovery available instead.
- Deliver standalone V1/V2/V3 SQL for H2/MySQL/PostgreSQL. V3 adds nullable application/Scheduler ownership and origin fields. Production migrations require one migration writer; historical local timestamps require explicit UTC conversion.
- Bound asynchronous JDBC batches, shutdown waits, alerts and failure caches; expose in-flight/drop/coalescing state. Preserve STARTED records during bounded UTC cleanup and reconcile cleanup configuration changes.
- Add explicit definition retirement previews/tombstones and method binding fingerprints. Definition changes require all cluster nodes stopped/standby and a single writer; mixed-version live reconciliation is unsupported.
- Distinguish primary/retry/manual/recovery triggers and local running scope; validate bounded, stably ordered log pagination. Reject invalid task policy values, @Async methods and Quartz useProperties=true.
- Extend best-effort credential redaction in library-controlled records/events/logs, cache database metric samples off the scrape path, bound tag admission and correlate asynchronous alerts.
- Pin Maven 3.9.9 and test plugins 3.5.4, provide subclass Mockito configuration, and explicitly enable real MySQL cluster tests in CI alongside container database tests.
