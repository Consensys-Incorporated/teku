# Teku ePBS (EIP-7732 / Gloas) Implementation Status

## Progress

```
███████████████████████████████░░░░░░░░░ 78%
```

**131 items total** — 102 ✅ done · 3 🚧 in progress · 13 ⚠️ partial/verify · 13 ❌ missing

> Strict done % = ✅ / total. If ⚠️ partial items are counted as half-credit, effective progress is ~84%.

---

Snapshot date: 2026-05-01.
Branch: tracking branch `upstream/glamsterdam-devnet-2` at `3a21b91d4d` (24 commits ahead of `upstream/master`).

**Status legend:** ✅ Done (merged) · 🚧 In progress (open PR) · ❌ Not started · ⚠️ Partial / verify

**Baseline note:** Anything merged on `upstream/glamsterdam-devnet-2` counts as ✅ for tracking purposes, even if it has not yet landed on `upstream/master`. PR links are kept where applicable; commits without an associated PR are linked by SHA.

## Scope basis

- EIP-7732 ("Enshrined Proposer-Builder Separation"), Draft, June 2024.
- consensus-specs `specs/gloas/` on `master`: `beacon-chain.md`, `fork-choice.md`,
  `p2p-interface.md`, `validator.md`, `builder.md`, `fork.md`.
