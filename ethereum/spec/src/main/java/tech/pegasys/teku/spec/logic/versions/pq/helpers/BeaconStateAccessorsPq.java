/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.logic.versions.pq.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.PqConstants.AGGREGATORS_PER_COMMITTEE;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPq;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPqSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

public class BeaconStateAccessorsPq extends BeaconStateAccessorsGloas {

  private final SpecConfigPq configPq;
  private final PredicatesPq predicatesPq;
  private final MiscHelpersPq miscHelpersPq;
  private final SchemaDefinitionsPq schemaDefinitionsPq;

  public BeaconStateAccessorsPq(
      final SpecConfigPq config,
      final SchemaDefinitionsPq schemaDefinitions,
      final PredicatesPq predicates,
      final MiscHelpersPq miscHelpers) {
    super(config, schemaDefinitions, predicates, miscHelpers);
    this.configPq = config;
    this.predicatesPq = predicates;
    this.miscHelpersPq = miscHelpers;
    this.schemaDefinitionsPq = schemaDefinitions;
  }

  /** get_committee_count_per_slot, modified in PQ: at most X_MAX_COMMITTEE_COUNT_PER_SLOT. */
  @Override
  public UInt64 getCommitteeCountPerSlot(final long activeValidatorCount) {
    return UInt64.valueOf(
        Math.max(
            1L,
            Math.min(
                configPq.getXMaxCommitteeCountPerSlot(),
                Math.floorDiv(
                    Math.floorDiv(activeValidatorCount, configPq.getSlotsPerEpoch()),
                    configPq.getTargetCommitteeSize()))));
  }

  /** In PQ the timely head flag needs an inclusion delay of exactly 2. */
  @Override
  protected UInt64 getTimelyHeadInclusionDelay() {
    return UInt64.valueOf(configPq.getMinAttestationInclusionDelayPq());
  }

  /**
   * With an inclusion delay of two slots the attested block may be the grandparent of the block
   * being processed, so its payload availability is read at its own slot.
   */
  @Override
  protected UInt64 getPayloadAvailabilitySlot(
      final BeaconState state, final AttestationData data, final UInt64 parentSlot) {
    return getLatestBlockSlot(state, data.getSlot(), parentSlot);
  }

  /**
   * get_latest_block_slot: the slot of the latest block at or before {@code slot}, where {@code
   * parentSlot} is the slot of the parent of the block being processed. Block roots are kept for
   * SLOTS_PER_HISTORICAL_ROOT slots, so the search stops at the oldest one kept.
   */
  public UInt64 getLatestBlockSlot(
      final BeaconState state, final UInt64 slot, final UInt64 parentSlot) {
    if (parentSlot.isLessThanOrEqualTo(slot)) {
      return parentSlot;
    }
    final Bytes32 blockRoot = getBlockRootAtSlot(state, slot);
    UInt64 latestSlot = slot;
    while (latestSlot.isGreaterThan(UInt64.ZERO)
        && latestSlot.plus(configPq.getSlotsPerHistoricalRoot()).isGreaterThan(state.getSlot())
        && getBlockRootAtSlot(state, latestSlot.minus(UInt64.ONE)).equals(blockRoot)) {
      latestSlot = latestSlot.minus(UInt64.ONE);
    }
    return latestSlot;
  }

