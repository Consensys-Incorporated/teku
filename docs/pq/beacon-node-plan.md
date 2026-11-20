# PQ beacon-node pipelines (phase 6)

Phase 6 of [pq-fork-plan.md](pq-fork-plan.md): taking the merged PQ state transition into the beacon node's pipelines (§3.8 to §3.12 there). Targets consensus-specs `pq` at `2f43bc0f9`.

**Goal of the first milestone: Teku follows a PQ chain.** A non-validating node imports PQ blocks and envelopes, runs fork choice over them and validates PQ gossip. Producing blocks for local validators needs the validator client's duties (phase 9c) and the REST changes (phase 8) as well, so it comes after. The follower milestone needs slices 6a to 6c here plus phase 7's topics.

## Slices

| Slice | What | State |
|---|---|---|
| 6a | Block production at the spec level, and test chains through the PQ fork | In review (#11674) |
| 6b | Block import and fork choice over PQ chains | In review (#11676, stacked on #11674) |
| 6c | Gossip validation of PQ messages | Done: 6c-1 (#11677), 6c-2 (#11678), 6c-3 (`pq-bn-gossip-rest`) |
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

**6c-2: attestations (done, except payload attestation messages).**
- **Single attestations go through the existing pipeline.** `SingleAttestationPq` implements `Attestation`, as Electra's `SingleAttestation` does; the BLS-only accessors throw.
  - `AttestationUtilPq` verifies a PQ single at its target epoch's attestation leaf, after checking the attester is in the committee.
  - It then converts it to a one-attester PQ aggregate for the pool and fork choice, as Electra does.
  - `ValidatableAttestation` keeps the original single, whose XMSS signature aggregators prove (6d).
- **Subnets:** attestation subnets are computed modulo `ATTESTATION_SUBNET_COUNT_PQ` (16).
- **Aggregates:** `AggregateAttestationValidatorPq` handles them, since `SignedAggregateAndProofPq` is not a `SignedAggregateAndProof`. Its PQ checks run against the attestation's state:
  - exactly one committee;
  - the committee index is in range, and the bits match the committee size;
  - the aggregate has participants;
  - the aggregator is in the aggregation committee (`AttestationUtilPq.isAggregator`), with no selection proof;
  - the aggregator's XMSS signature at offset 1;
  - the STARK proof (`isValidAggregateProof`).

  The aggregate is then marked verified, keeps its proof for the pool, and goes through `AttestationValidator`'s shared checks. The seen rules are the spec's superset rule and one aggregate per aggregator and epoch.
- **Done in phase 7:** the PQ topic decoders.
  - `PqAggregateGossipManager` hands `SignedAggregateAndProofPq` to `AttestationManager`, which routes it to `AggregateAttestationValidatorPq`.
  - `PqPayloadAttestationMessageGossipManager` hands `PayloadAttestationMessagePq` to `PayloadAttestationPool.addRemotePq`.
    - `PayloadAttestationMessageGossipValidator` checks the XMSS signature at the PTC attester leaf (offset 2) for PQ slots, and rejects a message whose signature type doesn't match its slot's fork.
    - A valid message goes to fork choice only: PQ blocks carry no payload attestations, so it is neither kept for block production nor queued while its block is unknown (ignored instead, which the spec allows).

**6c-3: slashings (done).**
- **Proposer slashings:** `OperationSignatureVerifierPq` checks a proposer slashing's headers with XMSS at the proposer leaf of their slot. Gossip validation (`ProposerSlashingValidator`, through `Spec.verifyProposerSlashingSignature`) and block processing both use it. `BlockProcessorPq` no longer has its own copy.
- **Attester slashings** already worked: `Spec.validateAttesterSlashing` checks both indexed attestations with `AttestationUtilPq`, which verifies their proofs.
- **Moved elsewhere:**
  - **Removed messages** (voluntary exits, BLS changes, sync committee messages and contributions): rejecting them is phase 7's PQ topic subscriptions, which leave those topics out, and phase 8's REST pools.
  - **An async XMSS verification service** (about 30 µs to 70 µs per verify): to be sized once a devnet shows the gossip load.

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
