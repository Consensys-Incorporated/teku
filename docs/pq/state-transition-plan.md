# Teku PQ fork: state-transition plan

Last updated 2026-11-20. Part of the [PQ fork plan](pq-fork-plan.md) (phase 4, with the switch-over that brings in phase 5). Follows the [containers plan](containers-plan.md).

## Summary

We implement PQ's state transition in five slices, each a PR into `pq-devnet-0`. The first four build the PQ logic classes standalone and test them directly against PQ states, as the containers were built, so the PQ milestone keeps running as EIP-8483 meanwhile. The fifth slice switches the milestone over: `SpecLogicPq` and `SchemaDefinitionsPq` start using the PQ containers and logic, and the fork upgrade from EIP-8483 lands with it, since a fork into PQ cannot produce a PQ state without it.

XMSS and STARK verification sit behind interfaces with a disabled mode, as the reference tests require. Real verification on lean-pq is wired in phase 10.

| Slice | Status |
| --- | --- |
| 1. Helpers | Done (`pq-st-helpers`) |
| 2. Execution requests | Done (`pq-st-requests`) |
| 3. Epoch processing | Done (`pq-st-epoch`) |
| 4. Block processing | Done (`pq-st-block`) |
| 5. Switch-over and fork upgrade | Upgrade done (`pq-st-upgrade`); switch-over done (`pq-st-switch`) except the reference-test fork |

## Approach

Build standalone, switch last. Each standalone class extends its Gloas or EIP-8483 counterpart (`MiscHelpersGloas`, `BeaconStateAccessorsGloas`, `BlockProcessorGloas`, `EpochProcessorGloas`, `ExecutionRequestsProcessorEip8483`) and is constructed directly in its tests, with a PQ state built from `SchemaDefinitionsPq.getPqBeaconStateSchema()`.

**After the switch, PQ devnets stop running until the validator client signs with XMSS.** Blocks then need XMSS signatures and VRF proofs, attestations go into a STARK proof, and the validator client still signs with BLS until phase 9. Unit and reference tests keep covering the state transition in the meantime, and the switch can wait until the validator client work is close if a runnable devnet matters more.

## Slices

