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
