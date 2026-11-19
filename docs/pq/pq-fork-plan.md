# Plan: adding the consensus-specs "PQ" fork to Teku

- **Spec source:** `consensus-specs` at `upstream/pq`, commit `ee867e912` (2026-11-17, "Use the superset rule for aggregates after their slot").
- **Devnet target (2026-11-20): commit [`2f43bc0f9`](https://github.com/ethereum/consensus-specs/tree/2f43bc0f989f47af81df390a90bc1f78e2436323)** (2026-11-19, "Hash the VRF tree with Keccak-256"). It adds five commits on `a4d16c5be`:
  - `2f43bc0f9` makes the VRF tree a Keccak-256 Merkle tree: `is_valid_vrf_proof` walks the branch with the new `keccak256` helper instead of `is_valid_merkle_branch`. This is the only consensus change. RANDAO mixing and SSZ stay on sha256.
  - `70b907fd4` defines how a validator derives its VRF tree from a secret `vrf_seed` (`compute_vrf_leaf`, `compute_vrf_node`, `compute_vrf_root`, `compute_vrf_proof`, in `validator.md`), for the validator client (phase 9).
  - `5286c65cd` adds `PQ` to `TESTGEN_FORKS`, so PQ reference vectors can be generated; `62d77b268` adds randomized block tests; `2f42da884` skips fast confirmation tests that assume a one-slot delay.
- **Previous devnet target (2026-11-19): commit `a4d16c5be`** (2026-11-18, "Find the attested block's slot to check its payload availability"), the current `pq` tip. It adds three commits on `ee867e912`, all PQ-only:
  - `98814e4be` returns aggregate gossip to the plain superset rule. `best_aggregate_balance` is gone.
  - `ec4e21652` adds preset `MIN_ATTESTATION_INCLUSION_DELAY_PQ = 2`, used by `process_attestation`. The timely head flag now needs an inclusion delay of **exactly** 2.
  - `a4d16c5be` adds `get_latest_block_slot`, so the head check reads payload availability at the attested block's slot (the grandparent with a two-slot delay). The `parent_slot` parameter comes back.
  - The EIP-8483 spec is unchanged, and the test-helper edits only change behaviour post-PQ, so Teku's merged EIP-8483 (`pq-devnet-0`) and its vectors are unaffected.
- **Fork lineage in the spec:** `GLOAS -> EIP8483 -> PQ` (`PREVIOUS_FORK_OF[PQ] = EIP8483`, `PREVIOUS_FORK_OF[EIP8483] = GLOAS`). EIP8483 is not in `TESTGEN_FORKS`; PQ is from `5286c65cd`, so its vectors can be generated from the target commit.
- **Teku baseline:** the `pq-devnet-0` branch, which carries EIP-8483 (#11649), the leanXMSS module (#11652), the PQ milestone skeleton (#11654) and the PQ containers as they land (#11656 onwards).
  - Its classes are `SpecConfigEip8483`, `SchemaDefinitionsEip8483`, `SpecLogicEip8483`, `Eip8483StateUpgrade`, `BeaconStateSchemaEip8483`, `ExecutionRequestsSchemaEip8483`, `ValidatorPqRegistration` and `BuilderPqRegistration`.
- **Status (2026-11-19):** phases 1 (milestone skeleton) and 2 (crypto API, on lean-pq) are done; phase 3 (containers) is complete or in review ([containers-plan.md](containers-plan.md)); phase 4 (state transition, with phase 5's fork upgrade at its switch-over) is under way ([state-transition-plan.md](state-transition-plan.md)). This document was written before the work started; sections that recorded open decisions now record what was decided.
- **Markers:** statements are read from the spec or code unless marked **[inferred]**. Sizes are relative effort, not commitments.

---

## 0. TL;DR

PQ is the largest fork Teku has faced since Altair. It is not "swap BLS for XMSS". It also:
- removes sync committees, the light client, voluntary-exit operations, BLS-to-execution changes, and payload attestations in blocks;
- changes the `Validator` and `Builder` records (32-byte XMSS pubkey, plus a new `vrf_root` for validators);
- replaces RANDAO signatures with a committed VRF Merkle proof;
- removes signatures from `Attestation` and `IndexedAttestation`, and adds a block-level STARK `proof`;
- replaces self-selected aggregators with a state-cached `aggregation_committee_window`;
- reduces the slot to 16 committees and gossip to 16 attestation subnets;
- adds three new EL request types (0x07 validator preregistration, 0x08 builder preregistration, 0x09 voluntary exit);
- rewrites pubkeys at the fork and exits unregistered validators.

Teku-side, the hardest parts are not the state transition. They are:
1. **The BLS-everywhere assumption.** `Validator` is a single static `Container8` with `SszPublicKey`. `ValidatorIndexCache` is keyed by `BLSPublicKey`. The `Signer` returns `BLSSignature`. The VC identity is the BLS pubkey. There are about 200 main files importing `BLSPublicKey` and about 180 importing `BLSSignature`.
2. **The attestation pipeline.** The pool aggregates BLS signatures today. PQ needs it to keep per-attester XMSS signatures, build proofs, and merge proofs.
3. **The validator client.** It needs XMSS keys, a VRF tree, and leaf-index protection for every signed message type, plus a fork-boundary switch from BLS to XMSS.

**Crypto recommendation** (revised 2026-11-18 after finding `ethereum/lean-pq-bindings`, §2.2):
- Use **`io.github.crate-crypto:lean-pq`** (Maven Central, Java 25 FFM, natives bundled) for leanXMSS keygen, sign and verify. It is the same publisher and release model as `java-eth-kzg`. A pure-Java port is no longer needed for the first devnet; keep it as a fallback only.
- Hide it behind a small `infrastructure/xmss` API with an `XmssConstants.verificationDisabled` switch, which the reference tests require.
- Keep STARK as the spec's concatenation stand-in for consensus, behind an interface. lean-pq's `Aggregate` is a real leanVM proof but **does not prove leaf indices**, so it cannot implement the spec's `VerifyAggregates` as written (§2.2, §5.17). From lean-pq 0.13.0, proving and verifying aggregates need a `leanvm-sidecar` process (§2.2).

---

## 1. Spec summary (what PQ changes)

### 1.1 Intent (beacon-chain.md intro)

PQ is an emergency fork for when elliptic-curve cryptography can no longer be trusted. The full intent is summarised in the TL;DR above. Points not covered there:
- BLS signatures are replaced with **leanXMSS** (stateful, hash-based).
- Every validator message is signed at a leaf index derived from its slot and type. An attestation uses its target epoch's leaf. Messages without their own leaf use a reserved leaf.
- XMSS signatures are not unique, so RANDAO can no longer use a signature.
- `IndexedAttestation` has a `proof` in place of its signature.
- `PayloadAttestationMessage`s are still gossiped and used by fork choice.

### 1.2 New types, constants, presets, configs

| Kind | Name | Value / definition |
|---|---|---|
| type | `XMSSPubkey` | `Bytes32` (Merkle root ‖ public parameter) |
| type | `XMSSSignature` | `ByteVector[XMSS_SIGNATURE_SIZE]` = 2404 bytes; the first 4 bytes are the leaf index (LE) |
| type | `VRFProof` | `Vector[Bytes32, VRF_TREE_DEPTH + 1]` (leaf ‖ 31-node branch) |
| type | `AttestationProof` | `ByteList[MAX_STARK_PROOF_SIZE]` |
| type | `CommitteeBits` | **modified**: `BitVector[MAX_COMMITTEES_PER_SLOT_PQ]` |
| type | `ValidatorPreregistrationRequests`, `BuilderPreregistrationRequests`, `ValidatorPreregistrations`, `BuilderPreregistrations`, `VoluntaryExitRequests`, `AggregationCommittee` (of `ValidatorIndex`), `AggregationCommittees` | `ProgressiveList[...]` |
| type | `AggregationCommitteeWindow` | `Vector[AggregationCommittees, (MIN_SEED_LOOKAHEAD + 2) * SLOTS_PER_EPOCH]` (96 on mainnet) |
| const | `AGGREGATORS_PER_COMMITTEE` | 4 |
| const | `XMSS_SIGNATURE_SIZE` | 2404 |
| const | `XMSS_RESERVED_LEAVES` / `XMSS_ATTESTATION_LEAVES` / `XMSS_BUILDER_BID_LEAVES` / `XMSS_LEAVES_PER_SLOT` | 2^15 / 2^29 / 2^30 / 8 |
| const | `VRF_TREE_DEPTH` | 31 |
| const | `LEAF_OFFSET_*` | BEACON_PROPOSER 0, AGGREGATE_AND_PROOF 1, PTC_ATTESTER 2, PROPOSER_PREFERENCES 3, EXECUTION_PAYLOAD_ENVELOPE 4 |
| const | `XMSS_DEPOSIT_PREFIX` | `Bytes16()` (16 zero bytes) |
| const | `LEGACY_XMSS_PUBKEY_DOMAIN` / `LEGACY_VRF_ROOT_DOMAIN` | `0x00` / `0x01` |
| const | `VALIDATOR_PREREGISTRATION_REQUEST_TYPE` / `BUILDER_PREREGISTRATION_REQUEST_TYPE` / `VOLUNTARY_EXIT_REQUEST_TYPE` | `0x07` / `0x08` / `0x09` |
| preset | `MAX_COMMITTEES_PER_SLOT_PQ` | mainnet 16, minimal 4 |
| preset | `MAX_STARK_PROOF_SIZE` | 2^20 (placeholder) |
| preset | `MAX_ATTESTATIONS_PQ` | 16 |
| preset | `MAX_VALIDATOR_PREREGISTRATION_REQUESTS_PER_PAYLOAD` / `MAX_BUILDER_PREREGISTRATION_REQUESTS_PER_PAYLOAD` / `MAX_VOLUNTARY_EXIT_REQUESTS_PER_PAYLOAD` | 64 / 4 / 16 |
| preset (beacon-chain.md only, **not** in `pq.yaml`) | `PREREGISTRATIONS_LIMIT` / `PREREGISTRATION_EXPIRY_SLOTS` | 2^19 / 2^18 slots |
| preset (p2p) | `MAX_SIGNED_AGGREGATE_AND_PROOF_SIZE_PQ` / `MAX_ATTESTER_SLASHING_SIZE_PQ` / `MAX_SIGNED_EXECUTION_PAYLOAD_BID_SIZE_PQ` | mainnet 1,055,231 / 2,621,720 / 199,240; minimal 1,052,158 / 2,228,504 / 199,240 |
| config | `PQ_FORK_VERSION` / `PQ_FORK_EPOCH` | mainnet `0xe0000000`, minimal `0xe0000001` / far future |
| config | `UNREGISTERED_VALIDATOR_WITHDRAWABILITY_DELAY` | 16384 epochs |
| config (p2p) | `ATTESTATION_SUBNET_COUNT_PQ` | 16 |

`pysetup/spec_builders/pq.py` deprecates the following.

- **Constants:**
  - `BUILDER/VALIDATOR_PQ_REGISTRATION_REQUEST_TYPE`
  - `CURRENT/NEXT_SYNC_COMMITTEE_GINDEX_GLOAS`
  - `DOMAIN_VOLUNTARY_EXIT`
  - `G2_POINT_AT_INFINITY`
  - `TARGET_AGGREGATORS_PER_COMMITTEE`
- **Presets:**
  - `EPOCHS_PER_SYNC_COMMITTEE_PERIOD`, `SYNC_COMMITTEE_SIZE`, `MIN_SYNC_COMMITTEE_PARTICIPANTS`, `UPDATE_TIMEOUT`
  - `MAX_ATTESTATIONS_ELECTRA`, `MAX_COMMITTEES_PER_SLOT`, `MAX_BLS_TO_EXECUTION_CHANGES`, `MAX_PAYLOAD_ATTESTATIONS`, `MAX_VOLUNTARY_EXITS`
  - the two EIP8483 per-payload limits
  - the three Gloas size bounds
- **Configs:** `CONTRIBUTION_DUE_BPS_GLOAS`, `SYNC_MESSAGE_DUE_BPS_GLOAS`.
- **Containers:**
  - every sync-committee and light-client container
  - `VoluntaryExit`/`SignedVoluntaryExit`, `BLSToExecutionChange`
  - `PayloadAttestation`, `IndexedPayloadAttestation`
  - the four EIP8483 registration containers
- **Functions (~70):**
  - `process_sync_aggregate`, `process_voluntary_exit`, `process_bls_to_execution_change`, `process_payload_attestation`
  - `get_epoch_signature`, `get_slot_signature`, `notify_ptc_messages`
  - `is_valid_deposit_signature`, `is_valid_builder_deposit_signature`, `eth_fast_aggregate_verify`
  - all light-client functions
  - the gossip validators for voluntary exits, BLS changes and sync committees

### 1.3 Containers

**Modified (beacon-chain.md):**
- **`Attestation`** (ProgressiveContainer, width 4, gap 2): the `signature` is removed.
- **`IndexedAttestation`** (width 3): `signature` is replaced by `proof: AttestationProof`.
- **`BeaconBlockBody`** (ProgressiveContainer, width 14, gaps 7, 8, 9, 11):
  - `randao_reveal: VRFProof`;
  - `voluntary_exits`, `sync_aggregate`, `bls_to_execution_changes` and `payload_attestations` are removed;
  - `proof: AttestationProof` is added at index 13.
- **`BeaconState`** (ProgressiveContainer, width 51, gaps 22, 23, 46, 47):
  - `current_sync_committee`, `next_sync_committee`, `validator_pq_registrations` and `builder_pq_registrations` are removed;
  - added: `validator_preregistrations` (48), `builder_preregistrations` (49), `aggregation_committee_window` (50).
- **`Validator`** (plain Container): `pubkey: XMSSPubkey`, plus a new `vrf_root: Root` at position 1.
- **`Builder`**: `pubkey: XMSSPubkey`.
- **`ExecutionRequests`** (width 10, gaps 5, 6): adds `validator_preregistrations` (7), `builder_preregistrations` (8) and `voluntary_exits` (9).
- **EL request formats are kept.** `DepositRequest`, `PendingDeposit`, `BuilderDepositRequest`, `BuilderExitRequest`, `WithdrawalRequest` and `ConsolidationRequest` keep 48-byte pubkeys and 96-byte signatures, now typed as raw `Bytes48`/`Bytes96`:
  - A PQ pubkey field is `0^16 ‖ xmss_pubkey`.
  - A PQ deposit "signature" is `vrf_root ‖ 0^64`.
- **XMSS signatures:** `SignedBeaconBlock`, `SignedBeaconBlockHeader`, `SignedExecutionPayloadBid`, `SignedExecutionPayloadEnvelope`, `SingleAttestation`, `PayloadAttestationMessage` and `SignedProposerPreferences` (p2p) now carry `XMSSSignature`.
- **validator.md:**
  - `AggregateAndProof` drops `selection_proof` and adds `proof: AttestationProof`.
  - `SignedAggregateAndProof` carries an `XMSSSignature`.

**New:**
- `ValidatorPreregistrationRequest(pubkey, withdrawal_credentials, vrf_root)`
- `ValidatorPreregistration(+expiry_slot)`
- `BuilderPreregistrationRequest(pubkey, withdrawal_credentials)`
- `BuilderPreregistration(+expiry_slot)`
- `VoluntaryExitRequest(pubkey)`
- dataclass `XMSSAggregateStatement(pubkeys, leaf_index, message)`

### 1.4 Helpers

**Crypto:**
- `xmss.Sign(privkey, leaf_index, msg)`, `xmss.Verify(pk, leaf_index, msg, sig)` (also checks the embedded leaf index), `xmss.GetLeafIndex(sig)`.
- `stark.ProveAggregates(statements, sigs)`, `stark.MergeProofs(proofs)`, `stark.VerifyAggregates(statements, proof)`. The reference implementation concatenates signatures.

**Misc:**
- `compute_legacy_xmss_pubkey(bls_pk) = sha256(pk ‖ 0x00)`.
- `compute_legacy_vrf_root = sha256(pk ‖ 0x01)`.
- `compute_deposit_pubkey`: legacy hash if the top 16 bytes are non-zero, else `pk[16:]`.
- `compute_deposit_vrf_root`: `signature[:32]` or the legacy value.
- `compute_xmss_attestation_leaf_index(epoch) = 2^15 + epoch`.
- `compute_xmss_leaf_index(slot, offset) = 2^15 + 2^29 + 2^30 + 8*slot + offset`.
- `compute_aggregation_committee(state, slot, index)`: sampling without replacement, up to 4 times, using the Gloas `compute_balance_weighted_selection`, seeded by `sha256(get_seed(DOMAIN_SELECTION_PROOF) ‖ slot ‖ index ‖ k)`.
- `compute_aggregation_committees(state, slot)`.

**Predicates:**
- `is_active_{validator,builder}_preregistration`: compared against `state.latest_execution_payload_bid.slot`.
- `is_builder_bid_leaf_index`, `is_legacy_pubkey`.
- `is_valid_vrf_proof`: `is_valid_merkle_branch` with depth 31, `index=slot`, `root=validator.vrf_root`.
- Modified `is_pending_validator`: matches by deposit pubkey.
- Modified `is_valid_indexed_attestation`:
  - bound is `MAX_VALIDATORS_PER_COMMITTEE * MAX_COMMITTEES_PER_SLOT_PQ`;
  - one statement, verified with `stark.VerifyAggregates`.

**Accessors:**
- `get_committee_count_per_slot` caps at `MAX_COMMITTEES_PER_SLOT_PQ`. This changes the shuffling, committee sizes and committee indices.
- `get_indexed_attestation(state, att, proof)`.
- `get_aggregation_committee(state, slot, index)` reads the window. It allows the previous epoch through `current + MIN_SEED_LOOKAHEAD`.
- `get_attestation_statements(state, att)` returns one statement per committee.
- Preregistration lookup and active-binding getters (linear scans in the spec).

**Mutators:**
- `queue_excess_active_balance` now writes `0^16 ‖ xmss_pk` and an empty signature.
- `remove_{validator,builder}_preregistration` (order-preserving).

### 1.5 State transition

**Signatures:**
- `verify_block_signature`, `verify_execution_payload_envelope_signature` and `process_proposer_slashing` use `xmss.Verify` with a fixed leaf from slot and offset.
- The envelope leaf is the leaf of `state.latest_block_header.slot`.
- `verify_execution_payload_bid_signature`:
  - reads the leaf from the signature with `GetLeafIndex`;
  - requires `is_builder_bid_leaf_index`.
- A self-build bid must carry the **all-zero `XMSSSignature()`**, not the G2 infinity point.

**Block processing:**
- `process_block` drops `process_sync_aggregate`. This lowers issuance by `SYNC_REWARD_WEIGHT/WEIGHT_DENOMINATOR`; there is no reweighting.
- `process_randao`:
  - asserts `is_valid_vrf_proof(state, proposer, state.slot, reveal)`;
  - `mix = sha256(prev_mix ‖ reveal[0] ‖ DOMAIN_RANDAO)`. Note this is a hash, not an XOR.
- `process_operations`:
  - limits are proposer slashings, attester slashings (`_ELECTRA`) and `MAX_ATTESTATIONS_PQ`;
  - calls `process_stark(state, body)` after the attestations;
  - no exits, BLS changes or payload attestations.
- `process_attestation`:
  - same as Gloas, minus the signature check (`data.index < 2`, builder-payment weight);
  - **[`a4d16c5be`]** asserts `data.slot + MIN_ATTESTATION_INCLUSION_DELAY_PQ (2) <= state.slot`.
- **[`a4d16c5be`]** Modified `get_attestation_participation_flag_indices(state, data, inclusion_delay, parent_slot)`:
  - payload matching looks up `execution_payload_availability` at `get_latest_block_slot(state, data.slot, parent_slot)`, not at `data.slot`;
  - `TIMELY_HEAD` requires `inclusion_delay == 2`, so attestations included later lose the head reward.
- **[`a4d16c5be`]** New `get_latest_block_slot(state, slot, parent_slot)`:
  - returns `parent_slot` if it is at or before `slot`;
  - otherwise walks back through `block_roots` while the root is unchanged, stopping at the oldest root kept.
  - In Teku this is a `BeaconStateAccessorsPq` override plus a helper. The Gloas flag-index method already receives the parent slot, so check its signature first **[inferred]**.
- `process_stark`:
  - concatenates `get_attestation_statements` over all attestations;
  - asserts one `stark.VerifyAggregates(statements, body.proof)`.

**Parent execution requests** (`apply_parent_execution_payload`):
- New limits.
- Processing order:
  1. deposits
  2. withdrawals
  3. consolidations
  4. builder deposits
  5. builder exits
  6. validator preregistrations
  7. builder preregistrations
  8. voluntary exits
- The EIP8483 registration processors are removed.

**Request semantics:**
- **Deposit request:** an active preregistration must match withdrawal credentials and `signature[:32]` (the VRF root), or the deposit is dropped. On a match the binding is consumed.
- **Pending deposits:**
  - Legacy (BLS-pubkey) deposits bypass churn.
  - A new legacy validator is created already exited, withdrawable after `MIN_VALIDATOR_WITHDRAWABILITY_DELAY`.
  - Validator creation uses `get_index_for_new_validator` (index reuse) and `add_validator_to_registry(pubkey, vrf_root, ...)`.
- **Builder deposit:** checks the preregistration binding. A legacy builder deposit triggers `initiate_builder_exit` immediately.
- **Withdrawal and consolidation requests:** ignored if they carry a legacy pubkey; otherwise looked up by `pk[16:]`.
- **Builder exit:** lookup by `pk[16:]`.
- **New `process_validator_preregistration_request` / `process_builder_preregistration_request`:**
  - no-op if a binding is active, if the key is a validator/builder, or if a deposit is pending;
  - capacity counts only active records;
  - expired records are replaced in place.
- **New `process_voluntary_exit_request`:**
  - pubkey lookup;
  - active, not exiting, past `SHARD_COMMITTEE_PERIOD`, no pending withdrawals;
  - then `initiate_validator_exit`.
  - The system contract verifies the signature, so the CL does not.

**Epoch processing:**
- `process_sync_committee_updates` is removed.
- After `process_ptc_window`, adds `process_aggregation_committee_window` (shift by an epoch, then fill epoch `current + MIN_SEED_LOOKAHEAD + 1`) and `process_preregistration_expiry` (garbage collection only).

### 1.6 Fork upgrade (`fork.md`, `upgrade_to_pq(pre: eip8483.BeaconState)`)

- **`compute_validators`:**
  - **Registered** (from `pre.validator_pq_registrations`, excluding pq_data whose pubkey equals any validator's legacy hash): take `pq_data[:32]` as the pubkey and `pq_data[32:]` as `vrf_root`. Exit and withdrawable epochs are kept.
  - **Unregistered:** take the legacy pubkey and `vrf_root`, `exit_epoch = min(exit, epoch)`, and `withdrawable_epoch = min(withdrawable, epoch + UNREGISTERED_VALIDATOR_WITHDRAWABILITY_DELAY)`. A slashed validator keeps its withdrawable epoch.
  - Indices are preserved.
- **`compute_builders`:** registered builders (keyed by builder BLS pubkey) take the XMSS key. Unregistered builders take the legacy key and `withdrawable_epoch = min(withdrawable, epoch)`.
- **`compute_pending_deposits`:**
  - A deposit for an *existing* validator is rewritten to `0^16 ‖ new xmss pk`.
  - A deposit for a new validator keeps its BLS pubkey and becomes a legacy deposit.
- **Unregistered builders' payments:**
  - `compute_builder_pending_withdrawals` settles their quorum-reaching pending payments into withdrawals.
  - `compute_builder_pending_payments` clears their non-zero entries.
- **Kept:** `proposer_lookahead` and `ptc_window`. Unregistered validators simply miss their slots.
- **New fields:** empty preregistration lists, and `initialize_aggregation_committee_window(post)` (previous epoch empty, then current + lookahead).
- **Spec note:** `latest_execution_payload_bid.execution_requests_root` may commit to the **EIP8483 `ExecutionRequests` type**, so the first post-fork block's parent requests root will not match the PQ type. This is the same fork-boundary gap as EIP8483; the `pqreg` branch built a Teku-specific "convert to pre-fork type and compare" bridge.
- **Spec TODO:** registrations can "steal" pending new-validator deposits. This is unresolved.

### 1.7 Fork choice

- `on_block` drops `notify_ptc_messages`, because blocks carry no payload attestations.
- `on_attestation` checks only that attesters exist. There is no signature or indexed-attestation validation.
- `on_payload_attestation_message` loses `is_from_block`. It always asserts `data.slot == current_slot` and checks `xmss.Verify` at the `LEAF_OFFSET_PTC_ATTESTER` leaf.

### 1.8 P2P

- **Topics:**
  - Removed: `voluntary_exit`, `bls_to_execution_change`, `sync_committee_contribution_and_proof`, `sync_committee_{subnet_id}`, and the light-client topics.
  - Light-client req/resp is also gone.
  - Every signed message type now carries an XMSS signature.
- **`beacon_block`:** XMSS proposer signature at the block leaf. Uses the new body operation limits and `verify_execution_requests_limits` (three new limits).
- **`beacon_aggregate_and_proof`** (heavily modified; latest commit):
  1. exactly one committee;
  2. if the attestation epoch is beyond `current + MIN_SEED_LOOKAHEAD`, advance a copy of the head state;
  3. `is_aggregator` means membership of `get_aggregation_committee`, with no selection proof;
  4. XMSS aggregator signature at the `LEAF_OFFSET_AGGREGATE_AND_PROOF` leaf of `aggregate.data.slot`;
  5. `stark.VerifyAggregates(get_attestation_statements(...), proof)`;
  6. duplicate suppression: **[`a4d16c5be`]** back to the plain superset rule. IGNORE only if an aggregate already seen for the same data and committee covers all of its attesters. `best_aggregate_balance` was removed in `98814e4be`.
- **`beacon_attestation_{subnet}`:**
  - 16 subnets (`ATTESTATION_SUBNET_COUNT_PQ`) via a modified `compute_subnet_for_attestation`, `compute_subscribed_subnet` and `compute_attestation_subnet_prefix_bits`;
  - XMSS verification at the attestation leaf;
  - `Attnets`/`MetaData`/ENR keep 64 bits, but only the first 16 may be set.
- **`payload_attestation_message`, `execution_payload_bid`, `proposer_preferences`, `proposer_slashing`:** XMSS verification at their leaves.
  - The bid's "parent may exit builder" check uses `pk[16:]`.
  - Proposer preferences: the proposer MUST NOT sign two preferences for the same slot even if the dependent root changes.
- **SSZ bounds:** `MAX_*_SIZE_PQ` for aggregates, attester slashings and bids.

### 1.9 Validator and builder duties (validator.md, builder.md)

- **Keys:**
  - The XMSS private key is a 32-byte "hot" seed.
  - The VRF tree (depth 31, one secret leaf per slot) is built once. Its root is registered via EIP8483 as `pq_data = xmss_pk(32) ‖ vrf_root(32)`.
  - A post-fork joiner instead puts the pk into the deposit pubkey and `vrf_root` into the signature field.
  - **Leaf derivation for the VRF tree is not specified** (the tests use identical placeholder leaves).
- **Preregistration:**
  - Sign the binding at a reserved leaf and submit it to the system contract.
  - Wait until the binding is in state, then deposit before expiry.
  - If the binding is not stored, generate a new key.
- **Proposal:**
  - `randao_reveal` = the VRF proof for `block.slot`;
  - `compute_on_chain_aggregate` uses `MAX_COMMITTEES_PER_SLOT_PQ` with no signature;
  - `body.proof = stark.MergeProofs(proofs of each network aggregate, in attestation order then committee order)`;
  - the self-build bid signature is all zeros;
  - `compute_new_state_root` uses `XMSSSignature()`;
  - block signed at leaf offset 0.
- **Attesting:** the `SingleAttestation` signature is at `compute_xmss_attestation_leaf_index(target.epoch)`. `compute_subnet_for_attestation` takes modulo 16.
- **Aggregation:**
  - `is_aggregator(state, slot, index, validator_index)` means membership of the aggregation committee.
  - `get_aggregate_stark(state, aggregate, signatures)` calls `ProveAggregates` over the attesters' **individual XMSS signatures in committee order**.
  - `AggregateAndProof(aggregator_index, aggregate, proof)` is signed at leaf offset 1.
- **PTC:** sign at leaf offset 2. **Preferences:** offset 3. **Envelope** (builder or self-build): offset 4 of the beacon block's slot.
- **No duties for:** sync committees, voluntary-exit operations (exits go via the EL system contract, signed at a reserved leaf), BLS changes.
- **Protection best practices (normative "MUST" / "SHOULD"):**
  - Extend slashing protection to **every** signed message.
  - Keep a persistent per-key record of leaf → signing root.
  - Refuse a different root at a recorded leaf.
  - Record before signing.
  - Reserved leaves are allocated once each in increasing order. The record includes the preregistration leaf.
  - The record must survive restarts and move with the key.
- **Builder:**
  - Bids use any leaf in the 2^30 bid range, each used once.
  - Exit and rejoin with a new key when exhausted.
  - Envelopes are signed at most once per bid even if the proposer equivocates.

### 1.10 Leaf-index layout (derived from the constants above)

| Range (absolute leaf) | Use |
|---|---|
| `[0, 32768)` | reserved: preregistration, voluntary exit, anything else without a slot |
| `[32768, 32768 + 2^29)` | attestations, one per target epoch |
| `[536,903,680, 1,610,645,504)` | builder bids (validators never use these) |
| `1,610,645,504 + 8*slot + {0..4}` | block, aggregate, PTC, preferences, envelope (offsets 5 to 7 unused) |

**[inferred]** With 2^32 total leaves (two height-16 layers), the per-slot region runs out at about slot 335.5M. That is about 127 years at 12 s slots, or about 64 years at 6 s slots.

---

## 2. Cryptography

### 2.1 What leanXMSS / ethlib is (verified via web research and wheel inspection)

- **`ethlib==0.3.0`** (pinned in the pyspec's `pyproject.toml`) is published on PyPI by "ethereum": "formally verified Ethereum primitives, written in Lean".
  - Repo: <https://github.com/ethereum/EthLib>.
  - The XMSS code is **Lean 4 compiled to C**, exposed as a CPython C extension (`ethlib/cryptography/_native*.so` plus a bundled `libleanshared` of about 176 MB). It is not Rust and not pure Python.
- **Scheme:** "leanXMSS" (spec PDF: leanVM `doc/lean_xmss`). It is **not** leanSig/hash-sig (the Poseidon2/KoalaBear scheme used on lean devnets).
  - Two-layer XMSS, each tree of height 16, so a lifetime of 2^32 leaves. Each top-layer leaf signs a bottom-tree root.
  - One-time keys are WOTS+C: chain length 4, v=58 chains, target sum 106, with a 16-byte randomizer ground by the signer (about 2^20 trials).
  - Tweakable hash `Th(P,A,M)` = the first 16 bytes of **Keccak-256**(P‖A‖M). n=16, public parameter 16 B, address 16 B, message 32 B (pre-hashed by the caller).
- **Sizes** (match `utils/xmss_kat.json` and the spec's `XMSS_SIGNATURE_SIZE`):
  - secret key: 32-byte seed;
  - public key: 32 B (root 16 ‖ public parameter 16);
  - signature: **2404 B** = 4-byte LE leaf index + 2 × 1200 B layers (randomizer, 58×16 chain values, 16×16 auth path).
- **Verification cost:** 186 Keccak-f permutations.
- **Signing is deterministic from the seed and stateless.** The caller owns leaf bookkeeping. This is good for Teku: the only safety-critical persisted state is the leaf-usage record, not mutable key material.
- **The parameters have churned.** EthLib #134 replaced XMSS with leanXMSS. EthLib #138 (merged 2026-10-28) switched BLAKE2s to Keccak-256, which changed every vector. Treat the parameters as unfrozen.

### 2.2 Implementations and bindings

| Impl | Lang | Status |
|---|---|---|
| ethlib (reference used by the pyspec) | Lean 4 → C, CPython ext | about 9.6 min keygen and about 22 min sign (EthLib PR #138); usable only as an oracle |
| leanVM `crates/lean_xmss` (<https://github.com/leanEthereum/leanVM>, PR #640 merged 2026-10-17) | Rust | byte-for-byte tested against ethlib vectors; API `key_gen`, `sign`, `verify`, `LeanXmssExpandedSecretKey` with an LRU of bottom trees and `prepare(leaf)` |
| go-leanxmss (<https://github.com/nalepae/go-leanxmss>) | Go | PoC; passes all 41 ethlib vectors |
| **JVM: `lean-pq`** (<https://github.com/ethereum/lean-pq-bindings>, `io.github.crate-crypto:lean-pq`) | Java 25 FFM over Rust leanVM | See below. Supersedes the earlier finding that no JVM binding existed. BouncyCastle XMSS (RFC 8391) is a different construction; `c-leanvm` binds the older Poseidon XMSS. |

**`lean-pq` Java bindings** (checked 2026-11-18 at v0.10.1, released 2026-11-16):
- **Update 2026-11-19, v0.11.0** (leanVM `28368657`, the same commit as the sidecar's README):
  - leanXMSS keys and signatures are unchanged. The EthLib vectors and the spec KAT still pass byte for byte through Teku's `infrastructure/xmss`.
  - Aggregates are now **proof-only**. `toBytes()` has no claims, and `Aggregate.fromBytes(bytes, xmssClaims, sphincsClaims)` verifies against exactly the claims the receiver expects. This matches the spec, where statements are rebuilt from committee bitfields; claims still carry no leaf index. 0.10.x aggregates no longer decode.
  - **Signing is about 9–13× slower per key,** because upstream stopped parallelizing inside the signature crates. Upstream's own measurements, on a Ryzen 7950X3D:
    - key from seed: 0.41 s → 3.6 s;
    - first signature in a bottom tree: 0.45 s → 3.7 s;
    - cached signature: 8 ms → 103 ms.
  - A key now caches its last 8 bottom trees, up from 1.
  - The validator client must sign across keys in parallel. Benchmark slot-time signing at realistic key counts before relying on it.
  - **Measured 2026-11-19** with `XmssBenchmark` (JMH, `infrastructure/xmss/src/jmh`) on an M4 Max with 16 cores:

    | Operation | 0.10.1 | 0.11.0 |
    |---|---|---|
    | verify | 31 µs | 31 µs |
    | sign, cached bottom tree | 6.0 ms | 67 ms |
    | sign, new bottom tree | 202 ms | 2.4 s |
    | load key from seed | 188 ms | 2.3 s |
    | sign 64 keys, 1 / 4 / 12 threads | 416 / 413 / 410 ms | 4.68 / 1.26 / 0.56 s |

  - 0.10.1 already used every core inside each signature, so extra caller threads didn't help it. In 0.11.0 the caller parallelizes instead.
  - Aggregate throughput is therefore close: about 156 vs 115 signatures per second on this machine.
  - Either way, 1,000 keys take about 6–9 s of signing per round. A large validator client cannot sign every key at a single deadline on one host, so PQ duties must be spread through the slot, or keys spread across hosts. This feeds §3.15.10.
- **Update 2026-11-20, v0.13.0** (released 2026-11-19; see the bindings [README](https://github.com/leanEthereum/leanVM/tree/main/bindings#readme) and `CHANGELOG.md`):
  - **The bindings moved into leanVM** (`bindings/`, 0.12.0) and are released from there with tags `bindings-vX.Y.Z`. The Maven coordinates are unchanged. Aggregates from 0.11.x and 0.12.0 reject each other.
  - **Aggregation left the JVM** (0.13.0, leanVM #804). `LeanPq.aggregate`, `LeanPq.setup` and `Aggregate.fromBytes` are gone. Proving and verifying aggregates both go through a `Sidecar`, a client for the `leanvm-sidecar` process (below):
    - `Sidecar.connect(socket)` or `Sidecar.start(binary, socket, threads)`;
    - `sidecar.aggregate(children ≤ 16, xmss, sphincs)`;
    - `sidecar.verify(proofBytes, xmssClaims, sphincsClaims)`, which throws `LeanPqException` unless the proof covers exactly those claims.
  - **Sidecar limits:**
    - Linux and macOS only.
    - The sidecar must be built from the same release as the bindings; a mismatched one is rejected.
    - The socket's directory must be private (mode 0700).
    - A sidecar serves at most 16 connections, so share one `Sidecar` per process. Its calls run one at a time.
  - **leanXMSS signing and single-signature verification still run in-process,** and they are all Teku uses today. The EthLib vectors and the spec KAT still pass through `infrastructure/xmss` on 0.13.0.
- **Packaging (as of v0.10.1):** on Maven Central as `io.github.crate-crypto:lean-pq`, versions 0.6.1 to 0.11.0, jar SHA-1 `3b098870…`. The jar is 7.5 MB and bundles natives for linux-x86_64 and linux-aarch64 (glibc ≥ 2.17; x86_64 needs AVX2 and PCLMULQDQ), macOS universal, and windows-x86_64. There is no musl or Windows arm64 build. The library is the named module `org.ethereum.leanpq`.
- **FFM, not JNI:** the JVM needs `--enable-native-access=ALL-UNNAMED`, because Teku runs on the class path. Add it to the start scripts, the Docker image and test JVM args, the same way `java-eth-kzg` is handled. On Linux and macOS the native library is copied into `java.io.tmpdir`, which must allow executable mappings. `-Dlean.pq.native.path` overrides it.
- **API:**
  - `XmssSecretKey.fromSeed(32 B)`, `generate()`, `toBytes()` (32-byte seed), `publicKey()` (32 B), `prepare(leaf)`, `sign(msg32, leaf)` (2404 B); it is `AutoCloseable`;
  - `LeanPq.xmssVerify(pk, msg, sig)` (static);
  - leanSPHINCS (stateless, 7740 B signatures), which the spec does not use;
  - up to 0.12.0: `LeanPq.aggregate(children ≤ 16, xmss SignedMessages, sphincs SignedMessages) → Aggregate`, with `Aggregate.fromBytes` (verifies on decode; from 0.11.0 it takes the expected claims), `toBytes()`, and `xmssClaims()`. From 0.13.0 these are `Sidecar.aggregate` and `Sidecar.verify` (above).
- **Spec compatibility: confirmed 2026-11-18.** v0.10.1 pins leanVM `5e3ade21`, which hashes with Keccak-256 and vendors EthLib's leanXMSS vectors from commit `03b7e4dd`. Those 37 vectors are byte-identical to EthLib v0.3.0, which the pyspec pins; v0.3.0 only adds 4 more verify cases. A local run against the spec's `utils/xmss_kat.json` matched: the derived public key matches, the signature at leaf 1 is **byte-for-byte identical**, and the KAT signature verifies.
- **Measured** (Apple Silicon, JDK 25, single run; the Java test is not committed):
  - verify: about 62 µs average over 100 (the first call costs about 740 ms to load the native library);
  - sign: about 200 ms cold, under 1 ms for the next leaf;
  - `LeanPq.setup()`: about 850 ms, once per process;
  - aggregating 2 signatures: 75 ms, giving a 196 KB proof that `fromBytes` verifies in 8 ms. Native memory was not measured.
- **`xmssVerify` does not check the leaf index.** The spec's `_verify` checks `signature_index(sig) == leaf_index` first. Teku must parse the 4-byte LE prefix itself, which it needs anyway for `GetLeafIndex`.
- **Aggregation gaps against the spec:**
  - Claims are `(pubkey, message)` only. The Rust facade says outright: "A claim names a public key and a message, never the leaf index used." The spec's `XMSSAggregateStatement` binds `leaf_index`, so a lean-pq aggregate cannot prove what `VerifyAggregates` checks.
  - Claims are strictly sorted and de-duplicated. In 0.10.x they were encoded inside the aggregate bytes (64 B per claim). From 0.11.0 the receiver supplies them, which suits the spec's committee-ordered statements.
  - At most 16 children per aggregation, so the block-level `MergeProofs` needs a tree.
  - One proof at a time per process. The README says one aggregation "takes seconds and roughly 1.6 GB of memory", all native, outside the Java heap.
  - Until the spec defines a real `AttestationProof`, use the binding for single signatures only and keep consensus on the concatenation stand-in.

**STARK aggregation (leanVM upstream):**
- leanVM (formerly leanMultisig) aggregates leanXMSS on CPU. On an M4 Max: 1,400 signatures produce a 209 KiB proof in 0.885 s, which verifies in 4.5 ms. Recursive 2→1 merging produces 270 KiB in 0.146 s.
- It is "not (yet) production ready". Bindings now exist through `lean-pq` (above).
- The spec says a real STARK "would change the encoding of `AttestationProof` but not how these functions are used". That holds only if the proof also binds the leaf index, or if the leaf index is folded into the signed message.

**`leanvm-sidecar`** (leanVM `bins/leanvm-sidecar`, added 2026-11-18 in leanVM #784; checked 2026-11-19):
- **What it is:** a separate Rust process that proves and verifies leanVM aggregates for clients on the same machine, over a Unix socket. It does **not** do key generation, signing or single-signature verification, so it complements lean-pq rather than replacing it.
- **Protocol v1:** small binary framing (u64 little-endian lengths).
  - The server sends a hello: magic `leanvmsc`, version 1, a 64 MiB request cap, verifier tag `leanvm-v1`, and whether it runs the `stable` or `experimental` prover.
  - Two requests: **Aggregate** (up to 16 child proofs, raw leanXMSS or leanSPHINCS signatures, up to 32 blobs, a proof rate of 1–4, and either all claims or an exact claim list) and **Verify** (claims plus proof).
  - Responses carry a status code (ok, invalid input, invalid signature, invalid child, proving failed, malformed).
- **Behaviour:**
  - It warms up before the socket appears, proves one request at a time in arrival order, and serves 16 connections.
  - Verify is answered without waiting for proofs in progress.
  - It doesn't verify children it is given, so a caller must verify untrusted children first.
- **Claims are still `(pubkey, message)`,** plus an optional DA commitment. The leaf-index gap (§5.17) is unchanged.
- **Clients:** since lean-pq 0.13.0, every binding has one, including Java's `Sidecar`. Teku no longer needs to implement the protocol itself.
- **Maturity:** two days old. Operators build it from source with `cargo install`, at the bindings' release tag with the `experimental_prover` feature. The README recommends building it on the target machine, because proving is performance-critical.
- **Versus lean-pq in-process aggregation** (written for 0.11.0; from 0.13.0 there is no in-process option):
  - The sidecar moves the heavy proving work out of the JVM: about 1.6 GB of native memory and seconds of CPU per proof, plus any native crash.
  - It can run the faster experimental prover.
  - It can be shared, for example by a beacon node and a validator client on one host.
  - The cost is another process for operators to run, version-match and secure (the socket must be private), plus a new failure mode when the socket is down.
- **Fit for Teku:**
  - Proving is only needed by aggregators and proposers, so a sidecar `StarkAggregator` backend is a good option for production proving.
  - **Superseded by 0.13.0:** the plan was to keep verifying aggregates in-process through lean-pq, because verification is consensus-critical on every node and shouldn't depend on an external process. lean-pq 0.13.0 removed in-process verification, so a real `StarkProofVerifier` needs a sidecar on every PQ node, not just aggregators and proposers. That raises questions to settle before replacing the concatenation stand-in:
    - **Operations:** every beacon node needs a sidecar built from the release matching Teku's lean-pq, on Linux or macOS only. Teku could launch it itself with `Sidecar.start` from a configured binary, or connect to one the operator runs.
    - **Throughput:** one shared `Sidecar` handles one call at a time, and verifies share it with proving. Each block and each gossiped aggregate needs a verify, so measure verify latency through the socket while a proof is in progress. The sidecar protocol says verify does not wait for proofs, but the Java client serializes its own calls, so verifies may need their own connection.
    - **Failure mode:** with the sidecar down, Teku can't validate blocks with attestations. That needs a clear startup check and a health metric, and should not be treated as an invalid proof (no peer penalties, no invalid-block marking).
    - **Alternatives:** ask upstream to keep an in-process verify (verification needs no prover and was about 8 ms), or verify through the Rust `lean_vm` crate behind Teku's own native wrapper.
  - Either way, this waits for the spec to define a real `AttestationProof`.

**Performance** (go-leanxmss, M2 Max):

| Operation | 1 CPU | 12 CPUs |
|---|---|---|
| Keygen (top tree) | 5.2 s | 0.58 s |
| Verify | about 70 µs | about 70 µs |
| Sign, bottom tree cached | 77 ms | 8.8 ms |
| Sign, bottom tree cold | 5.4 s | 0.68 s |

**[inferred]** A Java port on BouncyCastle Keccak is probably 2 to 4 times slower: about 150 to 300 µs per verify and 150 to 300 ms per cached sign.

### 2.3 What `xmss_active=False` means for reference tests

`test/context.py` sets `xmss.xmss_active = False` globally. `@always_bls` tests are **skipped** post-PQ ("XMSS signing is too slow"). Every PQ vector therefore has:

- **Stub pubkeys:** `StubPk(sk) = sha256("XMSS stub pubkey" ‖ seed)`. These are not real keys.
- **Stub signatures:** `leaf_index_le32 ‖ 0x11…` (2404 B). `GetLeafIndex` still parses the first 4 bytes, so the **bid leaf-range check and the self-build all-zero check still execute**.
- **Verification:** `Verify` always returns `True`. `VerifyAggregates` always returns `True`, and the proof is `b""` (`STUB_PROOF`).
- **VRF proofs are real Merkle proofs.** Test leaves are placeholders, but `is_valid_vrf_proof` is real sha256 Merkle code and **always runs**.

**Consequences for Teku:**
- Teku needs an `XmssConstants.verificationDisabled`-style switch, the analogue of `BLSConstants.verificationDisabled` in `infrastructure/bls/.../BLSConstants.java`, set for every PQ reference test.
- There will be **no negative-signature vectors**. Signature correctness must be covered by Teku unit tests against the ethlib/leanVM KATs (`utils/xmss_kat.json`, leanVM `tests/lean_xmss/ethlib`).
- PQ and EIP8483 are not in `TESTGEN_FORKS`. Vectors must be generated locally (patch `TESTGEN_FORKS`, run the generators for `pq`) and run with `-Dteku.ref-test.root-dir=...`.

### 2.4 Options for Teku

| Option | Pros | Cons |
|---|---|---|
| **A. Stub only** (no real crypto) | Unblocks phases 1 to 6 and the ref tests immediately | Useless on a devnet with signature checks; needed anyway as a test mode |
| **B. Pure-Java port** of leanXMSS (from leanVM `lean_xmss`, about 4 Rust files, or go-leanxmss) | No native build; Keccak already in Teku (`MessageDigestFactory.createKeccak256()`, BouncyCastle); easy to audit; verify is small | Signing perf (randomizer grinding, bottom-tree builds) is the risk; must track scheme churn; KAT discipline required |
| **C. `lean-pq` (FFM to leanVM Rust)**, published like `io.github.crate-crypto:java-eth-kzg` (used in `infrastructure/kzg/RustKZG.java`) | Exists today on Maven Central with all four platforms prebuilt; same leanVM code the lean clients use; real aggregation available for later | Upstream API churn (three breaking releases in Oct–Nov 2026); needs `--enable-native-access`; no musl; aggregates do not bind leaf indices |
| D. Wrap ethlib C | n/a | Far too slow (minutes per op), 176 MB runtime |

**Recommendation** (revised 2026-11-18):
1. Build A immediately, as part of the crypto module API.
2. Take C for keygen, sign and verify: add `lean-pq` to `gradle/versions.gradle` and wrap it in `infrastructure/xmss` as `LeanPqXmss`. The first PR runs `xmss_kat.json` and EthLib's vectors through it as unit tests. Pin an exact version and treat every upgrade as a KAT-gated change.
3. B (a pure-Java port) is now optional. Do it only if FFM proves a problem: distribution, musl users, or a need for a JVM-only verify path for throughput.
4. For STARK, implement the spec stand-in (concatenation) in Java behind a `StarkAggregator` interface. A `LeanPqStarkAggregator` waits for the spec to define a real `AttestationProof` and to resolve how leaf indices are bound (§5.17).

---

## 3. Mapping to Teku

Fork naming follows the existing `Eip8483` pattern: milestone `PQ`, class suffix `Pq` (`SpecConfigPq`, `SchemaDefinitionsPq`, `SpecLogicPq`, `PqStateUpgrade`, `BeaconStateSchemaPq`, ...).

### 3.1 Milestone and config

- **`SpecMilestone`:** add `PQ`. Decided (§5.1): `..., GLOAS, EIP8483, PQ, HEZE`, so Heze builds on PQ (#11654).
  - Inserting PQ before HEZE makes Heze inherit all `isGreaterThanOrEqualTo(PQ)` gating.
- **New config classes:** `SpecConfigPq` / `SpecConfigPqImpl` / `DelegatingSpecConfigPq` (extends the EIP8483 config), `config/builder/PqBuilder`, plus the wiring:
  - `SpecConfigBuilder`, `SpecConfigReader`, `SpecConfigLoader`;
  - the `SpecConfig.toVersionPq()` and `getPqForkEpoch()/getPqForkVersion()` accessors;
  - `DelegatingSpecConfig`.
- **New config fields:**
  - presets: `MAX_COMMITTEES_PER_SLOT_PQ`, `MAX_STARK_PROOF_SIZE`, `MAX_ATTESTATIONS_PQ`, the three request limits, the three `_SIZE_PQ` bounds, and `PREREGISTRATIONS_LIMIT` / `PREREGISTRATION_EXPIRY_SLOTS` (in the spec tables, not in `pq.yaml`, so they must be Java constants or added to the presets);
  - configs: `PQ_FORK_*`, `UNREGISTERED_VALIDATOR_WITHDRAWABILITY_DELAY`, `ATTESTATION_SUBNET_COUNT_PQ`.
- **Resources:**
  - `ethereum/spec/src/main/resources/tech/pegasys/teku/spec/config/presets/{mainnet,minimal,gnosis,swift}/pq.yaml`;
  - add keys to `configs/*.yaml`;
  - test resources under `src/test/resources/.../standard/presets/mainnet/pq.yaml`.
- **Other wiring:**
  - `SpecFactory.create` (highest-milestone chain), `SpecMilestone.getForkVersion/getForkEpoch`;
  - `TestSpecFactory.createMinimalPq()`, etc.;
  - `Eth2NetworkConfiguration` plus `Eth2NetworkOptions` (`--Xnetwork-pq-fork-epoch/-version`);
  - `eth-tests/TestFork`, `ReferenceTestFinder`.
- **Constants** (XMSS sizes, leaf offsets, domains, request types) go in a `PqConstants` class or `SpecConfigPq` getters. Follow how `WithdrawalPrefixes` and `ExecutionRequestType`-style constants are held **[inferred]**.

### 3.2 Crypto module: new `infrastructure/xmss`

Modelled on `infrastructure/bls` and `infrastructure/kzg`. `DepCheckPlugin` layering: infrastructure only.

- **Value types:**
  - `XmssPublicKey` (wraps `Bytes32`, with `fromLegacyBls(Bytes48)` helpers kept in spec, not here);
  - `XmssSignature` (2404 B, `getLeafIndex()` parses the LE uint32, `empty()` is all zeros);
  - `XmssSecretKey` (32-byte seed, zeroizable).
- **`Xmss` facade:** `sign(sk, leaf, msg32)`, `verify(pk, leaf, msg32, sig)` (also checks the embedded leaf), `getLeafIndex(sig)`.
- **`XmssConstants.verificationDisabled`** (test switch).
- **Pluggable backend:** `XmssImpl`, with `LeanPqXmss` (wrapping `io.github.crate-crypto:lean-pq`) as the real implementation, plus `StubXmss` for reference tests. An optional `JavaXmss` port is a fallback only. Selected like `KZG` chooses `CKZG4844` or `RustKZG`. `LeanPqXmss.verify` must check the signature's leaf prefix itself, because `LeanPq.xmssVerify` ignores it. Key handles are `AutoCloseable` native objects, so the VC key cache owns their lifecycle.
- **`XmssSignatureVerifier`** interface, the analogue of `tech.pegasys.teku.bls.BLSSignatureVerifier`, with a `NO_OP` and a simple impl.
- **STARK:**
  - `StarkAggregator`: `prove(List<XmssAggregateStatement>, List<List<XmssSignature>>)`, `merge(List<Bytes>)`, `verify(List<XmssAggregateStatement>, Bytes)`;
  - `XmssAggregateStatement(List<XmssPublicKey> pubkeys, long leafIndex, Bytes32 message)` record;
  - `ConcatenationStarkAggregator`, a byte-for-byte port of `utils/stark.py`;
  - stub mode returns an empty proof and accepts everything.
- **Signer-side helpers** (could sit in `validator/client` instead):
  - `XmssKeyCache`: top-tree and bottom-tree LRU, `prepare(leaf)` warm-up;
  - `VrfTree`: derivation, root, `proof(slot)`, persistent cache of level-k nodes.
- **Tests:** KATs from `xmss_kat.json` and the leanVM ethlib vectors; property tests for leaf parsing.

### 3.3 SSZ types and schemas

- **New SSZ wrappers** in `ethereum/spec/.../datastructures/type/`, mirroring `SszSignature`/`SszSignatureSchema` and `SszPublicKey`/`SszPublicKeySchema`:
  - `SszXmssSignature` + `SszXmssSignatureSchema` (fixed `ByteVector` of 2404);
  - `SszXmssPublicKey` + schema (a `Bytes32` view; or reuse `SszBytes32` with an accessor);
  - `SszVrfProof` (`SszBytes32Vector` of 32);
  - `AttestationProof` as an `SszByteList` with limit `MAX_STARK_PROOF_SIZE`.
- **Registry entries** in `schemas/registry/SchemaTypes` + `SchemaRegistryBuilder`:
  - `VALIDATOR_SCHEMA`, `BUILDER_SCHEMA`;
  - `ATTESTATION_PROOF_SCHEMA`, `VRF_PROOF_SCHEMA`;
  - the five preregistration and exit request/record list schemas;
  - `AGGREGATION_COMMITTEE(S)_SCHEMA`, `AGGREGATION_COMMITTEE_WINDOW_SCHEMA`;
  - PQ variants of `ATTESTATION_SCHEMA`, `INDEXED_ATTESTATION_SCHEMA`, `SINGLE_ATTESTATION_SCHEMA`, `AGGREGATE_AND_PROOF_SCHEMA`, `SIGNED_AGGREGATE_AND_PROOF_SCHEMA`, `SIGNED_BEACON_BLOCK_SCHEMA`, `SIGNED_BEACON_BLOCK_HEADER_SCHEMA`, `PROPOSER_SLASHING_SCHEMA`, `ATTESTER_SLASHING_SCHEMA`, `SIGNED_EXECUTION_PAYLOAD_BID_SCHEMA`, `SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA` (+ blinded), `PAYLOAD_ATTESTATION_MESSAGE_SCHEMA`, `SIGNED_PROPOSER_PREFERENCES_SCHEMA`, `EXECUTION_REQUESTS_SCHEMA`, and the six EL request schemas (raw `Bytes48`/`Bytes96` fields).
- **Static singletons that must become registry or per-fork schemas.** They are BLS-typed today:
  - `SignedBeaconBlockHeader.SSZ_SCHEMA` (used by `ProposerSlashing.SSZ_SCHEMA` and Fulu sidecars);
  - `ProposerSlashing.SSZ_SCHEMA`;
  - `Validator.SSZ_SCHEMA`;
  - `SignedAggregateAndProof` / `AggregateAndProof` schemas (`selection_proof` field).
- **Files with `SszSignatureSchema.INSTANCE` / `SszPublicKeySchema.INSTANCE` in `datastructures/`, about 60.** The PQ-relevant subset is listed above; Altair sync and Capella BLS-change schemas just stop being used.
- **New `SchemaDefinitionsPq`** (extends `SchemaDefinitionsEip8483`):
  - getters for every schema above;
  - `getBeaconStateSchema` → `BeaconStateSchemaPq`;
  - `getBeaconBlockBodySchema` → `BeaconBlockBodySchemaPqImpl`;
  - sync-committee/LC/BLS-change/voluntary-exit getters must throw or return `Optional.empty()`.
- **New containers** in `datastructures/execution/versions/pq/`, `datastructures/state/versions/pq/`, `datastructures/operations/versions/pq/`:
  - `ValidatorPreregistrationRequest(+Schema)`, `BuilderPreregistrationRequest(+Schema)`, `VoluntaryExitRequest(+Schema)`;
  - `ValidatorPreregistration(+Schema)`, `BuilderPreregistration(+Schema)`;
  - `ExecutionRequestsPq`, `ExecutionRequestsSchemaPq`, `ExecutionRequestsBuilderPq`, `ExecutionRequestsFieldsPq` (gaps at 5, 6). Update `ExecutionRequestsDataCodec` for 0x07 to 0x09 and to drop 0x05/0x06.
  - `AttestationPqSchema` (no signature, gap 2), `IndexedAttestationPqSchema` (proof);
  - `AggregateAndProofPq`, `SignedAggregateAndProofPq`;
  - PQ `SingleAttestation`, `PayloadAttestationMessage`, `SignedExecutionPayloadBid`, `SignedExecutionPayloadEnvelope`, `SignedProposerPreferences`, `SignedBeaconBlock`, `SignedBeaconBlockHeader` schemas.
- **Interface pressure.** These Java interfaces expose BLS types and need a PQ story:
  - `Attestation.getAggregateSignature()` (7 main call sites; return `Optional` or throw for PQ);
  - `AggregateAndProof.getSelectionProof()` (6 main);
  - `BeaconBlockBody.getRandaoReveal(): BLSSignature` (14 main call sites of `getRandaoReveal()`; add `getOptionalVrfProof()` and make the BLS getter fail for PQ);
  - `BeaconBlockBody.getVoluntaryExits()` (non-optional today, 17 main call sites; PQ returns an empty list or the method becomes optional);
  - `SignedBeaconBlock.getSignature(): BLSSignature`, `SignedBeaconBlockHeader.getSignature()`.
  - Proposed pattern: keep the BLS getters, add `getXmssSignature()`/`getSignatureBytes()` siblings, and gate them by schema. A fully generic `Signature` type would touch about 180 files.

### 3.4 `BeaconState` and `Validator`

- **`Validator` today** is `Container8<..., SszPublicKey, ...>` with a static `SSZ_SCHEMA`, used by every state version.
- **PQ needs `ValidatorPq`:** `Container9` with `SszBytes32 pubkey` and `SszBytes32 vrf_root`.
- **Options:**
  - **(a)** Make `Validator` an interface. `ValidatorPhase0` is the existing container; `ValidatorPq` is new. Common getters: `getPubkeyBytes(): Bytes` (48 or 32 bytes), `getWithdrawalCredentials`, epochs, `getEffectiveBalance`, and the `with*` copy methods. `getPublicKey(): BLSPublicKey` throws for PQ; `getXmssPublicKey()` and `getVrfRoot()` are added.
  - **(b)** Keep `Validator` as a class and add PQ fields, which is not possible with SSZ containers.
  - **(a) is the realistic path.** `getPublicKey()` has about 84 main call sites to audit.
- **Pending work to coordinate:** PR #11627 (tbenr, byte-backed packed `Validator` views, approved 2026-11-17) changes the `Validator` storage layout. PQ needs a packed variant for the 9-field layout. Coordinate before starting.
- **New `BeaconStateSchemaPq`.** It **cannot** simply extend `BeaconStateSchemaEip8483`, because it removes Altair fields 22 and 23 (sync committees) and EIP8483 fields 46 and 47. Build the field list explicitly:
  - drop `CURRENT_SYNC_COMMITTEE`, `NEXT_SYNC_COMMITTEE`, `VALIDATOR_PQ_REGISTRATIONS`, `BUILDER_PQ_REGISTRATIONS`;
  - override `VALIDATORS` with a `ValidatorPq` list and `BUILDERS` with a PQ `Builder` list;
  - add `VALIDATOR_PREREGISTRATIONS`, `BUILDER_PREREGISTRATIONS`, `AGGREGATION_COMMITTEE_WINDOW` (new `BeaconStateFields` entries);
  - set `ACTIVE_FIELDS` width 51 with gaps {22, 23, 46, 47}. Gapped progressive containers are supported (`SszProgressiveContainerSchemaTest` `GAPPED_SCHEMA`).
- **New state classes:** `BeaconStatePq`, `BeaconStatePqImpl`, `MutableBeaconStatePq`, `MutableBeaconStatePqImpl`.
- **Interface problem:** `BeaconStateAltair` exposes `getCurrentSyncCommittee()`/`getNextSyncCommittee()` (15/14 main call sites) and is the carrier for participation flags and inactivity scores (31 files use `toVersionAltair()`). PQ must keep participation and inactivity but not sync committees.
  - Option 1: `BeaconStatePq` implements `BeaconStateGloas` and the sync-committee getters throw `UnsupportedOperationException`. This is the cheap path.
  - Option 2: split a `BeaconStateAltairParticipation` super-interface.
  - Recommend option 1 plus an audit of all callers that reach sync committees through `toVersionAltair()`. They must first check `spec.getSyncCommitteeUtil(slot).isPresent()`.
- **`Builder` (gloas)** gets a PQ variant with an XMSS pubkey. `BuilderIndexCache` must be keyed by `Bytes32` for PQ.

### 3.5 Block body

- `BeaconBlockBodySchemaPqImpl` / `BeaconBlockBodyPqImpl` / `BeaconBlockBodyPq` / `BeaconBlockBodyBuilderPq`, with width 14 and gaps {7, 8, 9, 11}.
  - `randao_reveal` is a VRF proof; `proof` is added; four fields are removed.
  - The builder must not require exits, sync aggregate, BLS changes or payload attestations.
- **`BlindedBeaconBlockBody`:** Gloas has no blinded body (ePBS), so none is needed **[inferred, verify]**.
- **Operation limits are no longer type-enforced.** `Attestations`/`AttesterSlashings` are progressive lists in Gloas, so `MAX_ATTESTATIONS_PQ` etc. must be asserted in processing and at gossip (`verify_block_body_operation_limits`).

### 3.6 State transition: `SpecLogicPq` and helpers

`SpecLogicPq` (extends `AbstractSpecLogic`, cloned from `SpecLogicEip8483`):
- returns `Optional.empty()` for `getSyncCommitteeUtil()` and `getLightClientUtil()`, so existing Optional-based callers degrade naturally;
- wires the PQ helpers listed below.

| Spec item | Teku class (new or modified) |
|---|---|
| leaf helpers, legacy pubkey/VRF root, deposit pubkey | `MiscHelpersPq` (extends `MiscHelpersGloas`) |
| `get_committee_count_per_slot` (cap 16) | `BeaconStateAccessorsPq.getCommitteeCountPerSlot`. **Check all shuffling and committee caches** (`TransitionCaches` committee cache, `getBeaconCommitteesSize`) use the per-fork accessor. |
| `get_aggregation_committee`, `compute_aggregation_committee(s)`, `get_attestation_statements` | `BeaconStateAccessorsPq`, `MiscHelpersPq` (reuse `MiscHelpersGloas.computeBalanceWeightedSelection`) |
| preregistration getters | `BeaconStateAccessorsPq`. **Use hash-set lookups** like `ExecutionRequestsProcessorEip8483` does, not the spec's linear scans. |
| `queue_excess_active_balance`, `remove_*_preregistration`, `add_validator_to_registry(pk, vrf_root, ...)`, `add_builder_to_registry` | `BeaconStateMutatorsPq` |
| `is_valid_vrf_proof`, `is_legacy_pubkey`, `is_builder_bid_leaf_index`, `is_pending_validator` | `PredicatesPq` (Merkle branch check exists in `Predicates.isValidMerkleBranch`) |
| `is_valid_indexed_attestation` via STARK | `AttestationUtilPq` (`isValidIndexedAttestation[Async]` uses `StarkAggregator` rather than `AsyncBLSSignatureVerifier`) |
| `verify_block_signature`, `process_randao`, `process_operations` + `process_stark`, `process_attestation` (no sig), proposer slashing verify, no sync aggregate | `BlockProcessorPq` extends `BlockProcessorGloas`. Overrides `verifyBlockSignatures`/`verifyBlockSignature`/`verifyRandao`/`verifyProposerSlashings`/`verifyAttestationSignatures` (in `AbstractBlockProcessor`) and `processRandaoNoValidation`, `processOperationsNoValidation`, `processBlock` (skip `processSyncAggregate`), `processPayloadAttestations` (none). |
| `verify_execution_payload_bid_signature`, `process_execution_payload_bid` (zero sig for self-build) | `BlockProcessorPq.processExecutionPayloadBid`, `OperationSignatureVerifierPq` |
| `verify_execution_payload_envelope_signature` | `ExecutionPayloadVerifierPq` (or a PQ branch in `ExecutionPayloadVerifierGloas`) |
| parent requests: new limits, new processors, legacy-pubkey rules | `ExecutionRequestsProcessorPq` (deposit/withdrawal/consolidation/builder deposit/builder exit modified; new validator/builder preregistration and voluntary-exit request processors); `BlockProcessorPq.applyParentExecutionPayload` |
| `apply_pending_deposit`, `process_pending_deposits` (legacy bypass, no signature) | `EpochProcessorPq` / `BeaconStateMutatorsPq` |
| `process_aggregation_committee_window`, `process_preregistration_expiry`, no sync-committee update | `EpochProcessorPq` extends `EpochProcessorGloas` (pattern: `processPtcWindow`) |
| `get_validator_from_deposit(pk, vrf_root, ...)` | `ValidatorsUtilPq` / `MiscHelpersPq` |
| operation validation (no exits, no BLS changes) | `OperationValidatorPq` (or PQ-aware `OperationValidatorCapella`); `VoluntaryExitValidator*` unused |
| block production | `BlockProposalUtilPq`, `BeaconBlockBodyBuilderPq`, `ExecutionPayloadProposalUtil` (self-build envelope at leaf 4) |
| fork choice util | `ForkChoiceUtilPq` extends `ForkChoiceUtilGloas` (see 3.10) |
| weak subjectivity | `WeakSubjectivityCalculatorGloas` reused **[inferred]** |

**Signature verification threading.** `BLSSignatureVerifier` is threaded through `processAndValidateBlock`/`processBlock`.
- Add a parallel `XmssSignatureVerifier` parameter, or a combined `SignatureVerifiers` holder, so that block import can run a no-op XMSS verifier when signatures are pre-verified.
- `AbstractBlockProcessor.processAndValidateBlock` and `BlockImporter`/`ForkChoice` callers must pass it.
- Ref-test executors pass `NO_OP` when `bls_setting` is ignored; for PQ, XMSS is always disabled.

### 3.7 Fork upgrade: `PqStateUpgrade`

- Lives in `logic/versions/pq/forktransition/`. Pattern: `Eip8483StateUpgrade`/`HezeStateUpgrade`, `BeaconStateFields.copyCommonFieldsFromSource`.
- Implements `compute_validators`, `compute_builders`, `compute_pending_deposits`, `compute_builder_pending_payments`, `compute_builder_pending_withdrawals` (uses `get_builder_payment_quorum_threshold` from Gloas accessors) and `initialize_aggregation_committee_window`.
- **Performance [inferred]:**
  - `get_validator_pq_registrations` is O(V+R) with a hash set of legacy pubkeys (about 1M sha256 hashes at mainnet scale, about 1 s);
  - `compute_validators` rebuilds the whole validator list. Build it with the list schema's `createFromElements`, not by repeated `append`.
- **Cache invalidation (critical).** Every pubkey changes at the fork:
  - `BeaconStateCache.getTransitionCaches(state).getValidatorIndexCache()`: `ValidatorIndexCache` is keyed by `BLSPublicKey`;
  - any index→pubkey cache (`TransitionCaches` validator pubkey cache, `getValidatorPubKey` users, 23 main files);
  - `BuilderIndexCache`.
  - All of these must be **reset, not copied**, by `createEmptyWithTransitionCachesFrom` on upgrade. PQ needs a `Bytes32`-keyed index cache: generalise `ValidatorIndexCache` to `Bytes` keys, or add `ValidatorXmssIndexCache`.
- **Fork-boundary parent requests.** Extend the EIP8483 "convert to pre-fork type and compare against the bid root" bridge so that `BlockProcessorPq` accepts parent requests from an EIP8483-typed payload. `ExecutionRequestsSchema.convertFrom` handles the EIP8483 → PQ field mapping, which drops the registration lists.

### 3.8 Signature verification services (beacon node)

- **Today** `statetransition/validation/signatures/SignatureVerificationService` and `AggregatingSignatureVerificationService` batch BLS checks onto a worker pool. `AsyncBLSSignatureVerifier`/`AsyncBatchBLSSignatureVerifier` live in `spec/logic/common/util`.
- **Add `XmssVerificationService`** (async pool; no batching benefit, since each verify is independent at about 70 to 300 µs) and an `AsyncXmssSignatureVerifier`.
- **Add `StarkVerificationService`.** With the stand-in this is N XMSS verifies; with real STARK it is about 5 ms per proof. It runs on its own bounded queue so aggregate floods cannot starve block import.
- **Gossip validators to switch by milestone:**
  - `BlockGossipValidator`, `AttestationValidator`, `AggregateAttestationValidator`, `ProposerSlashingValidator`, `AttesterSlashingValidator`;
  - `PayloadAttestationMessageGossipValidator`, `ExecutionPayloadBidGossipValidator`, `ExecutionPayloadGossipValidator` (envelope), `ProposerPreferencesGossipValidator`;
  - `GossipValidationHelper` (proposer signature helper).
- **Per-validator gossip load [inferred]:** at 1M validators there are about 31k single attestations per slot spread over 16 subnets. A node on 2 subnets verifies about 3.9k XMSS signatures per slot, about 0.3 to 1.2 core-seconds in Java. Acceptable, but measure.

### 3.9 Fork choice

- `ForkChoice` (`statetransition/forkchoice/ForkChoice.java`):
  - for PQ, `onAttestation` must not run the indexed-attestation signature check. Wire attestations as already verified (gossip did it) or block-proven;
  - `onPayloadAttestationMessage` always verifies XMSS and requires `slot == current`;
  - drop the block → PTC vote application (`notify_ptc_messages`) for PQ blocks.
- `ForkChoiceUtilPq`: `applyBlockToStore` without PTC notification.
- `AttestationManager`/`DeferredAttestations`: `ValidatableAttestation` must carry an "XMSS-verified" or "proof-verified" flag rather than relying on the aggregate BLS signature.

### 3.10 Attestation pool and block production

The pool aggregates BLS signatures today (`AggregatingAttestationPoolV2`, `MatchingDataAttestationGroupV2`, `PooledAttestation`, `AggregateAttestationBuilder`). PQ semantics differ:
- **Aggregators need individual `SingleAttestation` XMSS signatures** per (data, committee), in committee order, to call `ProveAggregates`. This adds about 2.4 KB per attester per slot retained until aggregation time.
- **Proposers can only use proofs they hold.** With the stand-in (and probably with recursive STARKs), overlapping aggregates of the same committee cannot be unioned without the raw signatures. The proposer picks, per (data, committee), the single best network aggregate (highest balance or bits), groups committees with equal data into `compute_on_chain_aggregate`, and calls `MergeProofs` in body order.
- **[`a4d16c5be`] Inclusion delay 2.** The block packer may only take attestations from `slot - 2` or earlier, and should prefer exactly `slot - 2` for the head reward. Teku's pool and packer read `MIN_ATTESTATION_INCLUSION_DELAY` from the config today, so make that milestone-aware. Aggregators keep their deadline, so fork choice sees votes as early as before.
- **Hard size budget.** `MAX_STARK_PROOF_SIZE // 2404 = 436` signatures for the **whole block** in stand-in mode, so the packer must budget proof bytes, not just `MAX_ATTESTATIONS_PQ = 16`.
- **New classes:**
  - `AggregatingAttestationPoolPq` implementing the `AggregatingAttestationPool` API;
  - `PqAttestationGroup` (per data root: per-committee best aggregate with proof, and per-committee XMSS signature map from singles);
  - `AttestationProofBuilder` (`get_aggregate_stark`);
  - `AttestationPackerPq` (or a PQ path in the `getAttestationsForBlock` equivalent) that also returns the merged `proof` for `BeaconBlockBodyBuilderPq`.
- **`RewardBasedAttestationSorter`** can be reused for ranking **[inferred]**.
- **Payload attestations:** `AggregatingPayloadAttestationPool`/`PayloadAttestationPool` are still needed (fork choice and gossip), but must not feed block production in PQ.
- **Operation pools to disable for PQ:**
  - `SignedVoluntaryExit` pool, `SignedBlsToExecutionChange` pool;
  - `SyncCommitteeMessagePool`, `SyncCommitteeContributionPool`;
  - `OperationsReOrgManager` handling of exits and BLS changes.

### 3.11 Networking

- **`GossipForkSubscriptionsPq`** (extends `GossipForkSubscriptionsGloas` / the BPO variant). Override `addGossipManagers` to skip:
  - `addVoluntaryExitGossipManager` (Phase0);
  - `addSignedBlsToExecutionChangeGossipManager` (Capella);
  - `addSignedContributionAndProofGossipManager`, `addSyncCommitteeMessageGossipManager`, `addLightClientGossipManagers` (Altair).
  - Wire it in `Eth2P2PNetworkBuilder`, which currently maps `case GLOAS, EIP8483, PQ, HEZE` to `GossipForkSubscriptionsGloas[Bpo]`.
- **Attestation subnets:**
  - make `NetworkingSpecConfig.getAttestationSubnetCount()` milestone-aware, or add a PQ accessor;
  - update `MiscHelpers`/`BeaconStateUtil.computeSubnetForAttestation`;
  - update `AttestationSubnetSubscriptions`, `NodeBasedStableSubnetSubscriber` (`compute_subscribed_subnet` with prefix bits from 16), `AllSubnetsSubscriber`, `GossipTopics`, `GossipScoringConfigurator`/`ScoringConfig` (topic weights assume 64 subnets).
  - `Attnets` stays 64 bits wide. Only set bits below 16, and do not penalise peers for unset bits at 16 and above.
- **MetaData / ENR:** `MetadataMessageSchemaFulu` and the Altair schema keep `syncnets`; publish zero. Discovery `syncnets` stays zero.
- **Seen caches:**
  - `SeenAggregatesCache` plus a new best-balance map keyed by (data root, committee index), with the time-dependent rule (§1.8);
  - aggregator-per-epoch seen set unchanged;
  - payload-attestation seen set unchanged.
- **Size bounds:** apply `MAX_SIGNED_AGGREGATE_AND_PROOF_SIZE_PQ` / `MAX_ATTESTER_SLASHING_SIZE_PQ` / `MAX_SIGNED_EXECUTION_PAYLOAD_BID_SIZE_PQ` in the gossip decoders. Check the Gloas type-specific bounds work; the memory notes say it was deferred.
- **Req/resp:** disable the light-client RPC methods (from #11542) for the PQ fork digest. Block and envelope RPC is unchanged.
- **Gossip scoring and rate limits:** sizing assumes 32-byte or 96-byte sigs; XMSS sigs are 2.4 KB and aggregates up to 1 MiB. Revisit the mesh-message rate limits and queue sizes (`Eth2GossipTopicFilter`, gossip decoders).

### 3.12 Removed features: switch off per milestone

- **Sync committees** (190 main files mention `SyncCommittee`). Gate on `spec.getSyncCommitteeUtil(slot).isPresent()`:
  - `SyncCommitteeScheduler`, `SyncCommitteeDutyLoader`, `duties/synccommittee/*`;
  - `SyncCommitteeSubscriptionManager`/subnet subscriber;
  - REST sync endpoints;
  - `EpochProcessor` updates;
  - rewards calculators (`SyncCommitteeRewardData`).
- **Light client** (108 main files): `LightClientUtil` absent; REST LC endpoints return 404/400 for PQ; gossip and RPC disabled.
- **Voluntary exits** as an operation: REST `POST /eth/v1/beacon/pool/voluntary_exits` must reject post-PQ. The VC `voluntary-exit` CLI must instead produce an XMSS-signed exit for the system contract. Its format is undefined by the CL spec (§5).
- **BLS-to-execution changes:** REST pool, gossip, and the CLI are disabled for PQ.
- **Payload attestations in blocks:** body field gone; block production path removed. The pool stays.

### 3.13 Execution layer

- `ExecutionRequestsDataCodec` / `ExecutionRequestsSchemaPq`: request types 0x07 to 0x09; 0x05/0x06 rejected.
- `MilestoneBasedEngineJsonRpcMethodsResolver`: add PQ, reusing the Gloas/EIP8483 engine methods **[inferred]**. The spec says nothing about a new Engine API version.
- **No EL implements these system contracts yet** (none implements 0x05/0x06 either), so request lists stay empty on devnets.

### 3.14 REST API (`data/beaconrestapi`, `data/provider`, `ethereum/json-types` **[inferred]**)

- **Validator JSON type definitions** must emit a 32-byte XMSS pubkey plus `vrf_root` for PQ states:
  - `/eth/v1/beacon/states/{id}/validators`, `validator_balances`, `.../validators/{id}` (lookup by a 32-byte pubkey);
  - committees, duties.
- **Signature fields:** blocks, attestations, aggregates, PTC, bids, envelopes, preferences and slashings need XMSS-typed (2404 B hex) definitions.
  - Attestation JSON has **no** signature.
  - Aggregate JSON carries `proof` (hex up to 1 MiB).
- **Validator API.** The beacon-APIs need changes, coordinated upstream:
  - `GET aggregate_attestation` must also return the proof, or a new endpoint is needed, because the BN holds the single-attestation signatures. **Recommendation:** the BN builds the proof and the VC signs `AggregateAndProof` (which includes the proof bytes in its signing root).
  - Attester duties should expose `is_aggregator`, or a new `aggregation_committee` endpoint.
  - `beacon_committee_selections` (DVT) and the sync-committee duties and selections endpoints are obsolete.
  - `randao_reveal` in `GET /eth/v3/validator/blocks/{slot}` becomes a VRF proof (32 × 32 B).
- **OpenAPI.** Regenerate the integration-test JSON under `data/beaconrestapi/src/integration-test/resources/...`. The EIP-8483 work already had to rename envelope schemas to avoid duplicate OpenAPI component names; expect the same for every PQ-typed container.

### 3.15 Validator client: the biggest Teku-specific redesign

1. **Identity.**
   - **Today:** the VC keys everything by `BLSPublicKey`: `OwnedValidators`, `validator.client.Validator`, `ValidatorIndexProvider`, keymanager API, doppelganger, `ValidatorApiChannel.getValidatorIndices(Collection<BLSPublicKey>)`, slashing-protection file names.
   - **After PQ:**
     - the BN returns XMSS pubkeys in duties and state queries;
     - validators that join after PQ have **no** BLS key.
   - **Proposal:**
     - introduce a `ValidatorIdentity` (index, optional BLS pubkey, optional XMSS pubkey);
     - match duties by **validator index** (indices survive the fork);
     - add `getValidatorIndicesByXmssPubkey`.
2. **Keys.**
   - **Secrets per validator:** an XMSS seed (32 B), plus a VRF secret (seed for leaf derivation) and a VRF tree cache.
   - **Keystore:** reuse the `infrastructure/bls-keystore` `KeyStore` encryption, which decrypts to `Bytes`. A new keystore "kind" carries a 32-byte pubkey; EIP-2335 assumes BLS, so this needs a standard (§5).
   - **Loading:**
     - extend `LocalValidatorSource`/`KeyManager` to load XMSS key, VRF material and leaf DB alongside, or instead of, the BLS key;
     - provide a CLI (`teku validator generate-pq-keys`, **[inferred]** name) that emits the XMSS pubkey, the VRF root and the EIP8483 `pq_data`.
   - This tooling is a **prerequisite for EIP8483 registration**, so it may belong to the EIP8483 workstream.
3. **VRF.** RANDAO is not a signature, so `Signer.createRandaoReveal` gets a PQ sibling `getVrfProof(slot)`.
   - **[inferred]** Teku must pick a derivation, for example `leaf_i = sha256(vrf_seed ‖ uint64(i))`.
   - **[inferred]** The root needs about 2^32 sha256 calls, about 5 to 10 min per validator in Java. This is a one-off at key generation.
   - **[inferred]** To produce a proof cheaply, persist the level-16 nodes (2^15 × 32 B = 1 MiB per validator). Each proof then costs about 2^16 hashes.
   - The spec leaves this to clients, which hurts portability between clients (§5).
4. **Signer.**
   - Add `XmssSigner`, or make `Signer` methods return a sealed `ValidatorSignature` (`Bls`/`Xmss`). `Signer` currently returns `SafeFuture<BLSSignature>` for 15 methods.
   - **Recommend** a separate `XmssSigner` interface held by the VC `Validator` next to the BLS `Signer`. Duties select by `spec.atSlot(slot).getMilestone()`.
   - Methods:
     - `signBlock` (leaf offset 0), `signAttestationData` (attestation leaf), `signAggregateAndProof` (offset 1);
     - `signPayloadAttestationData` (2), `signProposerPreferences` (3), `signExecutionPayloadEnvelope` (4);
     - `signReserved(message)` for exits and preregistration (allocates the next reserved leaf);
     - `getVrfProof`.
   - **Implementations:** `LocalXmssSigner`, `SlashingProtectedXmssSigner`, `DeletableXmssSigner` (mirror `LocalSigner`, `SlashingProtectedSigner`, `DeletableSigner` in `spec/signatures`).
5. **Leaf protection: a new safety-critical store.**
   - **Today:** `SlashingProtector`/`LocalSlashingProtector` only guard blocks and attestations (`maySignBlock`, `maySignAttestation`). Aggregates, PTC messages, preferences, envelopes and randao are unguarded.
   - **PQ requires** a per-key `XmssLeafProtector`:
     - `maySign(pubkey, leaf, signingRoot)` returns true if the leaf is unused or recorded with the same root;
     - record durably (fsync) **before** signing;
     - an allocator for reserved leaves.
   - **Storage [inferred]:** per-key file under the slashing-protection dir.
     - Per-slot and attestation leaves are monotonic, so store a high-water mark per region plus an exact map for recent leaves.
     - Reserved leaves get a counter.
   - **Interchange:** EIP-3076 has no leaf records, so an extension or a new format is needed.
   - **Higher stakes:** leaf reuse **exposes the key**. It is worse than slashing, because anyone can then forge at that leaf.
   - **Existing behaviour to fix:** `ProposerPreferencesPublisher` may re-sign preferences for the same slot when the dependent root changes. The spec forbids that in PQ, so make it sign once per slot.
6. **Duties.**
   - `AttestationProductionDuty`: XMSS signature on `SingleAttestation`.
   - `AggregationDuty` / `AggregationDutyAggregators`:
     - no selection proof (`signAggregationSlot`, 6 main files);
     - aggregator status from the aggregation committee (state-derived, via the BN);
     - obtain aggregate plus proof from the BN;
     - sign at offset 1.
   - `BlockProductionDuty`: VRF proof as the randao reveal; XMSS block signature.
   - PTC duty (`duties/payloadattestations`): offset 2.
   - `ProposerPreferencesPublisher`: offset 3, once per slot.
   - Execution payload envelope (self-build, `duties/execution`): offset 4.
   - `SyncCommitteeScheduler`/loader: disabled post-PQ.
   - `BeaconCommitteeSubscriptions`: the `is_aggregator` flag from the BN.
   - `DvtAttestationAggregations`: obsolete.
   - Committee indices change (16 per slot), but that flows from the BN duties.
7. **Fork boundary.**
   - Pre-PQ slots sign with BLS; PQ slots with XMSS.
   - The attestation for the last pre-fork epoch target is still BLS.
   - The first PQ epoch's aggregates and selection have no selection proof.
   - The VC must warn loudly before the fork about owned validators **without** an EIP8483 registration (they will be exited). It can read `validator_pq_registrations` via a new REST endpoint or state query **[inferred]**.
8. **External signer** (`ExternalSigner`, Web3Signer): no XMSS support exists. For the first iteration, refuse PQ duties for external validators and report clearly. Web3Signer would need XMSS plus leaf tracking (§5).
9. **Builder-API validator registrations** (`signValidatorRegistration`, BLS with `DOMAIN_APPLICATION_BUILDER`) and `signBuilderRequestAuth` are outside the CL spec. Decide whether they stay BLS (§5).
10. **Performance [inferred].**
    - A VC with 1,000 keys signs about 31 attestations per slot. At about 0.2 s per cached Java sign, that is about 6 core-seconds per slot, so it must be parallel. Prefer JNI if this does not fit.
    - Bottom trees are about 2 MiB each (2^17 × 16 B); keep two per key hot (the attestation region and the current per-slot tree).
      - At 1,000 keys that is about 4 GiB, too much. Use a smarter auth-path strategy or compute on demand, with `prepare(leaf)` ahead of time.
    - The per-slot bottom tree rolls every 8,192 slots (about 27 h). Precompute the next one in the background.

### 3.16 Storage

- States and blocks are SSZ by milestone, so there is nothing structural to change.
- **[inferred]** State size grows by about 16 B per validator (pubkey −16 B, vrf_root +32 B), plus the aggregation window.
- Check `state-diff` (`infrastructure/state-diff`) handles the new `ValidatorPq` layout and gapped fields.
- Hot caches keyed by BLS pubkey in storage or dataproviders (the `validatorcache` package in statetransition, `ActiveValidatorTracker`) need review **[inferred]**.

### 3.17 Reference tests

- **Wiring in `eth-reference-tests` / `eth-tests`:** `TestFork.PQ`, `ReferenceTestFinder`.
- **Operations executor:**
  - new handlers `validator_preregistration_request`, `builder_preregistration_request`, `voluntary_exit_request`;
  - modified `deposit_request`, `withdrawal_request`, `consolidation_request`, `builder_deposit_request`, `builder_exit_request`, `parent_execution_payload`, `randao`;
  - no `voluntary_exit`, `bls_to_execution_change`, `sync_aggregate` or `payload_attestation` for PQ.
- **Epoch processing:** `aggregation_committee_window`, `preregistration_expiry`, `pending_deposits`.
- **Fork:** `fork` (`ForkUpgradeTestExecutor`, eip8483 → pq).
- **Networking:** `gossip_beacon_aggregate_and_proof`. `phase0/gossip/*TestExecutor` exists, e.g. `GossipProposerPreferencesTestExecutor`.
- **`ssz_static`:** all new or changed containers.
- **Inherited suites** (sanity, finality, random, fork_choice, rewards, shuffling, transition) via `with_all_phases_from`.
- **Always set `XmssConstants.verificationDisabled` for PQ.** The BLS setting remains relevant only for pre-PQ parts of transition tests.
- **Generate vectors locally:**
  1. patch `TESTGEN_FORKS` to add `EIP8483, PQ`;
  2. `make test reftests=true fork=pq` (or the generator for the `pq` fork);
  3. run with `-Dteku.ref-test.root-dir`.
- Generating them means running the spec's test generators locally, outside Teku's build.
- **Specrefs:** PQ is a `_features` fork. Specrefs tracking is optional until the fork leaves `_features` **[inferred]**.

---

## 4. Phased work breakdown

Sizes: S ≈ <1 eng-week, M ≈ 1–2, L ≈ 2–4, XL ≈ 4+ (rough, for one engineer familiar with Teku fork work).

| # | Phase | Size | Depends on | Parallel with | Exit criteria |
|---|---|---|---|---|---|
| 0 | **Decisions and spikes:** milestone placement (§5.1); crypto backend spike (Java verify plus Java sign perf vs JNI to leanVM); `Validator` interface strategy (§3.4) with PR #11627 owner; VC identity model (§3.15.1) | S | EIP8483 branch stable | — | written decisions; perf numbers |
| 1 | **Milestone and config skeleton:** `SpecMilestone.PQ`, `SpecConfigPq*`, `PqBuilder`, presets and configs, `SpecFactory`, `TestSpecFactory`, CLI `--Xnetwork-pq-*`, `TestFork`; `SchemaDefinitionsPq` and `SpecLogicPq` initially delegating to EIP8483 | S | 0 | 2 | `./gradlew :ethereum:spec:test` green with PQ scheduled (behaves as EIP8483) |
| 2 | **Crypto API with stub:** `infrastructure/xmss` (types, `Xmss` facade with a disabled mode, real `getLeafIndex`, `ConcatenationStarkAggregator`, `XmssSignatureVerifier`), VRF Merkle verify | S–M | 0 | 1, 3 | unit tests; stand-in STARK byte-equal to `stark.py` |
| 3 | **Containers and schemas:** SSZ wrappers, `ValidatorPq` + `Validator` interface refactor, PQ `Builder`, `BeaconStateSchemaPq` (gaps), body (gaps), attestation family, signed wrappers, EL requests and `ExecutionRequestsSchemaPq`, registry entries, the static-schema to registry migrations (`SignedBeaconBlockHeader`, `ProposerSlashing`, `AggregateAndProof`) | L–XL | 1, 2 | 10 | `ssz_static` PQ vectors pass |
| 4 | **State transition (stubbed XMSS):** `MiscHelpersPq`, `PredicatesPq`, `BeaconStateAccessorsPq`, `BeaconStateMutatorsPq`, `BlockProcessorPq` (VRF randao, `process_stark`, no sync aggregate), `ExecutionRequestsProcessorPq`, `EpochProcessorPq`, `AttestationUtilPq`, `OperationSignatureVerifierPq`, committee-count cap through all caches, signature-verifier threading | L | 3 | 10, 9a | operations, epoch_processing and sanity PQ vectors pass |
| 5 | **Fork upgrade:** `PqStateUpgrade` (validators, builders, deposits, payments, window), cache resets (`ValidatorIndexCache` and the pubkey caches made `Bytes`-keyed), fork-boundary parent-requests bridge, Heze-overshadow handling | M | 4 | 10 | `fork` and `transition` vectors pass; upgrade unit tests (registered, unregistered, slashed, `0x00` creds, pending deposits, builders) |
| 6 | **Beacon-node pipelines:** `ForkChoice`/`ForkChoiceUtilPq`; `XmssVerificationService` and `StarkVerificationService`; all gossip validators; `AggregatingAttestationPoolPq` + `AttestationProofBuilder` + `AttestationPackerPq`; block production (proof merge, self-build zero sig, no payload attestations, proof-size budget); disabling the exit, BLS-change and sync pools; inclusion delay 2 in the packer; superset-rule seen cache (as Gloas) | XL | 4, 5 | 7, 8, 9 | fork_choice and `gossip_beacon_aggregate_and_proof` vectors pass; integration tests for pool and packing |
| 7 | **Networking:** `GossipForkSubscriptionsPq`, 16 subnets (subscriptions, stable subnets, scoring), metadata/ENR syncnets zero, LC RPC off, PQ size bounds, gossip rate limits | M | 3 (schemas), 6 (validators) | 6, 8 | `networking:eth2` tests; local 2-node sync across the fork |
| 8 | **REST and beacon-APIs:** XMSS/VRF JSON types, validator pubkey types, removed endpoints, aggregate+proof and aggregator-duty endpoints (draft upstream beacon-APIs PR), OpenAPI regeneration | M–L | 3; API design from 0 | 6, 7, 9 | `beaconrestapi` unit and integration tests; VC↔BN contract agreed |
| 9 | **Validator client:** (9a) identity refactor (index-first `ValidatorIdentity`) can start after 1; (9b) `XmssSigner` stack, `XmssLeafProtector` store, reserved-leaf allocator, VRF provider and cache; (9c) duties (attestation, aggregation without selection proof, block with VRF, PTC, preferences once per slot, envelope; sync disabled); (9d) keystore format, key/VRF/`pq_data` generation CLI, keymanager import, pre-fork "unregistered" warnings; (9e) external-signer stance | XL | 1 (9a), 2 and 8 (9b–c) | 3–8 | VC unit tests; acceptance test with stubbed XMSS across the fork |
| 10 | **Real crypto:** `LeanPqXmss` on `io.github.crate-crypto:lean-pq` (keygen, sign, verify plus Teku's own leaf-prefix check); `--enable-native-access` in scripts, Docker and tests; key cache with `prepare`; performance benchmarks (BN verify throughput, VC sign latency at N keys); leanVM STARK through the `leanvm-sidecar` (`Sidecar.aggregate` / `Sidecar.verify` since lean-pq 0.13.0) once the spec defines `AttestationProof` | M | 2 (API) | 3–9 | KATs (`xmss_kat.json`, EthLib vectors) pass through the binding; JMH benchmarks within budget |
| 11 | **Reference tests and devnet:** local PQ vector generation and full ref-test run; Kurtosis config (Gloas → EIP8483 → PQ with real XMSS on minimal, validator count under the proof-size cap); multi-client interop when available | M–L | 4–10 | — | all PQ ref tests green; devnet finalises across the PQ fork with real signatures |

**Critical path:** 0 → 1 → 3 → 4 → 5 → 6 → 11.
**Long parallel tracks:** crypto (2 → 10) and the VC (9a early, 9b–d once phases 2 and 8 define the APIs).
**Smallest end-to-end milestone:** phases 1 to 5 plus the stubbed parts of 6. This passes local ref tests without any real crypto.

---

## 5. Open questions and risks

1. **Milestone placement vs HEZE (decided).**
   - The spec has `EIP8483 → GLOAS` and `PQ → EIP8483`; Heze is a sibling.
   - Teku's linear `SpecMilestone` cannot represent branches.
   - PQ's state layout assumes the EIP8483 state is **Gloas-based** (gaps 46/47 are the registrations; no Heze fields). Basing PQ on Heze would make PQ's SSZ layout diverge from the spec.
   - **Decision:** `GLOAS, EIP8483, PQ, HEZE` (#11654): PQ is composed from the EIP8483/Gloas classes, Heze builds on PQ, and on PQ devnets Heze is unscheduled or overshadowed.
2. **Spec churn.**
   - `upstream/pq` is a moving research branch. The aggregate gossip rules changed in the last three commits on 2026-11-17.
   - The leanXMSS hash function changed on 2026-10-28.
   - The STARK encoding is a placeholder.
   - Pin to a commit, keep a spec-diff log, and avoid polishing `AttestationProof` handling beyond the stand-in.
3. **Crypto backend.** Resolved for signatures: use `io.github.crate-crypto:lean-pq` (§2.2, §2.4). Remaining risks: upstream API churn (breaking releases 0.7, 0.9, 0.10, 0.11, 0.12 and 0.13 within a month), KAT drift as EthLib moves, the `--enable-native-access` requirement, and no musl build. From 0.13.0, real STARK verification needs a sidecar process on every node (§2.2).
4. **Leaf-index safety** (worse than slashing, because a reused leaf can be forged).
   - The VC must record before signing (durable fsync), and survive crash or restore.
   - A restored backup with an old leaf DB, or two VCs running one key, is catastrophic.
   - Reserved leaves need an allocator that also covers out-of-band uses (preregistration and exit signing done by CLI tools).
   - Doppelganger detection should be mandatory for PQ.
   - There is no interchange standard (EIP-3076 extension needed).
   - `ProposerPreferencesPublisher` must sign at most once per slot.
5. **Who proves aggregates in the split BN/VC model.** The spec's `get_aggregate_stark` needs individual signatures, which only the BN has.
   - Proposal: the BN builds the proof and returns aggregate + proof; the VC signs `AggregateAndProof`. This transfers up to 1 MiB per aggregate over REST.
   - Needs a beacon-APIs change and agreement with other clients.
6. **Stand-in proof size limits devnets.** `MAX_STARK_PROOF_SIZE` fits about 436 signatures per proof. This caps both a single aggregate (so committees should stay below about 436) and the whole block's merged proof. Devnets must use small validator sets, or accept partial inclusion. The block packer must budget bytes.
7. **VRF tree derivation and storage are unspecified.** This affects key portability between clients, backup and restore, and the keystore standard. It needs a cross-client convention (likely an EIP or keystore extension) before mainnet-like use.
8. **Keystore and tooling.** EIP-2335 is BLS-specific. There is no standard for XMSS seed + VRF seed keystores, `pq_data` generation, preregistration signing, or the exit message format submitted to the system contract (the CL spec defers these to EL system contracts that do not exist yet).
9. **EL support.** No EL implements the 0x05–0x09 system contracts. PQ devnets will have empty request lists, so the preregistration, deposit and exit paths are covered only by ref tests.
10. **Fork-boundary execution-requests root** (spec note in fork.md). The first PQ block's parent requests root may be computed over the EIP8483 type. Teku needs the same Teku-specific bridge as for EIP8483 until the spec resolves it.
11. **Deposit-stealing TODO** (fork.md). This is open in the spec. Behaviour may change, for example requiring proof of possession in the EIP8483 contracts.
12. **Interface blast radius.**
    - `BLSPublicKey`: about 200 main files.
    - `BLSSignature`: about 180.
    - `Validator.getPublicKey()`: 84.
    - `SyncCommittee`: 190.
    - `LightClient`: 108.
    - `VoluntaryExit`: 103.
    - `BlsToExecutionChange`: 77.
    - Prefer additive PQ accessors and milestone gating over a global type refactor. Accept `UnsupportedOperationException` on BLS getters for PQ objects, and add tests that sweep REST/VC paths with a PQ spec.
13. **Coordination with in-flight PRs.** PR #11627 (packed `Validator` views) and the pending EIP8483 commit. Rebase cost is high if PQ starts before they land.
14. **Performance.**
    - Java Keccak verify throughput on the BN (§3.8).
    - VC signing latency and bottom-tree memory at hundreds or thousands of keys (§3.15.10).
    - Upgrade-time recomputation over about 1M validators (§3.7).
    - Epoch-time `process_aggregation_committee_window`: 32 slots × up to 16 committees × up to 4 balance-weighted selections, each scanning the committee. Profile it next to `processPtcWindow`.
15. **Builder API / MEV-boost.** `ValidatorRegistration` and `BuilderRequestAuth` are BLS-signed and outside the consensus spec. Their PQ fate is undefined, so disable them or keep BLS on a pre-fork key until builder-specs say otherwise.
16. **Light-client and sync-committee removal** is user-visible: REST consumers, metrics, rewards APIs. Document it and return explicit errors for PQ epochs.
17. **Real aggregates do not bind leaf indices (raise with the spec authors).**
    - The spec's `XMSSAggregateStatement(pubkeys, leaf_index, message)` and `VerifyAggregates` check every signature's leaf index.
    - leanVM aggregates, as exposed by `lean-pq`, prove only `(pubkey, message)` claims, which are sorted and de-duplicated.
    - Either leanVM must add the leaf index to its claims, or the spec must fold the leaf index into the signed message (for example `hash(leaf ‖ root)`). Otherwise moving from the stand-in to real proofs changes the consensus semantics, not just the `AttestationProof` encoding.
    - Proving cost (seconds, about 1.6 GB native memory, one proof at a time per process) also affects aggregator and proposer timing and node sizing.
