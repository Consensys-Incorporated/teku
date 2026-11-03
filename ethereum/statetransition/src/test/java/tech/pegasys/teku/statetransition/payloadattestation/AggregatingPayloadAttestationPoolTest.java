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

package tech.pegasys.teku.statetransition.payloadattestation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.payloadattestation.AggregatingPayloadAttestationPool.PAYLOAD_ATTESTATION_RETENTION_SLOTS;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.BLSTestUtil.NegatedKeyPairs;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

class AggregatingPayloadAttestationPoolTest {

  private static final NegatedKeyPairs KEYS = BLSTestUtil.negatedKeyPairs(1);

  private final Spec spec = TestSpecFactory.createMainnetGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final PayloadAttestationMessageGossipValidator validator =
      mock(PayloadAttestationMessageGossipValidator.class);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestations =
      new PoolFactory(metricsSystem).createPendingPoolForPayloadAttestations(spec, 10_000);

  private final SchemaDefinitionsGloas schemaDefinitionsGloas =
      SchemaDefinitionsGloas.required(
          spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions());

  private final AggregatingPayloadAttestationPool payloadAttestationPool =
      new AggregatingPayloadAttestationPool(
          spec, validator, pendingPayloadAttestations, metricsSystem);

  @BeforeEach
  public void setUp() {
    when(validator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
  }

  @Test
  public void doesNotAddIfMessageIsNotAccepted() {
    when(validator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("invalid")));

    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage();

    SafeFutureAssert.safeJoin(payloadAttestationPool.addLocal(payloadAttestationMessage));

    assertThat(getPoolSizeFromMetric()).isZero();
  }

  @Test
  public void addingLocallyAcceptedMessageNotifiesSubscribers() {
    final List<PayloadAttestationMessage> addedMessages = new ArrayList<>();

    payloadAttestationPool.subscribeOperationAdded(
        (message, validationStatus, fromNetwork) -> addedMessages.add(message));

    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage();

    SafeFutureAssert.safeJoin(payloadAttestationPool.addLocal(payloadAttestationMessage));

    assertThat(getPoolSizeFromMetric()).isOne();
    assertThat(addedMessages).containsExactly(payloadAttestationMessage);
  }

  @Test
  public void addingLocallyAcceptedMessageNotifiesValidatedSubscribers() {
    final List<ValidatablePayloadAttestationMessage> addedMessages = new ArrayList<>();

    payloadAttestationPool.subscribeValidatedOperationAdded(
        (message, validationStatus, fromNetwork) -> addedMessages.add(message));

    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage();

    SafeFutureAssert.safeJoin(payloadAttestationPool.addLocal(payloadAttestationMessage));

    assertThat(getPoolSizeFromMetric()).isOne();
    assertThat(addedMessages).hasSize(1);
    assertThat(addedMessages.get(0).getMessage()).isEqualTo(payloadAttestationMessage);
  }

  @Test
  public void addingSaveForFutureMessageAddsToPendingPoolAndProcessesItOnBlockImported() {

    when(validator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.SAVE_FOR_FUTURE));

    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    final PayloadAttestationData data =
        schemaDefinitionsGloas
            .getPayloadAttestationDataSchema()
            .create(block.getRoot(), block.getSlot(), true, true);

    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage(data);

    pendingPayloadAttestations.onSlot(block.getSlot());
    pendingPayloadAttestations.onNewFinalizedCheckpoint(
        new Checkpoint(
            spec.computeEpochAtSlot(block.getSlot()).minus(1), dataStructureUtil.randomBytes32()),
        false);

    SafeFutureAssert.safeJoin(
        payloadAttestationPool.addRemote(payloadAttestationMessage, Optional.empty()));

    assertThat(getPoolSizeFromMetric()).isZero();
    assertThat(pendingPayloadAttestations.size()).isOne();
    assertThat(pendingPayloadAttestations.getItemsDependingOn(block.getRoot(), true))
        .containsExactly(payloadAttestationMessage);

    final List<PayloadAttestationMessage> addedMessages = new ArrayList<>();

    payloadAttestationPool.subscribeOperationAdded(
        (message, validationStatus, fromNetwork) -> addedMessages.add(message));

