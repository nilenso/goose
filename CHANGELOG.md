# Change Log

All notable changes to Goose will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/).

## [Unreleased] - 31-Jan-2023

### Added
### Changed
- Redis client library from `ptaoussanis/carmine` to `nilenso/crimson`

### Fixed

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
