# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Scheduled the Glamsterdam (Gloas) upgrade on Sepolia for epoch `353024` (October 6, 2026 13:53:36 UTC).
 - Updated aircompressor to 3.8 and made it the only Snappy implementation for gossip and RPC (removed snappy-xerial dependency)

### Bug Fixes
 - Fixed status, event, validator and database log messages being written twice when `--log-destination` is set to `CONSOLE` or `FILE`.
 - Fixed an out of memory error when a sync stopped while the chain head was still far behind. The node no longer reports itself as in sync in that case.
