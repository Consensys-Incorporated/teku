/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.logic.common.helpers.MathHelpers.uint64ToBytes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.PayloadTimelinessCommitteeWindowSchema;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateAccessorsGloas extends BeaconStateAccessorsFulu {

  private final SpecConfigGloas configGloas;
  private final MiscHelpersGloas miscHelpersGloas;
  private final SchemaDefinitionsGloas schemaDefinitions;

  public static BeaconStateAccessorsGloas required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsGloas,
        "Expected %s but it was %s",
        BeaconStateAccessorsGloas.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsGloas) beaconStateAccessors;
  }

  public BeaconStateAccessorsGloas(
      final SpecConfigGloas config,
      final SchemaDefinitionsGloas schemaDefinitions,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers) {
    super(config, predicates, miscHelpers);
    this.configGloas = config;
    this.miscHelpersGloas = miscHelpers;
    this.schemaDefinitions = schemaDefinitions;
  }

  /**
   * EIP-8045: `get_beacon_proposer_indices` is modified to exclude slashed validators from the
   * candidate pool before invoking `compute_proposer_indices`, so the `proposer_lookahead` only
   * contains active and unslashed validators.
   */
  @Override
  public List<Integer> getBeaconProposerIndices(final BeaconState state, final UInt64 epoch) {
    final IntList indices =
        getActiveValidatorIndices(state, epoch)
            .intStream()
            .filter(index -> !state.getValidators().get(index).isSlashed())
            .collect(IntArrayList::new, IntList::add, IntList::addAll);
    final Bytes32 seed = getSeed(state, epoch, Domain.BEACON_PROPOSER);
    return miscHelpers.computeProposerIndices(state, epoch, seed, indices);
  }

  /**
   * EIP-8061: scaled balance churn limit using the supplied quotient. Returns
   * max(MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA, total_active_balance // quotient), rounded down to
   * EFFECTIVE_BALANCE_INCREMENT.
   */
  private UInt64 computeBalanceChurnLimit(final BeaconStateElectra state, final UInt64 quotient) {
    final UInt64 churn =
        configElectra
            .getMinPerEpochChurnLimitElectra()
            .max(getTotalActiveBalance(state).dividedBy(quotient));
    return churn.minusMinZero(churn.mod(configElectra.getEffectiveBalanceIncrement()));
  }

  /**
   * get_activation_churn_limit
   *
   * <p>EIP-8061: capped activation-only churn limit. Used when admitting validators from the
   * pending-deposit queue.
   */
  public UInt64 getActivationChurnLimit(final BeaconStateElectra state) {
    return computeBalanceChurnLimit(state, UInt64.valueOf(configGloas.getChurnLimitQuotientGloas()))
        .min(configGloas.getMaxPerEpochActivationChurnLimitGloas());
  }

  /**
   * get_exit_churn_limit
   *
   * <p>EIP-8061: uncapped exit churn limit. Used when scheduling validator exits.
   */
  public UInt64 getExitChurnLimit(final BeaconStateElectra state) {
    return computeBalanceChurnLimit(
        state, UInt64.valueOf(configGloas.getChurnLimitQuotientGloas()));
  }

  /**
   * get_consolidation_churn_limit
   *
   * <p>EIP-8061: consolidation churn is now derived independently from the activation/exit churn
   * via {@code CONSOLIDATION_CHURN_LIMIT_QUOTIENT}. Unlike the activation/exit churn limits, no
   * {@code MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA} floor is applied.
   */
  @Override
  public UInt64 getConsolidationChurnLimit(final BeaconStateElectra state) {
    final UInt64 churn =
        getTotalActiveBalance(state).dividedBy(configGloas.getConsolidationChurnLimitQuotient());
    return churn.minusMinZero(churn.mod(configElectra.getEffectiveBalanceIncrement()));
  }

  public UInt64 getPendingBalanceToWithdrawForBuilder(
      final BeaconState state, final UInt64 builderIndex) {
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);
    final UInt64 pendingBuilderWithdrawalsBalance =
        stateGloas.getBuilderPendingWithdrawals().stream()
            .filter(withdrawal -> withdrawal.getBuilderIndex().equals(builderIndex))
            .map(BuilderPendingWithdrawal::getAmount)
            .reduce(UInt64.ZERO, UInt64::plus);
    final UInt64 pendingBuilderPaymentsBalance =
        stateGloas.getBuilderPendingPayments().stream()
            .map(BuilderPendingPayment::getWithdrawal)
            .filter(withdrawal -> withdrawal.getBuilderIndex().equals(builderIndex))
            .map(BuilderPendingWithdrawal::getAmount)
            .reduce(UInt64.ZERO, UInt64::plus);
    return pendingBuilderWithdrawalsBalance.plus(pendingBuilderPaymentsBalance);
  }

  public boolean canBuilderCoverBid(
      final BeaconState state, final UInt64 builderIndex, final UInt64 bidAmount) {
    final UInt64 builderBalance =
        BeaconStateGloas.required(state).getBuilders().get(builderIndex.intValue()).getBalance();
    final UInt64 pendingWithdrawalsAmount =
        getPendingBalanceToWithdrawForBuilder(state, builderIndex);
    final UInt64 minBalance = config.getMinDepositAmount().plus(pendingWithdrawalsAmount);
    if (builderBalance.isLessThan(minBalance)) {
      return false;
    }
    return builderBalance.minusMinZero(minBalance).isGreaterThanOrEqualTo(bidAmount);
  }

  /**
   * get_indexed_payload_attestation
   *
   * <p>Return the indexed payload attestation corresponding to ``payload_attestation``.
   */
  public IndexedPayloadAttestationLight getIndexedPayloadAttestation(
      final BeaconState state, final PayloadAttestation payloadAttestation) {
    final UInt64 slot = payloadAttestation.getData().getSlot();
    final IntList ptc = getPtc(state, slot);
    final SszBitvector aggregationBits = payloadAttestation.getAggregationBits();
    final List<UInt64> attestingIndices = new ArrayList<>();
    for (int i = 0; i < ptc.size(); i++) {
      if (aggregationBits.isSet(i)) {
        final int index = ptc.getInt(i);
        attestingIndices.add(UInt64.valueOf(index));
      }
    }
    attestingIndices.sort(UInt64::compareTo);

    return new IndexedPayloadAttestationLight(
        attestingIndices, payloadAttestation.getData(), payloadAttestation.getSignature());
  }

  /**
   * compute_ptc
   *
   * <p>Get the payload timeliness committee for the given ``slot``
   */
  public SszUInt64Vector computePtc(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Bytes32 seed =
        Hash.sha256(
            Bytes.concatenate(getSeed(state, epoch, Domain.PTC_ATTESTER), uint64ToBytes(slot)));
    final IntList indices = new IntArrayList();
    // Concatenate all committees for this slot in order
    UInt64.range(UInt64.ZERO, getCommitteeCountPerSlot(state, epoch))
        .forEach(
            i -> {
              // no validation (state within range) because we compute_ptc during epoch processing
              // before we have set the next slot, but after we have reset the randao
              final IntList committee = getBeaconCommitteeNoValidation(state, slot, i);
              indices.addAll(committee);
            });
    return miscHelpersGloas
        .computeBalanceWeightedSelection(
            state, indices, seed, configGloas.getPayloadTimelinessCommitteeSize(), false)
        .intStream()
        .mapToObj(index -> SszUInt64.of(UInt64.valueOf(index)))
        .collect(
            schemaDefinitions
                .getPtcWindowSchema()
                .getPayloadTimelinessCommitteeSchema()
                .collector());
  }

  /**
   * get_ptc
   *
   * <p>*Note*: `get_ptc` uses the cached `ptc_window` for lookups.
   */
  @Override
  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(slot);
    final Fork fork = state.getFork();
    // The previous-epoch PTC is all zeros at the Gloas fork, so pre-fork slots are not queryable
    if (fork.getCurrentVersion().equals(config.getGloasForkVersion())) {
      checkArgument(
          epoch.isGreaterThanOrEqualTo(fork.getEpoch()),
          "PTC for slot %s is not queryable because it is before the Gloas fork epoch %s",
          slot,
          fork.getEpoch());
    }
    final UInt64 stateEpoch = getCurrentEpoch(state);
    final int cacheIndex;
    if (epoch.isLessThan(stateEpoch)) {
      checkArgument(
          epoch.plus(1).equals(stateEpoch),
          "PTC for slot %s is not queryable from the state which is currently at epoch %s",
          slot,
          stateEpoch);
      cacheIndex = slot.mod(config.getSlotsPerEpoch()).intValue();
    } else {
      checkArgument(
          epoch.isLessThanOrEqualTo(stateEpoch.plus(config.getMinSeedLookahead())),
          "PTC for slot %s is not queryable from the state which is currently at epoch %s",
          slot,
          stateEpoch);
      final int offset =
          epoch.minusMinZero(stateEpoch).plus(1).times(config.getSlotsPerEpoch()).intValue();
      cacheIndex = slot.mod(config.getSlotsPerEpoch()).plus(offset).intValue();
    }
    return state
        .toVersionGloas()
        .map(stateGloas -> stateGloas.getPtcWindow().get(cacheIndex).toIntList())
        // Fork boundary edge case
        .orElseGet(() -> computePtc(state, slot).toIntList());
  }

  /**
   * initialize_ptc_window
   *
   * <p>Return the cached PTC window starting from the current epoch. Used to initialize the
   * ``ptc_window`` field in the beacon state at genesis and after forks.
   */
  public SszVector<SszUInt64Vector> initializePtcWindow(final BeaconState state) {
    final PayloadTimelinessCommitteeWindowSchema payloadTimelinessCommitteeWindowSchema =
        schemaDefinitions.getPtcWindowSchema();
    final List<SszUInt64Vector> emptyPreviousEpoch =
        Collections.nCopies(
            config.getSlotsPerEpoch(),
            payloadTimelinessCommitteeWindowSchema
                .getPayloadTimelinessCommitteeSchema()
                .of(
                    Collections.nCopies(
                        (int)
                            payloadTimelinessCommitteeWindowSchema
                                .getPayloadTimelinessCommitteeSchema()
                                .getMaxLength(),
                        UInt64.ZERO)));
    final List<SszUInt64Vector> ptcWindow = new ArrayList<>(emptyPreviousEpoch);
    final UInt64 currentEpoch = getCurrentEpoch(state);
    IntStream.range(0, 1 + config.getMinSeedLookahead())
        .forEach(
            e -> {
              final UInt64 epoch = currentEpoch.plus(e);
              final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
              for (int i = 0; i < config.getSlotsPerEpoch(); i++) {
                ptcWindow.add(computePtc(state, startSlot.plus(i)));
              }
            });
    return payloadTimelinessCommitteeWindowSchema.createFromElements(ptcWindow);
  }

  /**
   * get_builder_payment_quorum_threshold
   *
   * <p>Calculate the quorum threshold for builder payments.
   */
  public UInt64 getBuilderPaymentQuorumThreshold(final BeaconState state) {
    final UInt64 perSlotBalance = getTotalActiveBalance(state).dividedBy(config.getSlotsPerEpoch());
    final UInt64 quorum = perSlotBalance.times(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_NUMERATOR);
    return quorum.dividedBy(SpecConfigGloas.BUILDER_PAYMENT_THRESHOLD_DENOMINATOR);
  }

  /**
   * is_attestation_same_slot
   *
   * <p>Check if the attestation is for the block proposed at the attestation slot.
   */
  public boolean isAttestationSameSlot(final BeaconState state, final AttestationData data) {
    if (data.getSlot().isZero()) {
      return true;
    }
    final Bytes32 blockRoot = data.getBeaconBlockRoot();
    final Bytes32 slotBlockRoot = getBlockRootAtSlot(state, data.getSlot());
    final Bytes32 prevBlockRoot = getBlockRootAtSlot(state, data.getSlot().minusMinZero(1));

    return blockRoot.equals(slotBlockRoot) && !blockRoot.equals(prevBlockRoot);
  }

  @Override
  protected boolean computeIsMatchingHead(
      final boolean isMatchingTarget,
      final boolean headRootMatches,
      final AttestationData data,
      final BeaconState state,
      final UInt64 parentSlot) {
    final boolean isAttestationSameSlot = isAttestationSameSlot(state, data);
    if (isAttestationSameSlot) {
      checkArgument(data.getIndex().isZero(), "Index must be set to zero");
    }

    if (!isMatchingTarget || !headRootMatches) {
      return false;
    }

    if (isAttestationSameSlot) {
      return true;
    }

    final int slotIndex = parentSlot.mod(config.getSlotsPerHistoricalRoot()).intValue();
    final boolean payloadIndex =
        BeaconStateGloas.required(state).getExecutionPayloadAvailability().get(slotIndex).get();
    return data.getIndex().intValue() == (payloadIndex ? 1 : 0);
  }

  /**
   * get_next_sync_committee_indices is refactored to use compute_balance_weighted_selection as a
   * helper for the balance-weighted sampling process.
   */
  @Override
  public IntList getNextSyncCommitteeIndices(final BeaconState state) {
    final UInt64 epoch = getCurrentEpoch(state).plus(1);
    final IntList activeValidatorIndices = getActiveValidatorIndices(state, epoch);
    final int activeValidatorCount = activeValidatorIndices.size();
    checkArgument(activeValidatorCount > 0, "Provided state has no active validators");
    final Bytes32 seed = getSeed(state, epoch, Domain.SYNC_COMMITTEE);
    return miscHelpersGloas.computeBalanceWeightedSelection(
        state, activeValidatorIndices, seed, configElectra.getSyncCommitteeSize(), true);
  }

  public UInt64 getIndexForNewBuilder(final BeaconState state) {
    final SszList<Builder> builders = BeaconStateGloas.required(state).getBuilders();
    for (int index = 0; index < builders.size(); index++) {
      final Builder builder = builders.get(index);
      if (builder.getWithdrawableEpoch().isLessThanOrEqualTo(getCurrentEpoch(state))
          && builder.getBalance().isZero()) {
        return UInt64.valueOf(index);
      }
    }
    return UInt64.valueOf(builders.size());
  }

  @Override
  public Optional<BLSPublicKey> getBuilderPubKey(
      final BeaconState state, final UInt64 builderIndex) {
    final Optional<SszList<Builder>> maybeBuilders =
        state.toVersionGloas().map(BeaconStateGloas::getBuilders);
    if (maybeBuilders.isEmpty()) {
      return Optional.empty();
    }
    final SszList<Builder> builders = maybeBuilders.get();
    if (builderIndex.isGreaterThanOrEqualTo(builders.size()) || builderIndex.longValue() < 0) {
      return Optional.empty();
    }
    return Optional.of(
        BeaconStateCache.getTransitionCaches(state)
            .getBuildersPubKeys()
            .get(
                builderIndex,
                i -> {
                  final BLSPublicKey pubKey = builders.get(i.intValue()).getPublicKey();
                  // eagerly pre-cache pubKey => builderIndex mapping
                  BeaconStateCache.getTransitionCaches(state)
                      .getBuilderIndexCache()
                      .invalidateWithNewValue(pubKey, i.intValue());
                  return pubKey;
                }));
  }

  @Override
  public Optional<Integer> getBuilderIndex(final BeaconState state, final BLSPublicKey publicKey) {
    return BeaconStateCache.getTransitionCaches(state)
        .getBuilderIndexCache()
        .getBuilderIndex(state, publicKey);
  }
}