  /**
   * compute_aggregation_committee: up to AGGREGATORS_PER_COMMITTEE members of the beacon committee
   * at {@code slot} for {@code index}, sampled by effective balance without duplicates.
   */
  public IntList computeAggregationCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Bytes32 seed =
        Hash.sha256(
            Bytes.concatenate(
                getSeed(state, epoch, Domain.SELECTION_PROOF),
                uint64ToBytes(slot),
                uint64ToBytes(index)));
    // No validation (state within range): the window is filled during epoch processing, before the
    // next slot is set but after the randao mix is reset, as for compute_ptc
    IntList candidates = new IntArrayList(getBeaconCommitteeNoValidation(state, slot, index));
    final IntList aggregators = new IntArrayList();
    while (aggregators.size() < AGGREGATORS_PER_COMMITTEE && !candidates.isEmpty()) {
      final int aggregator =
          miscHelpersPq
              .computeBalanceWeightedSelection(
                  state,
                  candidates,
                  Hash.sha256(Bytes.concatenate(seed, uint64ToBytes(aggregators.size()))),
                  1,
                  false)
              .getInt(0);
      aggregators.add(aggregator);
      final IntList remaining = new IntArrayList(candidates.size() - 1);
      candidates.forEach(
          candidate -> {
            if (candidate != aggregator) {
              remaining.add(candidate);
            }
          });
      candidates = remaining;
    }
    return aggregators;
  }

  /** compute_aggregation_committees: the aggregation committee of each committee of the slot. */
  public List<IntList> computeAggregationCommittees(final BeaconState state, final UInt64 slot) {
    final int committeesPerSlot =
        getCommitteeCountPerSlot(state, miscHelpers.computeEpochAtSlot(slot)).intValue();
    final List<IntList> committees = new ArrayList<>(committeesPerSlot);
    for (int index = 0; index < committeesPerSlot; index++) {
      committees.add(computeAggregationCommittee(state, slot, UInt64.valueOf(index)));
    }
    return committees;
  }

  /**
   * get_aggregation_committee: read from the state's window, which covers the previous epoch
   * through MIN_SEED_LOOKAHEAD epochs ahead.
   */
  public IntList getAggregationCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final UInt64 stateEpoch = getCurrentEpoch(state);
    final int slotInEpoch = slot.mod(configPq.getSlotsPerEpoch()).intValue();
    final int windowIndex;
    if (epoch.isLessThan(stateEpoch)) {
      checkArgument(
          epoch.plus(1).equals(stateEpoch), "Epoch %s is before the previous epoch", epoch);
      windowIndex = slotInEpoch;
    } else {
      checkArgument(
          epoch.isLessThanOrEqualTo(stateEpoch.plus(configPq.getMinSeedLookahead())),
          "Epoch %s is beyond the seed lookahead",
          epoch);
      windowIndex =
          epoch.minus(stateEpoch).plus(1).times(configPq.getSlotsPerEpoch()).intValue()
              + slotInEpoch;
    }
    final SszList<SszUInt64> committee =
        BeaconStatePq.required(state)
            .getAggregationCommitteeWindow()
            .get(windowIndex)
            .get(index.intValue());
    final IntList aggregators = new IntArrayList(committee.size());
    committee.forEach(aggregator -> aggregators.add(aggregator.get().intValue()));
    return aggregators;
  }

  /**
   * get_attestation_statements: for each committee of the attestation, in order, the statement that
   * the signatures of its attesters in that committee prove.
   */
  public List<XmssAggregateStatement> getAttestationStatements(
      final BeaconState state, final Attestation attestation) {
    final AttestationData data = attestation.getData();
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final List<XmssAggregateStatement> statements = new ArrayList<>();
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : attestation.getCommitteeIndicesRequired()) {
      final IntList committee = getBeaconCommittee(state, data.getSlot(), committeeIndex);
      final IntList attesters = new IntArrayList();
      for (int i = 0; i < committee.size(); i++) {
        if (aggregationBits.isSet(committeeOffset + i)) {
          attesters.add(committee.getInt(i));
        }
      }
      statements.add(getAttestationStatement(state, data, attesters));
      committeeOffset += committee.size();
    }
    return statements;
  }

  /**
   * The statement that the signatures of {@code attesters}, validator indices in committee order,
   * prove for {@code data}: one committee's entry in get_attestation_statements. It needs no
   * committee lookup, so {@code state} may be any state with these validators and the fork at the
   * attestation's target epoch.
   */
  public XmssAggregateStatement getAttestationStatement(
      final BeaconState state, final AttestationData data, final IntList attesters) {
    final UInt64 targetEpoch = data.getTarget().getEpoch();
    final Bytes32 domain =
        getDomain(
            Domain.BEACON_ATTESTER, targetEpoch, state.getFork(), state.getGenesisValidatorsRoot());
    final Bytes32 message = Bytes32.wrap(miscHelpers.computeSigningRoot(data, domain));
    final UInt64 leafIndex = miscHelpersPq.computeXmssAttestationLeafIndex(targetEpoch);
    final List<Bytes32> pubkeys = new ArrayList<>(attesters.size());
    attesters.forEach(
        attester ->
            pubkeys.add(((ValidatorPq) state.getValidators().get(attester)).getXmssPublicKey()));
    return new XmssAggregateStatement(pubkeys, leafIndex, message);
  }

  /**
   * get_indexed_attestation, modified in PQ: the indexed attestation carries the proof of its
   * attesters' signatures.
   */
  public IndexedAttestationPq getIndexedAttestation(
      final BeaconState state, final Attestation attestation, final Bytes proof) {
    final AttestationData data = attestation.getData();
    final SszBitlist aggregationBits = attestation.getAggregationBits();
    final IntList attesters = new IntArrayList();
    int committeeOffset = 0;
    for (final UInt64 committeeIndex : attestation.getCommitteeIndicesRequired()) {
      final IntList committee = getBeaconCommittee(state, data.getSlot(), committeeIndex);
      for (int i = 0; i < committee.size(); i++) {
        if (aggregationBits.isSet(committeeOffset + i)) {
          attesters.add(committee.getInt(i));
        }
      }
      committeeOffset += committee.size();
    }
    final IndexedAttestationPqSchema schema =
        schemaDefinitionsPq.getPqAttestationSchemas().getIndexedAttestationSchema();
    return schema.create(
        schema
            .getAttestingIndicesSchema()
            .of(attesters.intStream().distinct().sorted().mapToObj(UInt64::valueOf).toList()),
        data,
        proof);
  }

  /**
   * The index of the validator with XMSS public key {@code pubkey}, through the state's validator
   * index cache.
   */
  public Optional<Integer> getValidatorIndexByXmssKey(
      final BeaconState state, final Bytes32 pubkey) {
    return BeaconStateCache.getTransitionCaches(state)
        .getValidatorIndexCache()
        .getValidatorIndexByXmssKey(state, pubkey);
  }

  /** The index of the builder with XMSS public key {@code pubkey}. A linear scan for now. */
  public Optional<Integer> getBuilderIndexByXmssKey(final BeaconState state, final Bytes32 pubkey) {
    final SszList<?> builders = BeaconStatePq.required(state).getBuilders();
    for (int i = 0; i < builders.size(); i++) {
      if (((BuilderPq) builders.get(i)).getXmssPublicKey().equals(pubkey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  /** get_validator_preregistration_index */
  public Optional<Integer> getValidatorPreregistrationIndex(
      final BeaconState state, final Bytes32 pubkey) {
    final SszList<ValidatorPreregistration> preregistrations =
        BeaconStatePq.required(state).getValidatorPreregistrations();
    for (int i = 0; i < preregistrations.size(); i++) {
      if (preregistrations.get(i).getXmssPublicKey().equals(pubkey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  /** get_active_validator_preregistration */
  public Optional<ValidatorPreregistration> getActiveValidatorPreregistration(
      final BeaconState state, final Bytes32 pubkey) {
    return getValidatorPreregistrationIndex(state, pubkey)
        .map(index -> BeaconStatePq.required(state).getValidatorPreregistrations().get(index))
        .filter(
            preregistration ->
                predicatesPq.isActiveValidatorPreregistration(state, preregistration));
  }

  /** get_builder_preregistration_index */
  public Optional<Integer> getBuilderPreregistrationIndex(
      final BeaconState state, final Bytes32 pubkey) {
    final SszList<BuilderPreregistration> preregistrations =
        BeaconStatePq.required(state).getBuilderPreregistrations();
    for (int i = 0; i < preregistrations.size(); i++) {
      if (preregistrations.get(i).getXmssPublicKey().equals(pubkey)) {
        return Optional.of(i);
      }
    }
    return Optional.empty();
  }

  /** get_active_builder_preregistration */
  public Optional<BuilderPreregistration> getActiveBuilderPreregistration(
      final BeaconState state, final Bytes32 pubkey) {
    return getBuilderPreregistrationIndex(state, pubkey)
        .map(index -> BeaconStatePq.required(state).getBuilderPreregistrations().get(index))
        .filter(
            preregistration -> predicatesPq.isActiveBuilderPreregistration(state, preregistration));
  }
}
