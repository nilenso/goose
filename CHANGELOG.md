# Change Log

All notable changes to Goose will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/).

## [Unreleased]

### Added
### Changed
### Fixed

## [0.4.0] - 20-Oct-2023

### Added
- Batch processing of Jobs
### Changed
- Gauge metrics suffixes from `size` to `count` as the latter was more accurate
- Gauge metrics prefixes from `_queue` to `_jobs`
### Fixed
- Infinite loop in find Jobs API
- Validation of args serializability

## [0.3.2] - 07-Mar-2023

### Added
### Changed
### Fixed
- Fix JDK19 interop with `Thread/sleep`

## [0.3.1] - 16-Dec-2022

### Added
### Changed
- Disable statsd metrics initialization by default
### Fixed
- Fix compilation error caused by `clj-statsd` initialization
 
## [0.3.0] - 31-Oct-2022

### [Goose 0.3.0 Project Board](https://github.com/orgs/nilenso/projects/6)

### Added
- Periodic jobs for Redis
- Pluggable Message Brokers
- Native support for RabbitMQ
    - Replication, Clustering & Quorum queues
    - Publisher confirms
    - Return, Shutdown & Recovery handlers
- Pluggable Custom Metrics Backend
- Contribution Guide
- Docstrings
- Logo of Goose

### Changed
- Inject broker instead of broker-opts

### Fixed
- `No implementation of protocol method` bug

## [0.2] - 29-Jul-2022

### [Goose 0.2 Project Board](https://github.com/orgs/nilenso/projects/1)

### Added
- API to manage jobs
- Middleware support
- StatsD Metrics
- Specs for validation
- Performance Tests & Benchmarks
- Architecture Decisions

### Changed
### Fixed

## [0.1] - 09-Jun-2022

### Added
- Background processing of jobs
- Scheduling
- Error-Handling & Retrying
- Graceful Shutdown

### Changed
### Fixed
