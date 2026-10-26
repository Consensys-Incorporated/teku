# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Removed the Gloas fork schedule for the HOODI network (previously epoch 132352, 26 Oct 2026); Gloas is unscheduled on HOODI again.
 - Lowered the CPU used by the gossip event thread to recognise duplicate gossip messages, which grows with the number of connected peers. [#11549](https://github.com/Consensys-Incorporated/teku/pull/11549)
 - Updated discovery library to 26.10.1, which speeds up secp256k1 operations used by node discovery. [#11541](https://github.com/Consensys-Incorporated/teku/pull/11541)
 - Gossip signature verification now gathers larger batches and verifies signatures over the same message together, lowering CPU usage at the cost of up to ~10 ms added gossip validation latency. Use `--Xp2p-batch-verify-signatures-mode=LEGACY` to restore the previous batching behaviour. In both modes, signatures re-verified after a failed batch now honour `--Xp2p-batch-verify-signatures-strict-thread-limit-enabled` and a task that throws no longer fails the rest of its batch.
 - Payload attestation message signatures are now verified in batches by the gossip signature verification service, off the gossip thread, instead of one at a time.
 - Teku-generated Gloas genesis states now follow the consensus-specs and eth-beacon-genesis layout: the genesis execution payload bid's block hash is zero, so the genesis block is EMPTY and interop genesis states are interchangeable with those produced for Kurtosis. The synthetic genesis execution payload Teku seeded at startup is no longer needed and has been removed. Gloas genesis states and beacon node databases created by earlier Teku versions (interop devnets) use the previous layout and must be regenerated; a node started on one now stops at startup with a message saying so. [#11545](https://github.com/Consensys-Incorporated/teku/issues/11545)
 - Added the Gloas beacon API endpoints `GET /eth/v1/beacon/states/{state_id}/builder_pending_withdrawals` and `GET /eth/v1/beacon/states/{state_id}/builder_pending_payments`. [#11544](https://github.com/Consensys-Incorporated/teku/pull/11544)
 - Added the `GET /eth/v1/beacon/proposer_preferences` Gloas Beacon API endpoint, which returns the signed proposer preferences known by the node, optionally filtered by `slot` and `dependent_root`. [#11548](https://github.com/Consensys-Incorporated/teku/pull/11548)

### Bug Fixes
 - Close an inbound RPC stream when its response cannot be written, instead of half-closing it. Over QUIC a peer that cancelled its request (STOP_SENDING) refuses our FIN, so such streams stayed open until the peer disconnected and could exhaust the node-wide limit of 128 inbound RPC streams, after which every other peer's requests were rejected.
 - The fee recipient of a Gloas block now comes from signed proposer preferences, so preferences that fail validation no longer change where the block pays, and `prepare_beacon_proposer` is ignored once Gloas is active. [#11518](https://github.com/Consensys-Incorporated/teku/issues/11518)
 - Reduced the memory held by gossip blocks waiting for validation, limited their total size, and blocks too far in the future are now ignored before they are decoded. [#11503](https://github.com/Consensys-Incorporated/teku/pull/11503)
 - The node's ENR no longer advertises a TCP port when the TCP transport is disabled (`--Xp2p-tcp-enabled=false`), so peers stop dialing a port nothing listens on. [#11454](https://github.com/Consensys-Incorporated/teku/issues/11454)
 - Block production now recovers within the slot when the `engine_forkchoiceUpdated` call it relies on fails, sending a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
 - Slashing protection imported through the keymanager API for a key that was deleted earlier in the same run is now used when the key is re-imported, instead of being overwritten by the record cached before the delete. Slashing protection sent with a keystore that is already loaded is no longer applied, matching the `duplicate` response. [#11388](https://github.com/Consensys-Incorporated/teku/issues/11388)
 - `ExecutionPayloadEnvelopesByRange` only serves envelopes of payloads that are part of the chain defined by the current head, as required by the spec. Envelopes of finalized blocks whose payload the chain did not build on are no longer retained, and such envelopes received while syncing are dropped instead of imported. Such envelopes already persisted by earlier versions are not removed, which only affects Gloas devnet nodes upgraded in place. [#11246](https://github.com/Consensys-Incorporated/teku/issues/11246)
 - The OpenAPI documentation for the `pending_deposits`, `pending_partial_withdrawals`, `pending_consolidations` and `proposer_lookahead` state endpoints now lists `406 Not Acceptable` instead of `415 Unsupported Media Type`, as in the beacon-API spec. [#11558](https://github.com/Consensys-Incorporated/teku/pull/11558)
 - A peer can no longer stop the node from serving req/resp requests by leaving inbound streams open. An inbound RPC stream now holds its slot for at most two minutes, and is closed if it is still open five seconds after the node has finished sending its response; a single peer can hold at most 16 of the 128 node-wide inbound stream slots. A response that takes longer than two minutes to send is cut off. New metrics `rpc_inbound_streams_active`, `rpc_inbound_streams_rejected_total{reason}` and `rpc_inbound_streams_expired_total{reason}` show the slots in use, the streams turned away and the streams closed by a deadline.