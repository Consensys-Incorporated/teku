# Teku PQ fork: containers plan

Last updated 2026-11-19. Part of the [PQ fork plan](pq-fork-plan.md) (phase 3).

## Summary

We build Teku's PQ SSZ containers and schemas in four slices, each its own PR into `pq-devnet-0`. Each slice adds new schemas and tests without switching the PQ milestone over to them, so the PQ fork keeps running as it does today. The switch happens with the state transition, the phase after this one.

The first slice is the state side, which we can check byte for byte against a real PQ genesis from the ethpandaops genesis generator. Slice 1 settled the one open design decision: how the PQ `Validator` (and `Builder`) fits Teku's `BeaconState` interface.

| Slice | Status |
| --- | --- |
| 1. State | Merged ([#11656](https://github.com/Consensys-Incorporated/teku/pull/11656)) |
| 2. Execution requests | Merged ([#11657](https://github.com/Consensys-Incorporated/teku/pull/11657)) |
| 3. Attestations | Not started |
| 4. Block body and signed wrappers | Not started |

## Where we are

The PQ milestone exists in Teku and runs, but it still uses EIP-8483's containers and BLS signatures. We target consensus-specs commit [a4d16c5be](https://github.com/ethereum/consensus-specs/tree/a4d16c5be5e8bf8c5a3fb323ee1cb0293efecb1c), the `pq` branch tip noted for the devnet.

| PR into `pq-devnet-0` | What it added |
| --- | --- |
| [#11657](https://github.com/Consensys-Incorporated/teku/pull/11657) | PQ execution requests (slice 2) |
| [#11656](https://github.com/Consensys-Incorporated/teku/pull/11656) | PQ state containers; `Validator` and `Builder` become interfaces (slice 1) |
| [#11654](https://github.com/Consensys-Incorporated/teku/pull/11654) | PQ milestone skeleton: config, presets, fork plumbing; behaves as EIP-8483 |
| [#11652](https://github.com/Consensys-Incorporated/teku/pull/11652) | `infrastructure/xmss`: leanXMSS sign and verify on lean-pq 0.11.0 |
| [#11649](https://github.com/Consensys-Incorporated/teku/pull/11649) | EIP-8483 PQ registration fork on top of Gloas |

Local runs on the skeleton:

- **Gloas at genesis, PQ at epoch 1 (Kurtosis, 2 Teku + ethrex):** the fork happened at slot 8, with no missed slots and finalization (justified 4, finalized 3).
- **PQ at genesis, Teku interop genesis:** single node, every slot a PQ block, finalized.
- **PQ at genesis, ethpandaops `pq` genesis generator:** Teku fails to load `genesis.ssz`. This is expected: the generator builds the real PQ state, which this phase adds.

## What changes

PQ swaps BLS for leanXMSS keys and signatures and a STARK attestation proof, and drops sync committees, BLS exits and payload attestations. The spec's four new types are `XMSSPubkey` (32 bytes), `XMSSSignature` (2,404 bytes, leaf index first), `VRFProof` (32 × `Bytes32`) and `AttestationProof` (byte list up to `MAX_STARK_PROOF_SIZE`, 1 MiB placeholder).

| Container | Change in PQ | Slice |
| --- | --- | --- |
| `Validator` | pubkey becomes `XMSSPubkey`; new `vrf_root` at position 1 (9 fields) | 1 |
| `Builder` | pubkey becomes `XMSSPubkey` | 1 |
| `BeaconState` | Width 51. Gaps at 22 and 23 (sync committees) and 46 and 47 (EIP-8483 registrations). Adds `validator_preregistrations` (48), `builder_preregistrations` (49) and `aggregation_committee_window` (50) | 1 |
| `ValidatorPreregistration`, `BuilderPreregistration`, `AggregationCommittee(s)`, `AggregationCommitteeWindow` | New | 1 |
| `ExecutionRequests` | Width 10, gaps at 5 and 6. Adds validator and builder preregistrations (7, 8) and `voluntary_exits` (9), request types 0x07 to 0x09 | 2 |
| `ValidatorPreregistrationRequest`, `BuilderPreregistrationRequest`, `VoluntaryExitRequest` | New | 2 |
| `DepositRequest`, `PendingDeposit`, builder and other EL requests | Pubkey and signature become raw `Bytes48`/`Bytes96`; same bytes as today, so the existing containers are reused | — |
| `Attestation` | `signature` removed (gap at 2); `CommitteeBits` sized by `MAX_COMMITTEES_PER_SLOT_PQ` | 3 |
| `IndexedAttestation` (and `AttesterSlashing`) | `signature` replaced by `proof` | 3 |
| `AggregateAndProof`, `SignedAggregateAndProof` | `selection_proof` replaced by `proof`; XMSS signature | 3 |
| `SingleAttestation`, `PayloadAttestationMessage` | XMSS signature | 3 |
| `BeaconBlockBody` | Width 14, gaps at 7, 8, 9 and 11 (exits, sync aggregate, BLS changes, payload attestations). `randao_reveal` becomes `VRFProof`; adds `proof` at 13 | 4 |
| `SignedBeaconBlock`, `SignedBeaconBlockHeader` (and `ProposerSlashing`), `SignedExecutionPayloadBid`, `SignedExecutionPayloadEnvelope`, `SignedProposerPreferences` | XMSS signature | 4 |

Removed containers (sync committee and light client, `VoluntaryExit`, `BLSToExecutionChange`, `PayloadAttestation`, `IndexedPayloadAttestation`, the EIP-8483 registrations) just go unused from PQ on.

## Approach

Build the containers standalone, then wire them in with the state transition. If PQ's state schema changed before the transition logic, every PQ run would break: Teku would still process sync committees and sign with BLS against a state that has neither. Building standalone keeps `pq-devnet-0` runnable between PRs. Until the switch, `SchemaDefinitionsPq` exposes the new schemas through their own getters (`getPqBeaconStateSchema()`, `getPqExecutionRequestsSchema()`).

The slices land in order, each as a PR into `pq-devnet-0` with its tests:

1. **Shared types and the state.**
   - PQ `Validator` and `Builder`, the preregistration records and the aggregation-committee types.
   - `BeaconStateSchemaPq` and the `BeaconStatePq` classes.
   - Gate: Teku loads the generator's PQ genesis and gets the same validators root.
2. **Execution requests.**
   - The three request containers and `ExecutionRequestsSchemaPq`.
   - The request codec for types 0x07 to 0x09. Earlier forks keep ignoring request types they don't know; only PQ rejects EIP-8483's 0x05 and 0x06, which it removed.
3. **Attestations.**
   - SSZ wrappers for `XMSSSignature` and `AttestationProof`.
   - `Attestation`, `IndexedAttestation`, `AttesterSlashing`, `SingleAttestation`, `AggregateAndProof` and its signed wrapper, `PayloadAttestationMessage`.
4. **Block body and signed wrappers.**
   - The `VRFProof` wrapper, `BeaconBlockBodyPq` and the XMSS-signed block, header, bid, envelope and proposer-preference wrappers.
   - Move today's shared static schemas into per-fork registry schemas: `SignedBeaconBlockHeader`, `ProposerSlashing` and `AggregateAndProof`.

After slice 4, the state-transition phase points `SchemaDefinitionsPq` at the new schemas. From then on PQ is the real PQ fork rather than an EIP-8483 copy.

Two patterns apply throughout:

- **BLS getters stay; XMSS getters are added beside them.** `getAggregateSignature()`, `getRandaoReveal()`, `SignedBeaconBlock.getSignature()` and similar throw on PQ objects. This avoids a generic signature type that would touch about 180 files.
- **PQ state implements the Gloas state interface.** Sync-committee getters and setters throw on PQ, and the few callers that reach them must first check that the fork has sync committees.

## Decision made: the PQ `Validator`

`Validator` and `Builder` are now interfaces (landed in slice 1).

`BeaconState.getValidators()` returned a list of Teku's concrete `Validator`, an 8-field container with a 48-byte BLS key. The PQ validator has 9 fields and a 32-byte key, so a PQ state couldn't implement `BeaconState` without a common type. The packed validator views from [#11627](https://github.com/Consensys-Incorporated/teku/pull/11627) already noted that a fork changing the validator needs its own schema and view class.

| Option | What changes | Cost now | Cost later |
| --- | --- | --- | --- |
| **A. `Validator` becomes an interface** (chosen) | The BLS class is renamed `ValidatorPhase0` and keeps the packed views. `ValidatorPq` is added. `getPublicKey()` and `getPubkeyBytes()` throw on PQ; `getXmssPublicKey()` and `getVrfRoot()` are on `ValidatorPq`. | 17 files changed; code that only reads validators is untouched. | Low: shared helpers (committees, balances, epoch processing) work on both. |
| B. Separate PQ state type | The PQ state gets its own validator accessors outside `BeaconState`. | Small | High: every shared helper needs a PQ path. |

`Builder` got the same treatment: the BLS class is `BuilderGloas`, and `BuilderPq` is added.

## Testing

Each slice is checked against two independent sources of truth, plus unit tests.

- **The generator's PQ genesis.**
  - A 64-validator minimal PQ state from the ethpandaops genesis generator's `pq` image (eth-beacon-genesis `284025a0e8`), with leanXMSS keys, VRF roots and 0x02 credentials. It is a test resource (`pq_genesis_minimal.ssz`).
  - It is a Go implementation, independent of the Python spec, so agreement is a real cross-check.
  - Slice 1: Teku deserializes it, re-serializes it byte for byte, and matches its `genesis_validators_root` and aggregation committees.
  - Slice 2: the empty PQ `ExecutionRequests` matches its bid's `execution_requests_root` (`0xe885a70c…d6a7`).
  - Slice 4: its `latest_block_header.body_root` (`0x36a7c9d1…304d`) is the hash tree root of a default PQ `BeaconBlockBody`.
- **Spec `ssz_static` vectors.**
  - No release carries PQ vectors yet, so they must be generated from consensus-specs `a4d16c5be`.
  - Teku's runner can already read a local vector directory (`-Dteku.ref-test.root-dir`). The `pq` ref-test fork is wired but not yet in `SUPPORTED_FORKS`; it is added once vectors exist.
- **Unit tests per container:** SSZ round trips, active-field gaps and indices, limits, and the BLS-getter guards.

## Risks and open questions

- **Merge pressure.** The `Validator` refactor touched shared files across modules on a long-lived branch. Master changes to the same files will conflict when `pq-devnet-0` is synced.
- **Shared static schemas.** `SignedBeaconBlockHeader`, `ProposerSlashing` and `AggregateAndProof` each have one schema instance used by every fork. Moving them to per-fork schemas in slice 4 touches pre-PQ code paths, so it needs the full test suite.
- **Spec drift.** The `pq` branch is still moving. We pin `a4d16c5be`, and the genesis generator may track a different commit. A mismatch between the two shows up as a failed cross-check, not a silent bug.
- **Placeholder proof size.** `MAX_STARK_PROOF_SIZE` (1 MiB) is a stand-in until a real STARK proof format is specified. Schemas take it from config, so a change needs no code.
- **Vector generation.** Generating PQ `ssz_static` vectors means running the spec's test generator locally.

## After the containers

The state transition comes next, with XMSS stubbed. It covers VRF RANDAO, the attestation proof, preregistration and exit requests, aggregation committees and the 16-committee cap, and it switches the PQ milestone to the new schemas. After that comes the fork upgrade from EIP-8483, then the beacon-node pipelines and the validator client's move to XMSS signing. The validator-client work uses the generator's key derivation, `sha256("leanxmss-seed" || BLS secret key)`, to sign for genesis validators.
