# PQ beacon-node pipelines (phase 6)

Phase 6 of [pq-fork-plan.md](pq-fork-plan.md): taking the merged PQ state transition into the beacon node's pipelines (§3.8 to §3.12 there). Targets consensus-specs `pq` at `2f43bc0f9`.

**Goal of the first milestone: Teku follows a PQ chain.** A non-validating node imports PQ blocks and envelopes, runs fork choice over them and validates PQ gossip. Producing blocks for local validators needs the validator client's duties (phase 9c) and the REST changes (phase 8) as well, so it comes after. The follower milestone needs slices 6a to 6c here plus phase 7's topics.

## Slices

| Slice | What | State |
|---|---|---|
| 6a | Block production at the spec level, and test chains through the PQ fork | In progress (`pq-bn-blocks`) |
| 6b | Block import and fork choice over PQ chains | Not started |
| 6c | Gossip validation of PQ messages | Not started |
| 6d | Attestation pool, proofs and packing | Not started |
| 6e | Beacon-node block production | Not started |

### 6a: block production at the spec level, and PQ test chains

- **`BeaconBlockBodyBuilderPq`.** `SchemaDefinitionsPq.createBeaconBlockBodyBuilder` and `BeaconBlockBodySchemaPq.createBlockBody` no longer throw.
  - The RANDAO reveal is the VRF proof; the body also carries the attestation proof. `BeaconBlockBodyBuilder` gains `vrfRandaoReveal` and `attestationProof`, with `supports*` checks.
  - The BLS `randaoReveal` fails loudly, so an unchanged caller can't build a bad block.
  - Exits, the sync aggregate, BLS changes, payload attestations and deposits may only be left empty.
- **Bodies are read through their accessors, not cast to Gloas,** where PQ shares Gloas's code:
  - `MiscHelpersGloas.isParentBlockFull` read the parent's bid through `toVersionGloas()`, so a PQ parent was never full. Fork choice would have put every child of a PQ block on its parent's empty branch.
  - `ForkChoiceUtilGloas.getParentNode` and `DataColumnSidecarUtilGloas.getKzgCommitments` required a Gloas body.
- **Test chains through the PQ fork** (`PqTestKeys`, `ChainBuilder`, `BlockProposalTestUtil`):
  - `ChainBuilder` registers every validator's XMSS key and VRF root in an EIP-8483 genesis, so none is exited at the fork. A PQ genesis can't be built from BLS deposits, so test specs schedule PQ at epoch 1 or later.
  - Each test validator's VRF tree has identical leaves, so its root and proofs take 31 hashes, not 2^32. The proofs are real and pass `process_randao`.
  - Blocks, envelopes and bids carry stub XMSS signatures that only hold their leaf index. Tests importing them disable XMSS verification, as the spec's own tests do.
  - Not yet: attestations in PQ test blocks; they need 6d's PQ attestations and proofs.

### 6b: block import and fork choice

- Import PQ chains through `ForkChoice` and `BlockImporter` with a storage system, across the fork.
- PQ blocks have no payload attestations, so block import applies no PTC votes from them.
- `ForkChoice.onAttestation` for PQ attestations from blocks: the block's STARK proof covers their signatures.
- `ForkChoice.onPayloadAttestationMessage` with XMSS signatures (offset 2).
- `ValidatableAttestation` and `DeferredAttestations` must not assume a BLS aggregate signature.

### 6c: gossip validation

- **An async XMSS verification service** for gossip. Each verify is independent (about 30 µs to 70 µs), so there's no batching gain, but it must not run on the network threads.
- **Blocks** (`BlockGossipValidator`, `GossipValidationHelper`): the proposer's XMSS signature at offset 0.
- **Single attestations:** XMSS at the target epoch's attestation leaf; the subnet is modulo 16.
- **Aggregates:**
  - no selection proof; the aggregator must be in the aggregation committee;
  - verify the proof against the aggregate's statements;
  - apply the best-aggregate (balance) seen rule.
- **Envelopes, bids, proposer preferences and payload attestation messages:** XMSS at offsets 4, the bid range, 3 and 2.
- **Slashings:** `ProposerSlashingValidator` and `AttesterSlashingValidator`, still BLS today.
- **Removed messages are rejected:** voluntary exits, BLS changes, sync committee messages and contributions.

### 6d: attestation pool, proofs and packing

- Keep the singles' XMSS signatures per (data, committee), in committee order, for `get_aggregate_stark`.
- Keep the best network aggregate with its proof per (data, committee). A proposer can only include proofs it holds.
- `AttestationPackerPq`:
  - takes attestations from `slot - 2` or earlier (inclusion delay 2), preferring exactly `slot - 2`;
  - groups committees with equal data;
  - merges the proofs in body order;
  - budgets proof bytes (`MAX_STARK_PROOF_SIZE`).
- Switch off the exit, BLS-change and sync-committee pools for PQ.

### 6e: beacon-node block production

- **`BlockFactory` and `BlockOperationSelectorFactory`:**
  - the VC's VRF proof as the RANDAO reveal (REST changes in phase 8);
  - the merged attestation proof;
  - a self-built bid with an all-zero XMSS signature;
  - no payload attestations.
- **`BlockFactoryGloas`** detects a self-built bid by its BLS signature being the point at infinity. PQ needs the all-zero XMSS check.
- **The self-built envelope** is signed by the validator client (phase 9c).
