# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Scheduled Gloas fork for the HOODI network at epoch 132352, which is 26 Oct 2026 17:42:48 UTC.

### Bug Fixes
 - Discovered peers whose node record advertises only a QUIC address are no longer ignored. Nodes with QUIC disabled skip them instead of dialing an address they do not have. [#11420](https://github.com/Consensys-Incorporated/teku/issues/11420)
