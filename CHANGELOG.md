# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
### Changed
### Deprecated
### Removed
### Fixed
### Security

## [1.0.2] - 2026-03-07
### Added

- Added `activeSessions` support in `HookEmitterPlugin` class to send `session_id` on join and quit events.

## [1.0.1] - 2026-02-01

### Fixed

- Fixed permissions for `join_msg.others`

## [1.0.0] - 2026-01-29

### Added

- Initial code for the HookEmitter plugin, supporting the "PlayerJoinEvent" and "PlayerQuitEvent" events.
This is a rough, early version and requires refactoring, including moving parts of the code that generate text and
handle events into separate classes.


[unreleased]: https://github.com/stratila/HookEmitter/compare/v1.0.1...HEAD
[1.0.2]: https://github.com/stratila/HookEmitter/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/stratila/HookEmitter/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/stratila/HookEmitter/compare/a7892bb...v1.0.0

