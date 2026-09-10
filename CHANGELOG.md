# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Promoted `--Xvalidators-external-signer-concurrent-limit` to non-experimental `--validators-external-signer-concurrent-limit`, and imposed maximum limit of 1024
 - Removed the `--deposit-snapshot-enabled` and `--Xdeposit-snapshot` CLI options along with the bundled deposit tree snapshots.

### Additions and Improvements
 - Enabled aircompressor-v3 by default for gossip and RPC snappy encoding/decoding. The experimental flags `--Xp2p-gossip-snappy-aircompressor-enabled` and `--Xp2p-rpc-snappy-aircompressor-enabled` can be used to revert to snappy-java if needed.
 - Increased the default value of `--p2p-target-subnet-subscriber-count` from 2 to 3.
 - Added native support for the [Plataberget testnet](https://plataberget.dev/). Use `--network=plataberget` to join the network.
 - `--validator-keys` now accepts `<KEY_DIR>:<PASS_FILE>`, using a single password file for all keystores found in the directory.
 - Improved debug/beacon/states endpoint to allow searching of the finalized state root, to assist third party products searching on roots.

### Bug Fixes
 - Fixed validator duties for the first epoch of a fork scheduled after genesis failing with `Expected a Gloas state but got: BeaconStateFuluImpl`, which left validators without attestation duties for that epoch.
 - Fixed startup from a Gloas genesis state (e.g. a devnet with `GLOAS_FORK_EPOCH: 0`), which failed with `Genesis block root ... does not match genesis state latest block root`. The Gloas genesis block body now embeds the state's `latest_execution_payload_bid`, matching the ethpandaops genesis generator, Lighthouse and Lodestar.
 - Fixed `data_column_sidecar` gossip decoding to use the schema of the topic's fork instead of the highest supported milestone. Previously, on networks with Gloas scheduled, every Fulu-era column sidecar received via gossip failed deserialization.
 - Validate `BeaconBlocksByRoot` responses against the requested block roots before accepting them.
 - Fixed a regression where archive nodes using `leveldb-tree` storage would take an extremely long time to start up.
 - Post-Electra, the `committee_index` query parameter in `GET /eth/v1/validator/attestation_data` is now ignored instead of rejected when non-zero, matching the behaviour of other consensus clients.
 - Trigger an immediate peer search when publishing sync committee messages fails because there are no peers available on the required gossip topic.
 - Fixed gossip wire validator to reject inbound messages containing the `key` field.
 - Fixed the gossip message size gate comparing the compressed payload size against the uncompressed `MAX_PAYLOAD_SIZE`.
