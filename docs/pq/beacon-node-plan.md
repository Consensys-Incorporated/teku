# PQ beacon-node pipelines (phase 6)

Phase 6 of [pq-fork-plan.md](pq-fork-plan.md): taking the merged PQ state transition into the beacon node's pipelines (§3.8 to §3.12 there). Targets consensus-specs `pq` at `2f43bc0f9`.

**Goal of the first milestone: Teku follows a PQ chain.** A non-validating node imports PQ blocks and envelopes, runs fork choice over them and validates PQ gossip. Producing blocks for local validators needs the validator client's duties (phase 9c) and the REST changes (phase 8) as well, so it comes after. The follower milestone needs slices 6a to 6c here plus phase 7's topics.

## Slices

| Slice | What | State |
|---|---|---|
| 6a | Block production at the spec level, and test chains through the PQ fork | In review (#11674) |
| 6b | Block import and fork choice over PQ chains | In review (#11676, stacked on #11674) |
| 6c | Gossip validation of PQ messages | In progress (`pq-bn-gossip`): 6c-1 done |
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
  - Attestations in PQ test blocks arrive with 6b: `AttestationGenerator` builds unsigned PQ attestations, and `ChainBuilder` includes them two slots later (`MIN_ATTESTATION_INCLUSION_DELAY_PQ`), never across the fork.

### 6b: block import and fork choice

- **Already worked once 6a fixed `isParentBlockFull`.** `ForkChoicePqTest` imports a chain from EIP-8483 through the PQ fork with a storage system:
  - blocks through `ForkChoice.onBlock`, and envelopes through `onExecutionPayloadEnvelope`;
  - each PQ block lands on its parent's full node, across the fork too.
- **Attestations in PQ blocks:** the block's STARK proof covers their signatures, and their votes reach fork choice. A block carrying attestations gives the attested block weight and becomes head.
- **PQ blocks carry no payload attestations**, so block import applies no PTC votes from them.
- **Test chains:**
  - `AttestationGenerator` builds unsigned PQ attestations and aggregates them by their bits.
  - `ChainBuilder` includes attestations at PQ's inclusion delay of 2, and never puts pre-fork attestations in a PQ block.
- **Moved to 6c:** votes from gossip, meaning single attestations (XMSS), aggregates (proofs) and payload attestation messages (XMSS). `ForkChoice.onAttestation` and `onPayloadAttestationMessage` take them after gossip validation.

### 6c: gossip validation

**6c-1: signed objects (done).**
- **`GossipValidationHelper`** verifies XMSS signatures against the validator's, or a builder's, XMSS key in the state, at the leaf a message's type uses. A self-built message is signed by the latest block's proposer. The verifier is injectable (default: leanXMSS), so tests can exercise rejections without real keys.
- **Blocks:** the proposer's signature at offset 0, both before a future block is saved and after its state is built. The finalized state may be from before the fork, without XMSS keys, so the head state is the fallback.
- **Envelopes** at offset 4, **preferences** at offset 3 of the proposal slot, and **bids** at the leaf the signature carries, which must be in the builders' bid range (`verify_execution_payload_bid_signature`).

**6c-2: attestations.**
- Single attestations: XMSS at the target epoch's attestation leaf; the subnet is modulo 16.
- Aggregates:
  - no selection proof; the aggregator must be in the aggregation committee;
  - verify the proof against the aggregate's statements;
  - apply the best-aggregate (balance) seen rule.
- Payload attestation messages: these have their own PQ type (`PayloadAttestationMessagePq`), so they come with the PQ topic decoders. XMSS at offset 2.

**6c-3: the rest.**
- **Slashings:** `ProposerSlashingValidator` and `AttesterSlashingValidator`, still BLS today.
- **Removed messages are rejected:** voluntary exits, BLS changes, sync committee messages and contributions.
- **An async XMSS verification service:** about 30 µs to 70 µs per verify, kept off the network threads.

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
