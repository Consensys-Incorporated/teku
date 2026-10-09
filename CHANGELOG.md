# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Scheduled Gloas fork for the HOODI network at epoch 132352, which is 26 Oct 2026 17:42:48 UTC.

### Bug Fixes
 - Discovered peers whose node record advertises only a QUIC address are no longer ignored. Nodes with QUIC disabled skip them instead of dialing an address they do not have. [#11420](https://github.com/Consensys-Incorporated/teku/issues/11420)
 - Nodes with TCP disabled no longer dial, or fall back to, a discovered peer's TCP address; such candidates are skipped before peer selection and counted in the new `peer_candidate_count_total` metric. [#11421](https://github.com/Consensys-Incorporated/teku/issues/11421)
