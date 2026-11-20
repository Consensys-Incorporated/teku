# PQ phase 8: REST and the beacon-APIs

Phase 8 of `pq-fork-plan.md`: make the REST API work at PQ slots, and give the validator client the endpoints its PQ duties need (phase 9c).

## Where things stand

Most of Teku's REST JSON comes from the SSZ schemas of each milestone. So blocks, states, pool attestations, envelopes and anything read by index already work at PQ slots. What breaks falls into five groups:

1. **Validator keys.** Lookups by public key assume a 48-byte BLS key, and `ValidatorPq` throws on its BLS getters. The validators, balances, identities and builders endpoints, duties, `register_validator` and rewards by id all depend on this.
2. **BLS-pinned JSON types.** Some endpoints serialize with a fixed phase 0, Electra or Gloas type instead of the milestone's schema:
   - validators and identities;
   - block attestations, attester and proposer slashings, headers, and Teku's all-blocks-at-slot;
   - proposer preferences, bids, payload attestations, and the event payloads.
3. **Operations PQ removed.** These are sync committee messages, contributions and duties, the light client, voluntary exits and BLS changes. Some of their endpoints still accept requests or return 500 at PQ slots.
4. **The validator contract.** PQ changes what the VC and the BN exchange:
   - single attestations and aggregates carry XMSS signatures and a STARK proof;
   - aggregators come from the aggregation committee, so there's no selection proof;
   - the block's RANDAO reveal is a VRF proof (32 × 32 bytes);
   - payload attestation messages, preferences and envelopes are XMSS-signed.
5. **OpenAPI.** Every PQ-typed component needs a name that doesn't clash with an earlier milestone's. The EIP-8483 work shows how: pass `SchemaTypes.getSchemaName(milestone)` to the schema creators.

No beacon-APIs work for PQ exists upstream yet. Until a cross-client draft does, the validator-facing endpoints in 8c are Teku's own devnet contract, kept as close to the existing endpoints as possible.

## Slices

### 8a: validator keys (#11682)

- `ValidatorIndexCache` looks validators up by XMSS public key, with its own cache. The cache is shared by every branch's states, so a cached index is only used once the key at that index is checked. A BLS-key lookup in a PQ state finds nothing instead of throwing.
- `BeaconStateAccessors.getValidatorPubKey` returns empty for PQ validators. `BeaconStateAccessorsPq.getValidatorIndexByXmssKey`, used by deposit and preregistration processing, goes through the cache instead of scanning.
- `Spec.getValidatorIndexByXmssKey` and `getBuilderIndexByXmssKey`.
- `Validator.getSigningKeyBytes()`: the BLS key, or a PQ validator's XMSS key.
- REST:
  - validator ids accept a 32-byte XMSS key as well as a 48-byte BLS key (validators, balances, rewards), and so do builder ids;
  - the `validator` JSON (`StateValidator`) carries either key in `pubkey`, plus `vrf_root` for PQ validators, and parses back to the right class;
  - the builders endpoint serializes `BuilderPq` with its own type.

### 8b-1: read endpoints

- **Identities:** `StateValidatorIdentityPq` with a 32-byte key. The provider builds whichever list matches the state, and the response is a one-of on the list's schema, for JSON and SSZ alike.
- **PQ types beside the BLS-pinned ones**, added only where the network schedules PQ:
  - block attestations: `AttestationPq`, which has no signature;
  - attester slashings V2, GET and POST: the PQ indexed attestations with their `proof`;
  - proposer slashings, GET and POST: `ProposerSlashing.SSZ_SCHEMA_PQ`. The v1 POST carries no consensus version, so the type is picked by the length of the first header's signature;
  - headers: the XMSS-signed header type;
  - Teku's all-blocks-at-slot: the block's signature as hex, whichever kind it is.

### 8b-2: events and removed operations

- **Events:**
  - PQ single attestations stream on `single_attestation`. `SingleAttestationEvent` uses the attestation's own schema, so it no longer converts to the BLS `SingleAttestation`, which threw for them.
  - Bid, preferences and payload attestation events report the milestone at their slot, instead of always `gloas`.
  - **Not yet:** PQ payload attestation messages on the event stream. The pool hands them to fork choice only, and an event would need the `PayloadAttestationMessagePq` rebuilt with its XMSS signature.
- **Removed operations:**
  - Voluntary exits and BLS changes are rejected by their validators once the chain is in PQ, with a reason naming the fork. That covers REST and the local pools.
  - Sync committee subscriptions no longer 500 for a period running into PQ: they use the current slot's sync committee helpers, and duties wholly in PQ have nothing to subscribe to.
  - Sync committee rewards for a PQ block are a 400 naming the fork. Block rewards count no sync aggregate reward for a block without one.
  - Sync committee messages and contributions were already rejected by their validators, and their gossip topics are gone (phase 7).

### 8c: the validator contract

- **Attestations:**
  - `POST pool/attestations` takes `SingleAttestationPq`, through a `SchemaDefinitionsPq` getter.
  - `GET aggregate_attestation` returns the aggregate with its proof.
  - `POST aggregate_and_proofs` takes `SignedAggregateAndProofPq`.
  - `ValidatorApiChannel` carries the PQ types.
- **Duties:**
  - attester duties say whether the validator is in the aggregation committee for its slot;
  - duty pubkeys are XMSS keys for PQ.
- **Blocks:** `POST v4 blocks` accepts a VRF proof as `randao_reveal` for PQ slots, and `BlockFactory` uses it.
- **ePBS:** payload attestation messages, proposer preferences, bids and envelopes take their XMSS-signed PQ types.

This needs the most care, because the VC (9c) builds on it. A short contract note, with request and response shapes and the headers that select PQ, goes with it for other clients.

### 8d: OpenAPI and docs

- Regenerate the snapshots that 8b and 8c change, renaming components that clash.
- Note the PQ contract in the REST API docs.

## Testing

- Unit tests per handler or provider, against a PQ state or spec (`TestSpecFactory.createMinimalPq`).
- `OpenApiIntegrationTest` snapshots.
- Once 8c and 9c are both in, a VC talking to a BN over REST across the PQ fork.
