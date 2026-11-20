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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_PTC_ATTESTER;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.HashMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.pq.PayloadAttestationMessagePq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.PqTestKeys;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/** PQ payload attestation messages, which PTC members sign with XMSS. */
class PayloadAttestationMessagePqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  /**
   * Test signatures are stubs holding only their leaf index, so this verifier accepts a signature
   * when it was made at the expected leaf.
   */
  private static final XmssSignatureVerifier LEAF_CHECKING_VERIFIER =
      (publicKey, leafIndex, message, signature) ->
          XmssSignatureVerifier.getLeafIndex(signature).equals(leafIndex);

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<ValidatablePayloadAttestationMessage> validatedSubscriber =
      mock(OperationAddedSubscriber.class);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<PayloadAttestationMessage> subscriber =
      mock(OperationAddedSubscriber.class);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<PayloadAttestationMessagePq> pqSubscriber =
      mock(OperationAddedSubscriber.class);

  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private PayloadAttestationMessageGossipValidator validator;
  private AggregatingPayloadAttestationPool pool;

  @BeforeEach
  void setUp() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    recentChainData = storageSystem.recentChainData();
    validator =
        new PayloadAttestationMessageGossipValidator(
            spec,
            new GossipValidationHelper(
                spec, recentChainData, storageSystem.getMetricsSystem(), LEAF_CHECKING_VERIFIER),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP),
            new HashMap<>());
    pool =
        new AggregatingPayloadAttestationPool(
            spec,
            validator,
            new PoolFactory(metricsSystem).createPendingPoolForPayloadAttestations(spec, 100),
            metricsSystem);
    pool.subscribeValidatedOperationAdded(validatedSubscriber);
    pool.subscribeOperationAdded(subscriber);
    pool.subscribePqOperationAdded(pqSubscriber);
    // Run the chain into the PQ fork, with the current slot at its head
    storageSystem
        .chainUpdater()
        .updateBestBlock(
            storageSystem.chainUpdater().advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH)));
    storageSystem.chainUpdater().setCurrentSlot(recentChainData.getHeadSlot());
  }

  @Test
  void acceptsMessageSignedAtThePtcAttesterLeaf() {
    final PayloadAttestationMessagePq message = message(headData(), ptcAttesterLeaf());
    assertThat(spec.atSlot(message.getData().getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    final ValidatablePayloadAttestationMessage validatable =
        ValidatablePayloadAttestationMessage.fromNetworkPq(spec, message);

    assertThat(validator.validate(validatable))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    assertThat(validatable.getPayloadTimelinessCommitteePositions()).isPresent();
  }

  @Test
  void rejectsMessageSignedAtAnotherLeaf() {
    final PayloadAttestationMessagePq message =
        message(headData(), ptcAttesterLeaf().plus(UInt64.ONE));

    assertThat(
            validator.validate(ValidatablePayloadAttestationMessage.fromNetworkPq(spec, message)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void rejectsBlsSignedMessageAtPqSlot() {
    final PayloadAttestationMessage blsMessage =
        SchemaDefinitionsGloas.required(spec.atSlot(slot()).getSchemaDefinitions())
            .getPayloadAttestationMessageSchema()
            .create(ptcMember(), headData(), BLSSignature.empty());

    assertThat(validator.validate(ValidatablePayloadAttestationMessage.fromNetwork(blsMessage)))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void addRemotePqSendsValidMessageToForkChoiceOnly() {
    final PayloadAttestationMessagePq message = message(headData(), ptcAttesterLeaf());

    assertThat(pool.addRemotePq(message, Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    final ArgumentCaptor<ValidatablePayloadAttestationMessage> captor =
        ArgumentCaptor.forClass(ValidatablePayloadAttestationMessage.class);
    verify(validatedSubscriber).onOperationAdded(captor.capture(), any(), eq(true));
    assertThat(captor.getValue().getData()).isEqualTo(message.getData());
    assertThat(captor.getValue().getValidatorIndex()).isEqualTo(message.getValidatorIndex());
    assertThat(captor.getValue().getXmssSignature()).contains(message.getSignature());
    // Not kept for block production; PQ subscribers see it as from the network
    verifyNoInteractions(subscriber);
    verify(pqSubscriber).onOperationAdded(eq(message), any(), eq(true));
    assertThat(pool.getPayloadAttestations(headState())).isEmpty();
  }

  @Test
  void addLocalPqSendsValidMessageToForkChoiceAndPqSubscribersAsLocal() {
    final PayloadAttestationMessagePq message = message(headData(), ptcAttesterLeaf());

    assertThat(pool.addLocalPq(message))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    verify(validatedSubscriber).onOperationAdded(any(), any(), eq(false));
    // From a local validator, so it is published
    verify(pqSubscriber).onOperationAdded(eq(message), any(), eq(false));
    verifyNoInteractions(subscriber);
    assertThat(pool.getPayloadAttestations(headState())).isEmpty();
  }

  @Test
  void addLocalPqDoesNotNotifyForInvalidMessage() {
    // Signed at the wrong leaf
    final PayloadAttestationMessagePq message =
        message(headData(), ptcAttesterLeaf().plus(UInt64.ONE));

    assertThat(pool.addLocalPq(message))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);

    verify(validatedSubscriber, never()).onOperationAdded(any(), any(), anyBoolean());
    verifyNoInteractions(pqSubscriber);
  }

  @Test
  void addRemotePqIgnoresMessageForUnknownBlock() {
    final PayloadAttestationData unknownBlockData =
        SchemaDefinitionsGloas.required(spec.atSlot(slot()).getSchemaDefinitions())
            .getPayloadAttestationDataSchema()
            .create(Bytes32.fromHexString("0x01"), slot(), true, true);

    assertThat(pool.addRemotePq(message(unknownBlockData, ptcAttesterLeaf()), Optional.empty()))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
    verify(validatedSubscriber, never()).onOperationAdded(any(), any(), anyBoolean());
  }

  private PayloadAttestationMessagePq message(
      final PayloadAttestationData data, final UInt64 leafIndex) {
    return SchemaDefinitionsPq.required(spec.atSlot(slot()).getSchemaDefinitions())
        .getPqAttestationSchemas()
        .getPayloadAttestationMessageSchema()
        .create(ptcMember(), data, PqTestKeys.stubSignature(leafIndex).getBytes());
  }

  private PayloadAttestationData headData() {
    return SchemaDefinitionsGloas.required(spec.atSlot(slot()).getSchemaDefinitions())
        .getPayloadAttestationDataSchema()
        .create(recentChainData.getBestBlockRoot().orElseThrow(), slot(), true, true);
  }

  private UInt64 ptcMember() {
    final IntList ptc = spec.getPtc(headState(), slot());
    return UInt64.valueOf(ptc.getInt(0));
  }

  private UInt64 ptcAttesterLeaf() {
    return MiscHelpersPq.required(spec.atSlot(slot()).miscHelpers())
        .computeXmssLeafIndex(slot(), LEAF_OFFSET_PTC_ATTESTER);
  }

  private UInt64 slot() {
    return recentChainData.getHeadSlot();
  }

  private BeaconState headState() {
    return recentChainData.getBestState().orElseThrow().getImmediately();
  }
}