1. **Helpers** (`MiscHelpersPq`, `PredicatesPq`, `BeaconStateAccessorsPq`, `BeaconStateMutatorsPq`).
   - Leaf indices: `compute_xmss_attestation_leaf_index`, `compute_xmss_leaf_index`, `is_builder_bid_leaf_index`.
   - Legacy and deposit keys: `compute_legacy_xmss_pubkey`, `compute_legacy_vrf_root`, `compute_deposit_pubkey`, `compute_deposit_vrf_root`, `is_legacy_pubkey`.
   - `is_valid_vrf_proof` (a depth-31 Merkle branch against `validator.vrf_root`).
   - Committees: `get_committee_count_per_slot` capped at `MAX_COMMITTEES_PER_SLOT_PQ`, with `get_beacon_committee` and `get_attesting_indices` following it. Every shuffling and committee cache must go through the per-fork accessor.
   - Aggregation committees: `compute_aggregation_committee(s)` (reusing Gloas's `compute_balance_weighted_selection`) and `get_aggregation_committee`.
   - Attestations: `get_indexed_attestation(state, attestation, proof)`, `get_attestation_statements`, `get_latest_block_slot`, and the modified `get_attestation_participation_flag_indices` (payload availability at the latest block's slot; timely head only at an inclusion delay of exactly 2).
   - Preregistrations: the index and active-binding getters (hash lookups, not the spec's linear scans), `is_active_*_preregistration`, `remove_*_preregistration`.
   - `is_pending_validator` by deposit pubkey, and `queue_excess_active_balance` writing `0^16 ‖ xmss_pubkey`.
2. **Execution requests** (`ExecutionRequestsProcessorPq`).
   - Deposit and builder deposit requests must match an active preregistration (withdrawal credentials, and the VRF root for validators), which they consume; otherwise they are dropped. A legacy builder deposit exits the builder at once.
   - Withdrawal, consolidation and builder exit requests look keys up by `pubkey[16:]` and ignore legacy keys.
   - New validator and builder preregistration requests and voluntary exit requests.
   - `apply_parent_execution_payload`'s new order and limits; the EIP-8483 registration processors are dropped.
3. **Epoch processing** (`EpochProcessorPq`).
   - `process_aggregation_committee_window` and `process_preregistration_expiry` after `process_ptc_window`; no sync-committee updates.
   - `apply_pending_deposit` and `process_pending_deposits`: legacy deposits bypass churn and create already-exited validators; new validators reuse indices and get their VRF root.
4. **Block processing** (`BlockProcessorPq`).
   - XMSS signatures: the block, the proposer slashing headers, the bid (leaf read from the signature, within the builder range; an all-zero signature for a self-build) and the payload envelope.
   - `process_randao` with the VRF proof: `mix = sha256(prev_mix ‖ reveal[0] ‖ DOMAIN_RANDAO)`.
   - `process_operations` with PQ's limits and `process_stark` after the attestations; `process_attestation` without a signature and with the inclusion delay of 2. No sync aggregate, exits, BLS changes or payload attestations.
   - `XmssSignatureVerifier` and a STARK verifier interface threaded through block processing, each with a disabled mode, and `is_valid_indexed_attestation` through the STARK verifier.
5. **Switch-over and fork upgrade.**
   - `SpecLogicPq` wires the PQ helpers and processors, with no sync-committee or light-client util; `SchemaDefinitionsPq` returns the PQ containers as the milestone's schemas, with registry entries. **Done**: the state and execution requests are registry entries for PQ; the block, attestation, attester slashing and XMSS-signed bid, envelope and proposer preferences schemas are returned by `SchemaDefinitionsPq`. The aggregate, single attestation and payload attestation message schemas don't share the Gloas types and stay on `getPqAttestationSchemas()` until PQ's networking uses them. Block production (`createBeaconBlockBodyBuilder`) throws until the validator client work.
   - `PqStateUpgrade` implements `upgrade_to_pq`: registered and unregistered validators and builders, pending deposits, unregistered builders' payments, and the initial aggregation-committee window. **Done.**
   - Caches keyed by BLS public keys (`ValidatorIndexCache`, the pubkey caches, `BuilderIndexCache`) are reset at the fork and keyed by `Bytes32` from PQ. **Partly done**: the upgrade builds an empty state, so the caches start empty; PQ lookups by XMSS key are linear scans, still to be cached.
   - The fork-boundary bridge for parent execution requests extends to EIP-8483 → PQ. **Done**, in `BlockProcessorPq`: a pre-PQ parent is matched as EIP-8483 requests, or Gloas requests when it predates EIP-8483 too.
   - The `pq` reference-test fork joins `SUPPORTED_FORKS` and its executors are wired. **Deferred** to the bump to consensus-specs `2f43bc0f9`, which adds PQ to `TESTGEN_FORKS` so vectors can be generated.
   - **Heze** builds on PQ on this branch, so it switches too: `SpecLogicHeze` uses the PQ logic with EIP-8365's deposit rule on `EpochProcessorPq`, and `HezeStateUpgrade` only changes the fork, running `upgrade_to_pq` first when PQ shares Heze's epoch.

## Testing

- **Unit tests** for every helper and processor, against PQ states built from the PQ schema, including the generator's PQ genesis (test resource `pq_genesis_minimal.ssz`), whose aggregation-committee window gives a cross-check for `compute_aggregation_committees`.
- **Reference tests** (`operations`, `epoch_processing`, `sanity`, `fork`, `transition`): the vectors must be generated locally from consensus-specs `a4d16c5be` until a release carries them. Generating them is a prerequisite for slice 5.
- **Interop** after the switch needs the validator client's XMSS signing (phase 9).

## Risks and open questions

- **No vectors yet.** Until PQ vectors are generated, correctness rests on unit tests written from the spec text, with the generator's genesis as the only independent check.
- **Committee cap.** Capping at 16 committees changes shuffling, committee sizes and indices. Any cache that bypasses the per-fork accessor gives wrong committees only in PQ.
- **Spec gaps.** The fork-boundary execution-requests root, and registrations taking pending new-validator deposits, are open in the spec ([PQ fork plan §1.6](pq-fork-plan.md)).
- **Runnable devnets.** See Approach: the switch stops PQ devnets until phase 9.
- **Validator client event size.** A maximum PQ attester slashing (two 1 MiB STARK proofs) is an 8,389,454-byte `attester_slashing` event, over the validator client's 8 MB default limit. To settle with the validator client work (`EventStreamDefaultSizeLimitTest` leaves PQ out until then).