    // payload attestation is accepted on next attempt
    when(validator.validate(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    // pending payload attestation is processed on block import
    payloadAttestationPool.onBlockImported(block, false);

    assertThat(getPoolSizeFromMetric()).isOne();
    assertThat(pendingPayloadAttestations.size()).isZero();
    assertThat(addedMessages).containsExactly(payloadAttestationMessage);
  }

  @Test
  public void getsAggregatedPayloadAttestationsForBlock() {
    final UInt64 blockSlot = dataStructureUtil.randomSlot();
    final int validatorCount = 8192;
    final BeaconState state =
        dataStructureUtil.randomBeaconStateWithActiveValidatorsAndInitialisedPtcWindow(
            validatorCount, blockSlot);

    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = blockSlot.minusMinZero(1);

    final IntList ptc = spec.getPtc(state, slot);

    final PayloadAttestationData payloadPresentData =
        createPayloadAttestationData(parentRoot, slot, true);
    final PayloadAttestationData payloadAbsentData =
        createPayloadAttestationData(parentRoot, slot, false);

    // ~80% vote for payload to be present
    // ~20% vote for payload to be absent
    final List<Integer> payloadPresentVoters = new ArrayList<>();
    final List<Integer> payloadAbsentVoters = new ArrayList<>();
    ptc.intStream()
        .distinct()
        .forEach(
            validatorIndex -> {
              final boolean voteForPayloadPresent = Math.random() < 0.8;
              final PayloadAttestationMessage payloadAttestationMessage =
                  createPayloadAttestationMessage(
                      UInt64.valueOf(validatorIndex),
                      voteForPayloadPresent ? payloadPresentData : payloadAbsentData);
              if (voteForPayloadPresent) {
                payloadPresentVoters.add(validatorIndex);
              } else {
                payloadAbsentVoters.add(validatorIndex);
              }
              SafeFutureAssert.safeJoin(
                  payloadAttestationPool.addRemote(payloadAttestationMessage, Optional.empty()));
            });

    // Not applicable votes
    final PayloadAttestationData differentSlotData =
        createPayloadAttestationData(parentRoot, slot.minus(1), true);
    final PayloadAttestationData differentBeaconBlockRootData =
        createPayloadAttestationData(dataStructureUtil.randomBytes32(), slot, true);
    SafeFutureAssert.safeJoin(
        payloadAttestationPool.addRemote(
            createPayloadAttestationMessage(UInt64.ZERO, differentSlotData), Optional.empty()));
    SafeFutureAssert.safeJoin(
        payloadAttestationPool.addRemote(
            createPayloadAttestationMessage(UInt64.valueOf(1), differentBeaconBlockRootData),
            Optional.empty()));

    assertThat(getPoolSizeFromMetric())
        .isEqualTo(payloadPresentVoters.size() + payloadAbsentVoters.size() + 2);

    final SszList<PayloadAttestation> payloadAttestations =
        payloadAttestationPool.getPayloadAttestationsForBlock(state, parentRoot);

    assertThat(payloadAttestations).hasSize(2);

    // more validators voted, so it should be first in the list
    assertThat(payloadAttestations.get(0).getData()).isEqualTo(payloadPresentData);
    assertThat(
            payloadAttestations
                .get(0)
                .getAggregationBits()
                .streamAllSetBits()
                .map(ptc::getInt)
                .distinct())
        .containsExactlyInAnyOrderElementsOf(payloadPresentVoters);

    assertThat(payloadAttestations.get(1).getData()).isEqualTo(payloadAbsentData);
    assertThat(
            payloadAttestations
                .get(1)
                .getAggregationBits()
                .streamAllSetBits()
                .map(ptc::getInt)
                .distinct())
        .containsExactlyInAnyOrderElementsOf(payloadAbsentVoters);
  }

  @Test
  public void getsEmptyPayloadAttestationsForBlockIfNoAttestationsToAggregate() {
    final BeaconState state =
        dataStructureUtil.randomBeaconStateWithActiveValidators(32, dataStructureUtil.randomSlot());
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();

    final SszList<PayloadAttestation> payloadAttestations =
        payloadAttestationPool.getPayloadAttestationsForBlock(state, parentRoot);

    assertThat(payloadAttestations).isEmpty();
  }

  @Test
  public void removesOldPayloadAttestations() {
    final PayloadAttestationMessage payloadAttestationMessage =
        dataStructureUtil.randomPayloadAttestationMessage();

    SafeFutureAssert.safeJoin(
        payloadAttestationPool.addRemote(payloadAttestationMessage, Optional.empty()));

    assertThat(getPoolSizeFromMetric()).isOne();

    payloadAttestationPool.onSlot(
        payloadAttestationMessage
            .getData()
            .getSlot()
            .plus(PAYLOAD_ATTESTATION_RETENTION_SLOTS + 1));

    assertThat(getPoolSizeFromMetric()).isZero();
  }

  @Test
  public void doesNotIncludePayloadAttestationWithInfinitySignatureInBlock() {
    final UInt64 blockSlot = dataStructureUtil.randomSlot();
    final BeaconState state =
        dataStructureUtil.randomBeaconStateWithActiveValidatorsAndInitialisedPtcWindow(
            8192, blockSlot);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = blockSlot.minusMinZero(1);
    final List<Integer> voters = selectPtcMembersAppearingOnce(spec.getPtc(state, slot), 3);

    final PayloadAttestationData payloadPresentData =
        createPayloadAttestationData(parentRoot, slot, true);
    final PayloadAttestationData payloadAbsentData =
        createPayloadAttestationData(parentRoot, slot, false);

    // the larger group aggregates to the point at infinity
    addSignedPayloadAttestationMessage(voters.get(0), payloadPresentData, KEYS.p());
    addSignedPayloadAttestationMessage(voters.get(1), payloadPresentData, KEYS.minusP());
    // the smaller group is still valid
    addSignedPayloadAttestationMessage(voters.get(2), payloadAbsentData, KEYS.q());

    final SszList<PayloadAttestation> payloadAttestations =
        payloadAttestationPool.getPayloadAttestationsForBlock(state, parentRoot);

    assertThat(payloadAttestations).hasSize(1);
    assertThat(payloadAttestations.get(0).getData()).isEqualTo(payloadAbsentData);
    assertThat(payloadAttestationPool.getPayloadAttestations(state))
        .extracting(PayloadAttestation::getData)
        .containsExactly(payloadAbsentData);
  }

  private List<Integer> selectPtcMembersAppearingOnce(final IntList ptc, final int count) {
    // a validator appearing multiple times in the PTC has its signature aggregated multiple times
    final List<Integer> members =
        ptc.intStream()
            .filter(
                validatorIndex -> ptc.indexOf(validatorIndex) == ptc.lastIndexOf(validatorIndex))
            .limit(count)
            .boxed()
            .toList();
    assertThat(members).hasSize(count);
    return members;
  }

  private void addSignedPayloadAttestationMessage(
      final int validatorIndex, final PayloadAttestationData data, final BLSKeyPair keyPair) {
    // the pool never verifies signatures; any message shared by all participants will do
    final BLSSignature signature = BLS.sign(keyPair.getSecretKey(), data.hashTreeRoot());
    SafeFutureAssert.safeJoin(
        payloadAttestationPool.addRemote(
            createPayloadAttestationMessage(UInt64.valueOf(validatorIndex), data, signature),
            Optional.empty()));
  }

  private int getPoolSizeFromMetric() {
    return (int)
        metricsSystem
            .getGauge(TekuMetricCategory.BEACON, "payload_attestation_pool_size")
            .getValue();
  }

  private PayloadAttestationData createPayloadAttestationData(
      final Bytes32 beaconBlockRoot, final UInt64 slot, final boolean payloadPresent) {
    return schemaDefinitionsGloas
        .getPayloadAttestationDataSchema()
        .create(beaconBlockRoot, slot, payloadPresent, true);
  }

  private PayloadAttestationMessage createPayloadAttestationMessage(
      final UInt64 validatorIndex, final PayloadAttestationData data) {
    return createPayloadAttestationMessage(
        validatorIndex, data, dataStructureUtil.randomSignature());
  }

  private PayloadAttestationMessage createPayloadAttestationMessage(
      final UInt64 validatorIndex,
      final PayloadAttestationData data,
      final BLSSignature signature) {
    return schemaDefinitionsGloas
        .getPayloadAttestationMessageSchema()
        .create(validatorIndex, data, signature);
  }
}
