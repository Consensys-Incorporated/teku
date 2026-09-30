# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

### Bug Fixes
 - The validator client now sends the required `Eth-Consensus-Version` header when submitting gloas proposer preferences to the beacon node.
 - The ENR `eth2` field now advertises the current fork version as `next_fork_version` when a BPO fork is scheduled before the next hard fork, as the Fulu p2p specification requires.
 - A block production request that fails no longer keeps its preparation for the slot, so a retry within the same slot starts from a fresh preparation.
