# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Gossip signature verification now gathers larger batches and verifies signatures over the same message together, lowering CPU usage at the cost of up to ~10 ms added gossip validation latency. Use `--Xp2p-batch-verify-signatures-mode=LEGACY` to restore the previous behaviour.

### Bug Fixes
 - Reduced the memory held by gossip blocks waiting for validation, limited their total size, and blocks too far in the future are now ignored before they are decoded. [#11503](https://github.com/Consensys-Incorporated/teku/pull/11503)
 - The node's ENR no longer advertises a TCP port when the TCP transport is disabled (`--Xp2p-tcp-enabled=false`), so peers stop dialing a port nothing listens on. [#11454](https://github.com/Consensys-Incorporated/teku/issues/11454)
 - Block production now recovers within the slot when the `engine_forkchoiceUpdated` call it relies on fails, sending a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
