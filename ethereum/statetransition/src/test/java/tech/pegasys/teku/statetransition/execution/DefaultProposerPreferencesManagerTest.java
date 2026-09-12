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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;

@TestSpecContext(milestone = SpecMilestone.GLOAS)
public class DefaultProposerPreferencesManagerTest {

  private final ProposerPreferencesGossipValidator gossipValidator =
      mock(ProposerPreferencesGossipValidator.class);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<SignedProposerPreferences> subscriber =
      mock(OperationAddedSubscriber.class);

  private DefaultProposerPreferencesManager manager;
  private PendingPool<PendingProposerPreferences> pendingProposerPreferences;
  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    pendingProposerPreferences =
        new PoolFactory(new StubMetricsSystem()).createPendingPoolForProposerPreferences(spec);
    manager = new DefaultProposerPreferencesManager(gossipValidator, pendingProposerPreferences);
    manager.subscribeOperationAdded(subscriber);
  }

  @TestTemplate
  void shouldStoreAndReturnAcceptedPreferences() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 slot = signedProposerPreferences.getMessage().getProposalSlot();
    final Bytes32 dependentRoot = signedProposerPreferences.getMessage().getDependentRoot();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    final InternalValidationResult result = safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(result).isEqualTo(ACCEPT);
    assertThat(manager.getProposerPreferences(slot, dependentRoot))
        .hasValue(signedProposerPreferences.getMessage());
  }

  @TestTemplate
  void shouldStorePreferencesForDistinctDependentRootsAtTheSameSlot() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final Bytes32 firstDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 secondDependentRoot = dataStructureUtil.randomBytes32();
    final SignedProposerPreferences firstPreferences =
        createSignedProposerPreferences(proposalSlot, firstDependentRoot);
    final SignedProposerPreferences secondPreferences =
        createSignedProposerPreferences(proposalSlot, secondDependentRoot);
    when(gossipValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addRemote(firstPreferences));
    safeJoin(manager.addRemote(secondPreferences));

    assertThat(manager.getProposerPreferences(proposalSlot, firstDependentRoot))
        .contains(firstPreferences.getMessage());
    assertThat(manager.getProposerPreferences(proposalSlot, secondDependentRoot))
        .contains(secondPreferences.getMessage());
    assertThat(manager.getProposerPreferencesForSlot(proposalSlot))
        .containsExactlyInAnyOrder(firstPreferences.getMessage(), secondPreferences.getMessage());
  }

  @TestTemplate
  void shouldReturnImmutableSnapshotOfPreferencesForSlot() {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 proposalSlot = preferences.getMessage().getProposalSlot();
    when(gossipValidator.validate(preferences)).thenReturn(SafeFuture.completedFuture(ACCEPT));
    safeJoin(manager.addRemote(preferences));

    assertThatThrownBy(() -> manager.getProposerPreferencesForSlot(proposalSlot).clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(manager.getProposerPreferencesForSlot(proposalSlot))
        .containsExactly(preferences.getMessage());
  }

  @TestTemplate
  void shouldNotStoreRejectedPreferences() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 slot = signedProposerPreferences.getMessage().getProposalSlot();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad signature")));

    final InternalValidationResult result = safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(result.isReject()).isTrue();

    assertThat(
            manager.getProposerPreferences(
                slot, signedProposerPreferences.getMessage().getDependentRoot()))
        .isEmpty();
  }

  @TestTemplate
  void shouldNotifySubscriberOnAcceptWithFromNetworkFalseForLocal() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    final InternalValidationResult result = safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(result).isEqualTo(ACCEPT);
    verify(subscriber).onOperationAdded(eq(signedProposerPreferences), eq(ACCEPT), eq(false));
  }

  @TestTemplate
  void shouldNotifySubscriberOnAcceptWithFromNetworkTrueForRemote() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    final InternalValidationResult result = safeJoin(manager.addRemote(signedProposerPreferences));

    assertThat(result).isEqualTo(ACCEPT);
    verify(subscriber).onOperationAdded(eq(signedProposerPreferences), eq(ACCEPT), eq(true));
  }

  @TestTemplate
  void shouldNotNotifySubscriberOnReject() {
    final SignedProposerPreferences signedProposerPreferences =
        dataStructureUtil.randomSignedProposerPreferences();

    when(gossipValidator.validate(signedProposerPreferences))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("bad signature")));

    final InternalValidationResult result = safeJoin(manager.addLocal(signedProposerPreferences));

    assertThat(result.isReject()).isTrue();
    verify(subscriber, never()).onOperationAdded(any(), any(), eq(false));
  }

  @TestTemplate
  void shouldReturnEmptyForUnknownSlot() {
    assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(999))).isEmpty();
  }

  @TestTemplate
  void shouldPruneAcceptedPreferencesBeforeCurrentSlot() {
    when(gossipValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));
    for (int slot = 9; slot <= 11; slot++) {
      safeJoin(
          manager.addRemote(
              createSignedProposerPreferences(
                  UInt64.valueOf(slot), dataStructureUtil.randomBytes32())));
    }

    manager.onSlot(UInt64.valueOf(10));

    assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(9))).isEmpty();
    assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(10))).isNotEmpty();
    assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(11))).isNotEmpty();
  }

  @TestTemplate
  void shouldRetainCurrentPreferencesWhenAddingNextEpochPreferences() {
    when(gossipValidator.validate(any())).thenReturn(SafeFuture.completedFuture(ACCEPT));

    for (int slot = 0; slot < 64; slot++) {
      safeJoin(
          manager.addRemote(
              createSignedProposerPreferences(
                  UInt64.valueOf(slot), dataStructureUtil.randomBytes32())));
    }
    for (int slot = 0; slot < 32; slot++) {
      assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(slot))).isNotEmpty();
    }

    manager.onSlot(UInt64.valueOf(32));
    for (int slot = 64; slot < 96; slot++) {
      safeJoin(
          manager.addRemote(
              createSignedProposerPreferences(
                  UInt64.valueOf(slot), dataStructureUtil.randomBytes32())));
    }

    for (int slot = 32; slot < 64; slot++) {
      assertThat(manager.getProposerPreferencesForSlot(UInt64.valueOf(slot))).isNotEmpty();
    }
  }

  @TestTemplate
  void shouldQueuePreferencesSavedForFuture() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(slot, dataStructureUtil.randomBytes32());
    manager.onSlot(slot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));

    assertThat(safeJoin(manager.addRemote(preferences))).isEqualTo(SAVE_FOR_FUTURE);

    assertThat(pendingProposerPreferences.get(preferences.hashTreeRoot()))
        .contains(new PendingProposerPreferences(preferences, true));
    assertThat(manager.getProposerPreferencesForSlot(slot)).isEmpty();
  }

  @TestTemplate
  void shouldRetryPreferencesWhenDependentBlockIsImported() {
    final SignedBeaconBlock dependentBlock = dataStructureUtil.randomSignedBeaconBlock(9);
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dependentBlock.getRoot());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addRemote(preferences));
    manager.onBlockImported(dependentBlock, false);

    verify(gossipValidator, times(2)).validate(preferences);
    assertThat(
            manager.getProposerPreferences(
                proposalSlot, preferences.getMessage().getDependentRoot()))
        .contains(preferences.getMessage());
    assertThat(pendingProposerPreferences.get(preferences.hashTreeRoot())).isEmpty();
  }

  @TestTemplate
  void shouldRetryPreferencesOnSlot() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addRemote(preferences));
    manager.onSlot(proposalSlot);

    verify(gossipValidator, times(2)).validate(preferences);
    assertThat(
            manager.getProposerPreferences(
                proposalSlot, preferences.getMessage().getDependentRoot()))
        .contains(preferences.getMessage());
  }

  @TestTemplate
  void shouldDropPendingPreferencesAfterProposalSlot() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));

    safeJoin(manager.addRemote(preferences));
    manager.onSlot(proposalSlot.plus(1));

    verify(gossipValidator).validate(preferences);
    assertThat(pendingProposerPreferences.get(preferences.hashTreeRoot())).isEmpty();
  }

  @TestTemplate
  void shouldNotRetainPreferencesAfterTerminalRetryResult() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResult.reject("invalid preferences")));

    safeJoin(manager.addRemote(preferences));
    manager.onSlot(proposalSlot);
    manager.onSlot(proposalSlot);

    verify(gossipValidator, times(2)).validate(preferences);
    assertThat(pendingProposerPreferences.get(preferences.hashTreeRoot())).isEmpty();
  }

  @TestTemplate
  void shouldRemoveQueuedPreferencesWhenResubmissionIsAccepted() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addRemote(preferences));
    safeJoin(manager.addRemote(preferences));

    assertThat(pendingProposerPreferences.get(preferences.hashTreeRoot())).isEmpty();
  }

  @TestTemplate
  void shouldPreserveLocalOriginWhenRetryingPreferences() {
    final SignedBeaconBlock dependentBlock = dataStructureUtil.randomSignedBeaconBlock(9);
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final SignedProposerPreferences preferences =
        createSignedProposerPreferences(proposalSlot, dependentBlock.getRoot());
    manager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(preferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    safeJoin(manager.addLocal(preferences));
    manager.onBlockImported(dependentBlock, false);

    verify(subscriber).onOperationAdded(preferences, ACCEPT, false);
  }

  @TestTemplate
  void shouldBoundPendingPreferences() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final PendingPool<PendingProposerPreferences> boundedPendingPool =
        new PoolFactory(new StubMetricsSystem()).createPendingPoolForProposerPreferences(spec, 1);
    final DefaultProposerPreferencesManager boundedManager =
        new DefaultProposerPreferencesManager(gossipValidator, boundedPendingPool);
    final SignedProposerPreferences firstPreferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    final SignedProposerPreferences secondPreferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    boundedManager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(any())).thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));

    safeJoin(boundedManager.addRemote(firstPreferences));
    safeJoin(boundedManager.addRemote(secondPreferences));

    assertThat(boundedPendingPool.size()).isOne();
  }

  @TestTemplate
  void shouldNotRetainLocalOriginAfterPendingPreferenceIsEvicted() {
    final UInt64 proposalSlot = UInt64.valueOf(10);
    final PendingPool<PendingProposerPreferences> boundedPendingPool =
        new PoolFactory(new StubMetricsSystem()).createPendingPoolForProposerPreferences(spec, 1);
    final DefaultProposerPreferencesManager boundedManager =
        new DefaultProposerPreferencesManager(gossipValidator, boundedPendingPool);
    boundedManager.subscribeOperationAdded(subscriber);
    final SignedProposerPreferences firstPreferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    final SignedProposerPreferences secondPreferences =
        createSignedProposerPreferences(proposalSlot, dataStructureUtil.randomBytes32());
    boundedManager.onSlot(proposalSlot.minus(1));
    when(gossipValidator.validate(firstPreferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    when(gossipValidator.validate(secondPreferences))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));

    safeJoin(boundedManager.addLocal(firstPreferences));
    safeJoin(boundedManager.addLocal(secondPreferences));
    safeJoin(boundedManager.addRemote(firstPreferences));
    boundedManager.onSlot(proposalSlot);

    verify(subscriber).onOperationAdded(firstPreferences, ACCEPT, true);
  }

  private SignedProposerPreferences createSignedProposerPreferences(
      final UInt64 proposalSlot, final Bytes32 dependentRoot) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(proposalSlot).getSchemaDefinitions());
    final ProposerPreferences randomPreferences = dataStructureUtil.randomProposerPreferences();
    final ProposerPreferences preferences =
        schemaDefinitions
            .getProposerPreferencesSchema()
            .create(
                dependentRoot,
                proposalSlot,
                randomPreferences.getValidatorIndex(),
                randomPreferences.getFeeRecipient(),
                randomPreferences.getTargetGasLimit());
    return schemaDefinitions
        .getSignedProposerPreferencesSchema()
        .create(preferences, dataStructureUtil.randomSignature());
  }
}
