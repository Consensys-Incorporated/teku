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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;
import tech.pegasys.teku.statetransition.validation.SignedBlsToExecutionChangeValidator;

public class MappedOperationPoolTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  private final BeaconState state = mock(BeaconState.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final SignedBlsToExecutionChangeValidator validator =
      mock(SignedBlsToExecutionChangeValidator.class);
  private final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(stubTimeProvider);
  private final Function<UInt64, SszListSchema<SignedBlsToExecutionChange, ?>>
      blsToExecutionSchemaSupplier =
          beaconBlockSchemaSupplier
              .andThen(BeaconBlockBodySchema::toVersionCapella)
              .andThen(Optional::orElseThrow)
              .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema);
  private final int maxBlsToExecutionChanges =
      spec.getGenesisSpecConfig().toVersionCapella().orElseThrow().getMaxBlsToExecutionChanges();
  private final OperationPool<SignedBlsToExecutionChange> pool =
      new MappedOperationPool<>(
          "BlsToExecutionOperationPool",
          metricsSystem,
          blsToExecutionSchemaSupplier,
          validator,
          asyncRunner,
          stubTimeProvider);

  @BeforeEach
  void init() {
    when(state.getSlot()).thenReturn(UInt64.ZERO);
  }

  @Test
  void emptyPoolShouldReturnEmptyList() {
    assertThat(pool.getItemsForBlock(state, maxBlsToExecutionChanges)).isEmpty();
  }

  @Test
  void shouldAddEntryToPool() {
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    final SafeFuture<?> future = pool.addLocal(item);
    assertThat(future).isCompleted();
    assertThat(pool.size()).isEqualTo(1);
    assertThat(pool.getAll()).containsExactly(item);
  }

  @Test
  void shouldRejectDuplicateEntryFromPool() throws ExecutionException, InterruptedException {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();

    final SafeFuture<InternalValidationResult> future = pool.addLocal(item);
    assertThat(future.get().code()).isEqualTo(InternalValidationResult.IGNORE.code());
  }

  @Test
  void shouldRemoveEntryFromPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    pool.removeAll(
        blsToExecutionSchemaSupplier
            .apply(UInt64.ZERO)
            .createFromElements(
                List.of(item, dataStructureUtil.randomSignedBlsToExecutionChange(), item)));
    assertThat(pool.size()).isEqualTo(0);
  }

  @Test
  void shouldAddEntriesToPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    final SignedBlsToExecutionChange item2 = dataStructureUtil.randomSignedBlsToExecutionChange();
    pool.addAll(
        blsToExecutionSchemaSupplier.apply(UInt64.ZERO).createFromElements(List.of(item, item2)));
    assertThat(pool.size()).isEqualTo(2);
    assertThat(pool.getAll()).containsExactlyInAnyOrder(item, item2);
  }

  private SignedBlsToExecutionChange initPoolWithSingleItem() {
    when(validator.validateForGossip(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    final SafeFuture<InternalValidationResult> future = pool.addLocal(item);
    assertThat(future).isCompleted();
    assertThat(pool.size()).isEqualTo(1);
    return item;
  }

  @Test
  void shouldAddMaxItemsToPool() {
    final SignedBlsToExecutionChange item = initPoolWithSingleItem();
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    while (pool.size() <= maxBlsToExecutionChanges) {
      assertThat(pool.addLocal(dataStructureUtil.randomSignedBlsToExecutionChange())).isCompleted();
    }

    // Verify the bounded schema limit is respected even when the caller requests more items
    assertThat(pool.getItemsForBlock(state, maxBlsToExecutionChanges + 1))
        .hasSize(maxBlsToExecutionChanges);
    assertThat(pool.size()).isEqualTo(maxBlsToExecutionChanges + 1);
    assertThat(pool.getItemsForBlock(state, maxBlsToExecutionChanges)).contains(item);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldLimitGloasVoluntaryExitsToConsensusMaximum() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final OperationValidator<SignedVoluntaryExit> exitValidator = mock(OperationValidator.class);
    final OperationPool<SignedVoluntaryExit> voluntaryExitPool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            slot ->
                gloasSpec
                    .atSlot(slot)
                    .getSchemaDefinitions()
                    .getBeaconBlockBodySchema()
                    .getVoluntaryExitsSchema(),
            exitValidator,
            asyncRunner,
            stubTimeProvider);
    when(exitValidator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(exitValidator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    final int maxVoluntaryExits = gloasSpec.getGenesisSpecConfig().getMaxVoluntaryExits();
    while (voluntaryExitPool.size() <= maxVoluntaryExits) {
      assertThat(voluntaryExitPool.addLocal(gloasDataStructureUtil.randomSignedVoluntaryExit()))
          .isCompleted();
    }

    assertThat(voluntaryExitPool.size()).isEqualTo(maxVoluntaryExits + 1);
    assertThat(voluntaryExitPool.getItemsForBlock(state, maxVoluntaryExits))
        .hasSize(maxVoluntaryExits);
  }

  @Test
  void shouldSelectLocalOperationBeforeRemoteOperation() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    initPoolWithSingleItem();
    final SignedBlsToExecutionChange remoteEntry =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final SignedBlsToExecutionChange secondLocalEntry =
        dataStructureUtil.randomSignedBlsToExecutionChange();

    assertThat(pool.addRemote(remoteEntry, Optional.empty())).isCompleted();
    assertThat(pool.addLocal(secondLocalEntry)).isCompleted();

    final SszList<SignedBlsToExecutionChange> blockItems =
        pool.getItemsForBlock(state, maxBlsToExecutionChanges);
    assertThat(blockItems.size()).isEqualTo(3);
    assertThat(blockItems.get(2)).isEqualTo(remoteEntry);
  }

  @Test
  void getLocalEntriesReturnsOnlyLocalEntries() {
    final SignedBlsToExecutionChange localEntry = initPoolWithSingleItem();
    final SignedBlsToExecutionChange remoteEntry =
        dataStructureUtil.randomSignedBlsToExecutionChange();
    final SignedBlsToExecutionChange secondLocalEntry =
        dataStructureUtil.randomSignedBlsToExecutionChange();

    assertThat(pool.addRemote(remoteEntry, Optional.empty())).isCompleted();
    assertThat(pool.addLocal(secondLocalEntry)).isCompleted();

    assertThat(pool.getLocallySubmitted()).containsExactlyInAnyOrder(localEntry, secondLocalEntry);
  }

  @Test
  void shouldPruneFromPoolIfNoLongerValid() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any()))
        .thenReturn(Optional.of(mock(OperationInvalidReason.class)));

    initPoolWithSingleItem();
    assertThat(pool.size()).isEqualTo(1);

    assertThat(pool.getItemsForBlock(state, maxBlsToExecutionChanges)).isEmpty();
    assertThat(pool.size()).isEqualTo(0);
  }

  @Test
  void shouldNotUpdateLocalOperationsIfLessThanMinimumTime() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any()))
        .thenReturn(Optional.of(mock(OperationInvalidReason.class)));

    initPoolWithSingleItem();
    assertThat(pool.size()).isEqualTo(1);
    // the object only gets verified once, because the async running task doesn't pick it up to
    // reprocess
    verify(validator, times(1)).validateForGossip(any());
    final Subscription subscription = initialiseSubscriptions();
    stubTimeProvider.advanceTimeBySeconds(7199);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeDueActions();

    verifyNoMoreInteractions(validator);
    assertThat(subscription.getBlsToExecutionChange()).isEmpty();
  }

  @Test
  void shouldUpdateLocalOperationsIfStale() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any()))
        .thenReturn(Optional.of(mock(OperationInvalidReason.class)));

    initPoolWithSingleItem();
    assertThat(pool.size()).isEqualTo(1);

    final Subscription subscription = initialiseSubscriptions();
    stubTimeProvider.advanceTimeBySeconds(7201);
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    asyncRunner.executeDueActions();
    // the stale object should get verified if it's being reprocessed
    verify(validator, times(2)).validateForGossip(any());
    assertThat(subscription.getBlsToExecutionChange()).isNotEmpty();
  }

  @Test
  void shouldNotReprocessRemoteOperations() {
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    final SignedBlsToExecutionChange remoteEntry =
        dataStructureUtil.randomSignedBlsToExecutionChange();

    assertThat(pool.addRemote(remoteEntry, Optional.empty())).isCompleted();
    verify(validator, times(1)).validateForGossip(any());
    stubTimeProvider.advanceTimeBySeconds(1_000_000);
    asyncRunner.executeDueActions();
    // the object only gets verified once, because the async running task doesn't pick it up to
    // reprocess
    verifyNoMoreInteractions(validator);
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationAddedSuccessfully(final boolean isFromNetwork) {
    final Subscription subscription = initialiseSubscriptions();
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(ACCEPT));

    if (isFromNetwork) {
      assertThat(pool.addRemote(item, Optional.empty())).isCompleted();
    } else {
      assertThat(pool.addLocal(item)).isCompleted();
    }
    assertThat(subscription.getBlsToExecutionChange()).contains(item);
    assertThat(subscription.getInternalValidationResult()).contains(ACCEPT);
    assertThat(subscription.getFromNetwork()).contains(isFromNetwork);
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationIgnored(final boolean isFromNetwork) {
    final Subscription subscription = initialiseSubscriptions();
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(IGNORE));

    if (isFromNetwork) {
      assertThat(pool.addRemote(item, Optional.empty())).isCompleted();
    } else {
      assertThat(pool.addLocal(item)).isCompleted();
    }
    assertThat(subscription.getBlsToExecutionChange()).isEmpty();
    assertThat(subscription.getInternalValidationResult()).isEmpty();
    assertThat(subscription.getFromNetwork()).isEmpty();
  }

  @ParameterizedTest(name = "fromNetwork={0}")
  @ValueSource(booleans = {true, false})
  void subscribeOperationIgnoredDuplicate(final boolean isFromNetwork)
      throws ExecutionException, InterruptedException {
    final SignedBlsToExecutionChange item = dataStructureUtil.randomSignedBlsToExecutionChange();
    when(validator.validateForGossip(item)).thenReturn(completedFuture(ACCEPT));
    // pre-populate cache
    assertThat(pool.addRemote(item, Optional.empty())).isCompleted();

    final Subscription subscription = initialiseSubscriptions();

    final SafeFuture<InternalValidationResult> future;
    if (isFromNetwork) {
      // pre-populate cache, then try to add a second time.
      future = pool.addRemote(item, Optional.empty());
    } else {
      future = pool.addLocal(item);
    }
    assertThat(future).isCompleted();
    assertThat(future.get().code()).isEqualTo(IGNORE.code());
    assertThat(subscription.getBlsToExecutionChange()).isEmpty();
    assertThat(subscription.getInternalValidationResult()).isEmpty();
    assertThat(subscription.getFromNetwork()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void builderExitShouldNotCollideWithRegularValidatorExitWithSameLowBits() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final OperationValidator<SignedVoluntaryExit> exitValidator = mock(OperationValidator.class);
    final OperationPool<SignedVoluntaryExit> voluntaryExitPool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            slot ->
                gloasSpec
                    .atSlot(slot)
                    .getSchemaDefinitions()
                    .getBeaconBlockBodySchema()
                    .getVoluntaryExitsSchema(),
            exitValidator,
            asyncRunner,
            stubTimeProvider);

    when(exitValidator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));

    final UInt64 regularIndex = UInt64.valueOf(5);
    final UInt64 builderIndex =
        UInt64.fromLongBits(
            regularIndex.longValue() | SpecConfigGloas.BUILDER_INDEX_FLAG.longValue());

    final SignedVoluntaryExit regularExit =
        new SignedVoluntaryExit(
            new VoluntaryExit(UInt64.ZERO, regularIndex), dataStructureUtil.randomSignature());
    final SignedVoluntaryExit builderExit =
        new SignedVoluntaryExit(
            new VoluntaryExit(UInt64.ZERO, builderIndex), dataStructureUtil.randomSignature());

    assertThat(voluntaryExitPool.addRemote(regularExit, Optional.empty())).isCompleted();
    assertThat(voluntaryExitPool.addRemote(builderExit, Optional.empty())).isCompleted();
    assertThat(voluntaryExitPool.size()).isEqualTo(2);
  }

  private Subscription initialiseSubscriptions() {
    final Subscription subscription = new Subscription();
    final OperationAddedSubscriber<SignedBlsToExecutionChange> subscriber =
        (key, value, fromNetwork) -> {
          subscription.setFromNetwork(fromNetwork);
          subscription.setBlsToExecutionChange(key);
          subscription.setInternalValidationResult(value);
        };
    pool.subscribeOperationAdded(subscriber);
    return subscription;
  }

  private static class Subscription {
    private Optional<Boolean> fromNetwork = Optional.empty();
    private Optional<SignedBlsToExecutionChange> blsToExecutionChange = Optional.empty();
    private Optional<InternalValidationResult> internalValidationResult = Optional.empty();

    public void setFromNetwork(final boolean fromNetwork) {
      this.fromNetwork = Optional.of(fromNetwork);
    }

    public void setBlsToExecutionChange(final SignedBlsToExecutionChange change) {
      this.blsToExecutionChange = Optional.of(change);
    }

    public void setInternalValidationResult(final InternalValidationResult result) {
      this.internalValidationResult = Optional.of(result);
    }

    public Optional<Boolean> getFromNetwork() {
      return fromNetwork;
    }

    public Optional<SignedBlsToExecutionChange> getBlsToExecutionChange() {
      return blsToExecutionChange;
    }

    public Optional<InternalValidationResult> getInternalValidationResult() {
      return internalValidationResult;
    }
  }
}