- Teku is tracking through `v1.7.0-alpha.7` of consensus-specs (PR [#10640](https://github.com/Consensys/teku/pull/10640)) and is being adapted incrementally as the spec moves.

## Caveats

- Consensus-specs Gloas is still moving; some Teku PRs labeled "for devnet-0/devnet-2" or
  "v1.7.0-alpha.X" may need follow-ups.
- Fork-choice work was planned as a 7-PR series. All 7 are now landed on `glamsterdam-devnet-2`:
  PRs 1..5 merged to master individually; 6/7 (#10610) was CLOSED and its content (including
  `ForkChoiceModelGloas`, `PayloadTimelinessCommitteeVoteTracker`, rebuild support) was absorbed into the omnibus
  [#10626](https://github.com/Consensys/teku/pull/10626); 7/7 ("live model swap") is done since
  `ForkChoiceModelFactory.forSlot` routes Gloas slots to `ForkChoiceModelGloas`.
- The "honest builder" guide (`gloas/builder.md`) is conceptually a separate
  deliverable; Teku has no in-protocol builder client today (proposer side talks to
  MEV-Boost / external builders only).

---

## Status table

### Fork plumbing & spec config

| Item | Description | Status |
|---|---|---|
| Bare-bones Gloas fork | Fork added, basis-points config, upgrade fn cleanup | ✅ [#9801](https://github.com/Consensys/teku/pull/9801), [#8800](https://github.com/Consensys/teku/pull/8800) (issue [#9819](https://github.com/Consensys/teku/issues/9819)) |
| Gloas state upgrade | Fork transition / state upgrade function | ✅ [#9886](https://github.com/Consensys/teku/pull/9886) |
| Spec config tracking | Refreshes through alpha.2 → alpha.7 | ✅ [#10141](https://github.com/Consensys/teku/pull/10141), [#10323](https://github.com/Consensys/teku/pull/10323), [#10503](https://github.com/Consensys/teku/pull/10503), [#10581](https://github.com/Consensys/teku/pull/10581), [#10640](https://github.com/Consensys/teku/pull/10640) |
| Specrefs & test scaffolding | Specrefs + new-fork unit-test detector + property-test fixes | ✅ [#10115](https://github.com/Consensys/teku/pull/10115), [#9804](https://github.com/Consensys/teku/pull/9804), [#9864](https://github.com/Consensys/teku/pull/9864) |
| Post-Gloas spec config cleanup | Remove compatibility constants no longer needed after devnet-0 (e.g. `ATTESTATION_SUBNET_PREFIX_BITS`, `MAX_REQUEST_BLOB_SIDECARS*`) | ❌ [#10499](https://github.com/Consensys/teku/issues/10499) |

### Data structures (SSZ containers)

| Item | Description | Status |
|---|---|---|
| Bid containers | `ExecutionPayloadBid`, `SignedExecutionPayloadBid`, blinded variants | ✅ [#9806](https://github.com/Consensys/teku/pull/9806), [#9871](https://github.com/Consensys/teku/pull/9871) |
| Envelope containers | `ExecutionPayloadEnvelope`, `SignedExecutionPayloadEnvelope` | ✅ [#9873](https://github.com/Consensys/teku/pull/9873), [#9912](https://github.com/Consensys/teku/pull/9912) |
| Payload attestation containers | `PayloadAttestation*`, `IndexedPayloadAttestation`, `PayloadAttestationMessage` | ✅ [#9943](https://github.com/Consensys/teku/pull/9943) |
| Proposer preferences | `ProposerPreferences`, `SignedProposerPreferences` | ✅ [#10530](https://github.com/Consensys/teku/pull/10530) |
| Builder containers | `Builder`, `BuilderPendingPayment`, `BuilderPendingWithdrawal` | ✅ [#9871](https://github.com/Consensys/teku/pull/9871) |
| `BeaconBlockBodyGloas` | Bid replaces payload; payload attestations added | ✅ [#10005](https://github.com/Consensys/teku/pull/10005), [#10549](https://github.com/Consensys/teku/pull/10549) |
| `BeaconStateGloas` | Builder registry, pending payments, latest_block_hash | ✅ [#9876](https://github.com/Consensys/teku/pull/9876), [#9916](https://github.com/Consensys/teku/pull/9916), [#8471](https://github.com/Consensys/teku/pull/8471) |
| `ExecutionPayload(Header)Gloas` | Gloas variants in `datastructures/execution/versions/gloas/` | ✅ |
| `DataColumnSidecar` Gloas changes | Sidecar shape adjusted for ePBS | ✅ [#9929](https://github.com/Consensys/teku/pull/9929) |

### State transition / block processing

| Item | Description | Status |
|---|---|---|
| `BlockProcessorGloas` skeleton | + `process_execution_payload_bid` | ✅ [#9932](https://github.com/Consensys/teku/pull/9932) |
| `process_execution_payload` (Gloas) | Execution payload processing | ✅ [#9939](https://github.com/Consensys/teku/pull/9939) |
| `process_attestation` (Gloas) | Modified attestation processing | ✅ [#9951](https://github.com/Consensys/teku/pull/9951) |
| `process_payload_attestation` | Payload attestation block processing | ✅ [#9943](https://github.com/Consensys/teku/pull/9943) |
| Modified withdrawals + proposer slashings | Initial Gloas variants | ✅ [#9911](https://github.com/Consensys/teku/pull/9911), [#9940](https://github.com/Consensys/teku/pull/9940) |
| Epoch processing helpers | `process_builder_pending_payments`, `process_ptc_window`, etc. | ✅ [#9944](https://github.com/Consensys/teku/pull/9944) |
| Fork-version helpers | `MiscHelpersGloas`, accessors, mutators, predicates, etc. | ✅ |
| State regeneration replays payload | Replay flow for execution payload | ✅ [#10511](https://github.com/Consensys/teku/pull/10511) |
| Unblinded execution payload cache | Cache for unblinded payloads at the EL boundary | ✅ [#10568](https://github.com/Consensys/teku/pull/10568) |
| Fulu→Gloas block boundary fix | Attestation + `onExecutionPayload` boundary correctness | ✅ [`245ff78`](https://github.com/Consensys/teku/commit/245ff78788b0df781525295fcebcacc2013ab292) |
| `apply_parent_execution_payload` | Latest spec rename (was `process_execution_payload` pre-alpha.5) | ✅ verify - add PR |
| `settle_builder_payment` end-to-end | Verify withdrawal placement matches spec | ✅ verify - add PR later |
| EIP-8061: scaled churn limits | `getActivationChurnLimit`, `getExitChurnLimit`, `getConsolidationChurnLimit` in `BeaconStateAccessorsGloas`; `initiatePendingBuilderExit` in `BeaconStateMutatorsGloas`; `processBuilderPendingWithdrawals` in `EpochProcessorGloas` | ✅ [#10629](https://github.com/Consensys/teku/pull/10629) |

### Fork choice (planned 7-PR series)

| Item | Description | Status |
|---|---|---|
| (1/7) Reorg + timeliness extracted | Logic extraction | ✅ [#10548](https://github.com/Consensys/teku/pull/10548) (issue [#9878](https://github.com/Consensys/teku/issues/9878)) |
| (2/7) `ForkChoiceNode` identity | Identity plumbing | ✅ [#10569](https://github.com/Consensys/teku/pull/10569) |
| (3/7) Slot-aware vote tracking | Vote-update plumbing | ✅ [#10573](https://github.com/Consensys/teku/pull/10573) |
| (4/7) Phase0 forkchoice model seam | Model abstraction | ✅ [#10590](https://github.com/Consensys/teku/pull/10590) |
| (5/7) Dormant Gloas helpers | Spec helpers + fork-gated utilities | ✅ [#10600](https://github.com/Consensys/teku/pull/10600) |
| (6/7) Dormant Gloas model + rebuild | Forkchoice model + rebuild support, blinded-envelope rebuild, anchor-state metadata, default-model rename. PR #10610 was CLOSED; content absorbed into #10626. | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) (issue [#10556](https://github.com/Consensys/teku/issues/10556)) |
| (7/7) Live model swap | `ForkChoiceModelFactory.forSlot` routes Gloas slots to `ForkChoiceModelGloas` (no longer dormant) | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) (issue [#10557](https://github.com/Consensys/teku/issues/10557)) |
| Avoid resolving unchanged votes | Optimization | ✅ [#10604](https://github.com/Consensys/teku/pull/10604) |
| Update head on imported payload | Head-update path | ✅ [#10478](https://github.com/Consensys/teku/pull/10478) |
| `notify_ptc_messages` integration | PTC observation feeds `PayloadTimelinessCommitteeVoteTracker`; consumed by Gloas head-selection helpers | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) |
| `should_extend_payload`, `get_payload_status_tiebreaker`, `should_apply_proposer_boost` | Gloas tiebreakers routed via `ForkChoiceStrategy` → `ForkChoiceModelGloas` | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) ([`268c9c4`](https://github.com/Consensys/teku/commit/268c9c49fe), [`4b9c233`](https://github.com/Consensys/teku/commit/4b9c2332057df278e620353cff057074be3f7839)) |
| `on_execution_payload_envelope` / `on_payload_attestation_message` Store integration | `ForkChoice.onExecutionPayloadEnvelope` and `ForkChoice.onPayloadAttestationMessage` wired to strategy/model | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) |
| `is_payload_verified` / `_timely` / `_data_available` predicates | Implemented in `ForkChoiceModelGloas` against PTC vote tracker thresholds | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) |
| Set `blob_data_available` correctly | VC-side correctness fix when surfacing payload-attestation data | ✅ [#10626](https://github.com/Consensys/teku/pull/10626) ([`c094b65`](https://github.com/Consensys/teku/commit/c094b6503268f9b32cfaee9a1f15b40423f82e05), [`6740e41`](https://github.com/Consensys/teku/commit/6740e41a0dd057b9f4ecc82bfb5f8d818fed7496)) |
| Optimistic head handling for Gloas | Proper optimistic head tracking in `ForkChoiceModelGloas` / `ForkChoiceStrategy` | ✅ [`990f23531a`](https://github.com/Consensys/teku/commit/990f23531a) |
| Stabilize forkchoice state during block production | Prevent fork choice state races while producing a block | ✅ [#10646](https://github.com/Consensys/teku/pull/10646) |
| Fix findhead on payload import | Correct head resolution after importing an execution payload | ✅ [#10649](https://github.com/Consensys/teku/pull/10649) |

### Networking — gossip

| Item | Description | Status |
|---|---|---|
| Attestation gossip rules | Gloas attestation rules | ✅ [#10099](https://github.com/Consensys/teku/pull/10099) |
| Block gossip rules | Gloas block rules | ✅ [#10140](https://github.com/Consensys/teku/pull/10140) |
| Payload-attestation gossip | Rules + `PayloadAttestationMessageGossipManager` | ✅ [#10186](https://github.com/Consensys/teku/pull/10186) |
| Execution-payload gossip | Rules + `ExecutionPayloadGossipManager` | ✅ [#10168](https://github.com/Consensys/teku/pull/10168) |
| Execution-payload-bid gossip | Rules + `ExecutionPayloadBidGossipManager` | ✅ [#10198](https://github.com/Consensys/teku/pull/10198) |
| DCSC gossip changes | Gloas DCSC adjustments | ✅ [#10299](https://github.com/Consensys/teku/pull/10299) |
| `GossipForkSubscriptionsGloas` | Wires all Gloas gossip managers | ✅ [#9966](https://github.com/Consensys/teku/pull/9966) |
| `ProposerPreferences` topic subscribe/publish | Manager exists; improvement PR -> [#10596](https://github.com/Consensys/teku/pull/10596) | ⚠️ need to verify if it works |
| `single_attestation` Gloas variant | Current schema is Electra-scoped | ⚠️ need to investigate the flow of single_att to the pool + api side + aggregation; the index of the attestation isn't only zero… it can be 0 or 1 |
| ENR / discovery field bumps | For Gloas fork digest | ✅ - find PR |

### Networking — Req/Resp

| Item | Description | Status |
|---|---|---|
| Boilerplate Gloas req/resp | Skeleton | ✅ [#9975](https://github.com/Consensys/teku/pull/9975) (issue [#9974](https://github.com/Consensys/teku/issues/9974), [#10301](https://github.com/Consensys/teku/issues/10301)) |
| Forward sync | Gloas forward sync | ✅ [#10388](https://github.com/Consensys/teku/pull/10388) (issue [#10069](https://github.com/Consensys/teku/issues/10069)) |
| BeaconBlocksByRange/Root v3 | Gloas envelope variant | ✅ [#10420](https://github.com/Consensys/teku/pull/10420), [#10422](https://github.com/Consensys/teku/pull/10422) |
| DataColumnSidecarsByRange/Root | Gloas variants | ✅ [#10429](https://github.com/Consensys/teku/pull/10429), [#10442](https://github.com/Consensys/teku/pull/10442), [#10382](https://github.com/Consensys/teku/pull/10382) |
| `ExecutionPayloadEnvelopesByRange` | Impl + tests | ✅ [#10374](https://github.com/Consensys/teku/pull/10374) |
| `ExecutionPayloadEnvelopesByRoot` | Handler present | ✅ |
| Serve finalized envelopes by range | Cold-store serve path | ✅ [#10605](https://github.com/Consensys/teku/pull/10605) |
| Avoid SSZ-backed `IndexedAttestation` | Memory optimisation | ✅ [#10599](https://github.com/Consensys/teku/pull/10599) |
| Extend `*ByRoot` serve range | Match `ByRange` for Gloas fork-boundary | ✅ [#10598](https://github.com/Consensys/teku/pull/10598), [#10616](https://github.com/Consensys/teku/pull/10616) |
| Fix RPC decoder reuse after dispose | `AbstractByteBufDecoder` / `RpcResponseDecoder` — prevents `IllegalStateException` when a single `channelRead` ByteBuf spans two response messages | ✅ [`3a21b91d4d`](https://github.com/Consensys/teku/commit/3a21b91d4d) |
| Execution payload historical sync | Backfill envelopes | 🚧 [#10592](https://github.com/Consensys/teku/pull/10592) (issue [#10069](https://github.com/Consensys/teku/issues/10069)) - needs review @lucassaldanha |
| `ExecutionPayloadEnvelopes` req/resp integration tests | `ExecutionPayloadEnvelopesByRange/RootIntegrationTest` not yet written | ❌ [#9974](https://github.com/Consensys/teku/issues/9974) |

### Pools / managers

| Item | Description | Status |
|---|---|---|
| `AggregatingPayloadAttestationPool` | + `MatchingDataPayloadAttestationGroup` | ✅ [#10536](https://github.com/Consensys/teku/pull/10536) |
| `PayloadAttestationMessageGossipValidator` | Validator | ✅ |
| `ExecutionPayloadBidGossipValidator` + manager | Validator + `DefaultExecutionPayloadBidManager` | ✅ |
| `ExecutionPayloadGossipValidator` + manager | Validator + `DefaultExecutionPayloadManager` | ✅ |
| `ProposerPreferencesGossipValidator` + manager | Validator + `DefaultProposerPreferencesManager` | ✅ |
| `RecentExecutionPayloadsFetcher` | Fetch payload on payload attestation | ✅ [#10394](https://github.com/Consensys/teku/pull/10394), [#10579](https://github.com/Consensys/teku/pull/10579) |
| Cache early payload / state selection | `on_block` & block production caches | ✅ [#10440](https://github.com/Consensys/teku/pull/10440), [#10302](https://github.com/Consensys/teku/pull/10302), [#10353](https://github.com/Consensys/teku/pull/10353), [#10334](https://github.com/Consensys/teku/pull/10334) |
| Availability checker NOOP for `on_block` | Gloas-specific | ✅ [#10591](https://github.com/Consensys/teku/pull/10591) |
| Builder bid pool | `DefaultExecutionPayloadBidManager` stores received bids indexed by slot (sorted by value descending); `getBidForBlock` selects highest-value remote bid, falls back to self-built. `BUILDER_PAYMENT_THRESHOLD` comparison still missing. | ⚠️ [`87a8d01939`](https://github.com/Consensys/teku/commit/87a8d01939) |
| Skip envelope unblinding when BAL is null | Guard in execution payload manager prevents unblinding when `block_auction_leaf` is null | ✅ [#10634](https://github.com/Consensys/teku/pull/10634) |

### Engine API / EL

| Item | Description | Status |
|---|---|---|
| Engine API call shape adjusted | Gloas-aware Engine API client tweaks | ✅ [#10397](https://github.com/Consensys/teku/pull/10397) |
| `engine_getBlobsV1`/`V2` | Pre-Gloas; reused | ✅ |
| Gloas-specific newPayload / `getPayloadV6` | Dedicated Gloas Engine API method group | ✅ |
| `BlindedExecutionPayloadProvider` | Lookup layer for blinded payloads from the store (`dataproviders/lookup/`); wired into `DefaultExecutionPayloadManager` and `RecentChainData` | ✅ [`927bf1a791`](https://github.com/Consensys/teku/commit/927bf1a791) |
| `engine_getBlobs` envelope variant | Builder-published sidecars vs proposer flow | ❌ - check @lucassaldanha |

### Validator / VC

| Item | Description | Status |
|---|---|---|
| Local signer extended for Gloas | Domain types | ✅ [#9982](https://github.com/Consensys/teku/pull/9982) |
| Bare-bones Gloas block production | Stub | ✅ [#9962](https://github.com/Consensys/teku/pull/9962) |
| Self-built bid generation | Self-build path | ✅ [#9999](https://github.com/Consensys/teku/pull/9999) |
| PTC duty scaffolding | `PayloadTimelinessCommitteeDutyScheduler`, `PayloadTimelinessCommitteeDutyLoader`, `PayloadAttestationProductionDuty`, factory | ✅ [#10043](https://github.com/Consensys/teku/pull/10043), [#10126](https://github.com/Consensys/teku/pull/10126) |
| `PtcDuty(ies)` JSON types | API types | ✅ |
| `ProposerPreferencesPublisher` | VC publisher | ✅ |
| Unified validator API handler | Removed `ValidatorApiHandlerGloas` | ✅ [#10561](https://github.com/Consensys/teku/pull/10561) |
| `GetAttestationData` validations | Gloas-aware | ✅ [#10417](https://github.com/Consensys/teku/pull/10417), [#10432](https://github.com/Consensys/teku/pull/10432) |
| `BlobSidecarSelectorFactory` | Gloas fix | ✅ [#10209](https://github.com/Consensys/teku/pull/10209) |
| Wire proposer preferences into proposer duties | Gloas-only flow | 🚧 [#10596](https://github.com/Consensys/teku/pull/10596) - review @lucassaldanha |
| PTC duty end-to-end signing | Verify against `DOMAIN_PTC_ATTESTER` | ✅ |
| Block proposer bid-selection | Bid threshold + `BUILDER_PAYMENT_THRESHOLD_NUMERATOR/DENOMINATOR` comparison not yet enforced (pool selects by value only) | ❌ |
| Block production state selection | Proper state selection during block production (post `ValidatorApiHandlerGloas` removal) | ❌ [#10352](https://github.com/Consensys/teku/issues/10352) |
| `BlockProductionPerformance` Gloas enhancements | Capture payload-attestation aggregation + bid-retrieval timing | ❌ [#10129](https://github.com/Consensys/teku/issues/10129) |
| Self-build execution payload timing | Better delay heuristics (currently hardcoded 500 ms) for execution payload duty when self-built bid chosen | ❌ [#10018](https://github.com/Consensys/teku/issues/10018) |
| Honest builder client | Decision: ship builder mode or rely on MEV-Boost? | ❌ open question |

### REST API

| Item | Description | Status |
|---|---|---|
| `GET /eth/v1/validator/execution_payload_bids/{slot}/{builder_index}` | Bid retrieval | ✅ [#10463](https://github.com/Consensys/teku/pull/10463), [#10476](https://github.com/Consensys/teku/pull/10476) |
| `POST /eth/v1/beacon/blocks` v2 | Gloas variant (`PostBlockV2`) | ✅ |
| `GET /eth/v1/beacon/pool/payload_attestations` | Pool read | ✅ [#10465](https://github.com/Consensys/teku/pull/10465) |
| `POST /eth/v1/beacon/pool/payload_attestations` | Pool publish | ✅ [#10534](https://github.com/Consensys/teku/pull/10534) |
| Gloas event-stream additions | + version field | ✅ [#10383](https://github.com/Consensys/teku/pull/10383), [#10481](https://github.com/Consensys/teku/pull/10481) |
| `ExecutionPayloadBidEvent`, `PayloadAttestationMessageEvent` SSE | Events | ✅ |
| `GloasRestApiBuilderAddon` | Routing | ✅ (issue [#9997](https://github.com/Consensys/teku/issues/9997)) |
| `GET /eth/v1/beacon/execution_payload_envelopes/{block_id}` + publish endpoint | Read + `block_validation` query param; handler improved in [#10615](https://github.com/Consensys/teku/pull/10615) | 🚧 [#10537](https://github.com/Consensys/teku/pull/10537) (issue [#10416](https://github.com/Consensys/teku/issues/10416)) - review @lucassaldanha |
| `GET /eth/v4/validator/blocks/{slot}` | Gloas proposer block endpoint (beacon-APIs PR [#580](https://github.com/ethereum/beacon-APIs/pull/580)) | ❌ [#10414](https://github.com/Consensys/teku/issues/10414), [#10092](https://github.com/Consensys/teku/issues/10092) |
| `POST /eth/v1/validator/duties/ptc/{epoch}` | Integration test exists; main handler not located | ⚠️ verify |
| Honest-builder REST endpoints | Publish envelope / register builder | ❌ |

### Builder lifecycle

| Item | Description | Status |
|---|---|---|
| Builder registry ingest from `0x03` deposits | `apply_deposit_for_builder`, `add_builder_to_registry` paths | ⚠️ verify end-to-end |
| `initiate_builder_exit` / voluntary exit | Gloas variant of `process_voluntary_exit` | ⚠️ verify end-to-end |
| `is_active_builder` / `is_builder_index` predicates | Usage in slashing / attester paths | ⚠️ verify end-to-end |
| `BUILDER_INDEX_FLAG` / `BUILDER_INDEX_SELF_BUILD` sentinels | Proposer-bid path handling | ⚠️ verify end-to-end |
| `convert_builder_index_to_validator_index` (and inverse) | Exposure via `MiscHelpersGloas` | ⚠️ verify end-to-end |

### Slashing

| Item | Description | Status |
|---|---|---|
| `process_proposer_slashing` Gloas | Builders reusing proposer slashing flow | ✅ |
| PTC equivocation / surrogate slashing | `IndexedPayloadAttestation` slashing | ⚠️ - check refs spec |

### Storage / DB

| Item | Description | Status |
|---|---|---|
| Envelope persistence (by-range serve) | Cold-store serve path | ✅ [#10605](https://github.com/Consensys/teku/pull/10605) |
| Long-term envelope pruning policy | Layout & retention | ⚠️ partial |
| Persistence of payload attestations in finalized state | Finalized DB layout | ⚠️ verify |
| DataColumnSidecar storage for Gloas | Sidecars produced by builder vs block | ✅ |

### KZG / blobs

| Item | Description | Status |
|---|---|---|
| `verify_data_column_sidecar` Gloas variant | Validation against envelope | ⚠️ verify end-to-end |
| Builder-published sidecar flow | Spec language re builder vs proposer | ❌ |

### Honest-builder guide (out of scope unless decided)

| Item | Description | Status |
|---|---|---|
| `get_execution_payload_bid_signature` | Builder signing helper | ✅ |
| `get_execution_payload_envelope_signature` | Builder signing helper | ✅ |
| Sidecar construction by builder | Builder-side blob/sidecar production | ❌ |

### Acceptance / test infrastructure

| Item | Description | Status |
|---|---|---|
| Forkchoice fixes + acceptance test | E2E coverage | ✅ [#10400](https://github.com/Consensys/teku/pull/10400) |
| `RespondingEth2Peer` Gloas | Test peer adapted | ✅ [#10108](https://github.com/Consensys/teku/pull/10108) |
| `ChainBuilder` Gloas support | Test fixtures | ✅ [#10107](https://github.com/Consensys/teku/pull/10107) |
| Reference tests for Gloas (excl. fork_choice) | Spec tests enabled | ✅ [#9957](https://github.com/Consensys/teku/pull/9957) |
| Remove test ignore flags for Gloas | Unit / property / integration tests re-enabled | ✅ [#9833](https://github.com/Consensys/teku/issues/9833) |
| `MergedGenesisInteropModeAcceptanceTest` Gloas | Gloas genesis support fixed; test now runs for all milestones including Gloas | ✅ [#10615](https://github.com/Consensys/teku/pull/10615) |
| Enable Gloas in `DataColumnSidecars*IntegrationTests` | `DataColumnSidecarsByRoot/RangeIntegrationTest` require Gloas-aware `ChainBuilder` strategy | ❌ [#9803](https://github.com/Consensys/teku/issues/9803) |

---

## Quick gap divvying suggestion

| Owner area | Items |
|---|---|
| Fork choice | Confirm `apply_parent_execution_payload` rename audit; tighten partial verifications |
| State transition | Builder lifecycle (deposit / exit / settle), slashing surface |
| Validator/VC | Block proposer bid threshold (`BUILDER_PAYMENT_THRESHOLD_*`) ([#10352](https://github.com/Consensys/teku/issues/10352)); PTC end-to-end signing; proposer-prefs ([#10596](https://github.com/Consensys/teku/pull/10596)); self-build timing ([#10018](https://github.com/Consensys/teku/issues/10018)); `BlockProductionPerformance` ([#10129](https://github.com/Consensys/teku/issues/10129)) |
| Networking | `single_attestation` Gloas review; proposer-prefs subscribe/publish (blocked on [#10596](https://github.com/Consensys/teku/pull/10596)); Req/Resp integration tests ([#9974](https://github.com/Consensys/teku/issues/9974)) |
| REST API | `GET execution_payload_envelopes` ([#10537](https://github.com/Consensys/teku/pull/10537)); `GET /eth/v4/validator/blocks/{slot}` ([#10414](https://github.com/Consensys/teku/issues/10414)); validator PTC duties handler verification; honest-builder REST endpoints |
| Engine API | `engine_getBlobs` envelope variant |
| Builder client | Open question: does Teku ship a honest-builder mode? |
| Storage | Long-term envelope/attestation pruning policy; finalized payload-attestation persistence |
| Testing | `DataColumnSidecars*IntegrationTests` ([#9803](https://github.com/Consensys/teku/issues/9803)) |
| Post-devnet-0/2 | Spec config cleanup ([#10499](https://github.com/Consensys/teku/issues/10499)) |
