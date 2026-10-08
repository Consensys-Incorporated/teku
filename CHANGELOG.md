# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Added gossipsub metrics `libp2p_gossip_gossipsub_*` (off by default; enable them with `--Xmetrics-additional-categories=LIBP2P_GOSSIP`).
 - Block production now resends `forkchoiceUpdated` when the execution layer returned no `payloadId` (e.g. `SYNCING`) for the one sent ahead of the proposal slot, instead of missing the proposal. A warning is logged when no `payloadId` is returned.
 - Set gossip `max_total_fields` limit to 32768. See [#11341](https://github.com/Consensys-Incorporated/teku/issues/11341).
 - Lowered the memory limit for blocks waiting on a missing parent, and added the `beacon_pending_pool_bytes` metric reporting the size in bytes of blocks in each pending block pool.

### Bug Fixes
 - The validator client now sends the required `Eth-Consensus-Version` header when submitting gloas proposer preferences to the beacon node.
 - The validator client now sends the required `Eth-Consensus-Version` header when submitting gloas payload attestation messages to the beacon node.
 - The ENR `eth2` field now advertises the current fork version as `next_fork_version` when a BPO fork is scheduled before the next hard fork, as the Fulu p2p specification requires.
 - A block production request that fails no longer keeps its preparation for the slot, so a retry within the same slot starts from a fresh preparation.
 - Fixed valid voluntary exits and slashings referring to an earlier fork being rejected on gossip, which wrongly penalised the peers forwarding them.
 - Peers discovered with both QUIC and TCP addresses are now dialed over TCP when the QUIC dial fails, instead of being retried over QUIC only. [#11397](https://github.com/Consensys/teku/issues/11397)
