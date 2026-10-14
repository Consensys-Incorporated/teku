# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Scheduled Gloas fork for the HOODI network at epoch 132352, which is 26 Oct 2026 17:42:48 UTC.

### Bug Fixes
 - The node's ENR no longer advertises a TCP port when the TCP transport is disabled (`--Xp2p-tcp-enabled=false`), so peers stop dialing a port nothing listens on. [#11454](https://github.com/Consensys-Incorporated/teku/issues/11454)
 - Block production now recovers within the slot when the `engine_forkchoiceUpdated` call it relies on fails, sending a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
 - Peer selection now gives each peer identity at most one connection slot per search, even when the candidates include more than one record for it. [#11424](https://github.com/Consensys-Incorporated/teku/issues/11424)
 - Gossip subscription announcements carrying up to 500 topics in one RPC are now accepted (previously 200). [#11482](https://github.com/Consensys-Incorporated/teku/issues/11482)
 - `ExecutionPayloadEnvelopesByRange` only serves envelopes of payloads that are part of the chain defined by the current head, as required by the spec. Envelopes of finalized blocks whose payload the chain did not build on are no longer retained, and such envelopes received while syncing are dropped instead of imported. [#11246](https://github.com/Consensys-Incorporated/teku/issues/11246)
 - Discovered peers whose node record advertises only a QUIC address are no longer ignored. Nodes with QUIC disabled skip them instead of dialing an address they do not have. [#11420](https://github.com/Consensys-Incorporated/teku/issues/11420)
 - Nodes with TCP disabled no longer dial, or fall back to, a discovered peer's TCP address; such candidates are skipped before peer selection and counted in the new `peer_candidate_count_total` metric. [#11421](https://github.com/Consensys-Incorporated/teku/issues/11421)
