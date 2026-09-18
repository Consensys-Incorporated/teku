# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

### Bug Fixes
 - Fixed status, event, validator and database log messages being written twice when `--log-destination` is set to `CONSOLE` or `FILE`.
 - Fixed an out of memory error when a sync stopped while the chain head was still far behind. The node no longer reports itself as in sync in that case.
 - Teku now shuts down on an `OutOfMemoryError` instead of continuing to run in a broken state or hanging during shutdown. This is done with the `-XX:+ExitOnOutOfMemoryError` JVM flag, so the process exits with code 3.
