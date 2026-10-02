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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

@SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
public class SimpleOperationPoolTest {

  Spec spec = TestSpecFactory.createMinimalPhase0();
  DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  Function<UInt64, BeaconBlockBodySchema<?>> beaconBlockSchemaSupplier =
      slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
  BeaconState state = mock(BeaconState.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  void init() {
    when(state.getSlot()).thenReturn(UInt64.ZERO);
  }

  @Test
  void emptyPoolShouldReturnEmptyList() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);

    OperationPool<ProposerSlashing> pool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    assertThat(pool.getItemsForBlock(state, spec.getGenesisSpecConfig().getMaxProposerSlashings()))
        .isNotEmpty();
  }

  @Test
  void shouldAddMaxItemsInBigPool() {
    final int largePoolSize = 16_384;
    OperationValidator<SszBytes4> validator = mock(OperationValidator.class);
    OperationPool<SszBytes4> pool =
        new SimpleOperationPool<>(
            "Bytes4Pool", metricsSystem, (a) -> null, validator, largePoolSize);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    final int loopSanityCounter = largePoolSize + 10_000;
    // because the loop de-duplicates, we can require more than the pool size of adds, so
    // the sanity counter will ensure we can go past the loop limit without going too far
    for (int i = 0; pool.size() < largePoolSize && i < loopSanityCounter; i++) {
      pool.addLocal(SszBytes4.of(dataStructureUtil.randomBytes4()));
    }
    assertThat(pool.size()).isEqualTo(largePoolSize);
  }

  @Test
  void shouldAddMaxItemsToPool() {
    OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new SimpleOperationPool<>(
            "SignedVoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    final int maxVoluntaryExits = spec.getGenesisSpecConfig().getMaxVoluntaryExits();
    for (int i = 0; i < maxVoluntaryExits + 1; i++) {
      pool.addLocal(dataStructureUtil.randomSignedVoluntaryExit());
    }
    // Verify the bounded schema limit is respected even when the caller requests more items
    assertThat(pool.getItemsForBlock(state, maxVoluntaryExits + 1)).hasSize(maxVoluntaryExits);
  }

  @Test
  void shouldNotCountFilteredOperationsInMaxItems() {
    final Predicate<SignedVoluntaryExit> filter = mock(Predicate.class);
    OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    OperationPool<SignedVoluntaryExit> pool =
        new SimpleOperationPool<>(
            "SignedVoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);
    when(filter.test(any())).thenReturn(false);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    final int maxVoluntaryExits = spec.getGenesisSpecConfig().getMaxVoluntaryExits();
    for (int i = 0; i < maxVoluntaryExits + 10; i++) {
      pool.addLocal(dataStructureUtil.randomSignedVoluntaryExit());
    }
    // Didn't find any applicable items but tried them all
    assertThat(pool.getItemsForBlock(state, maxVoluntaryExits, filter, operation -> {})).isEmpty();
    verify(filter, times(maxVoluntaryExits + 10)).test(any());
  }

  @Test
  void shouldAllowFilterConditionToChangeAfterEachSelection() {
    final OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);

    OperationPool<SignedVoluntaryExit> pool =
        new SimpleOperationPool<>(
            "SignedVoluntaryExitPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator);

    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());

    final SignedVoluntaryExit exit1 = dataStructureUtil.randomSignedVoluntaryExit();
    final SignedVoluntaryExit exit2 = dataStructureUtil.randomSignedVoluntaryExit();

    pool.addLocal(exit1);
    pool.addLocal(exit2);

    final Set<SignedVoluntaryExit> exitsToAccept = new HashSet<>(Set.of(exit1, exit2));
    final SszList<SignedVoluntaryExit> selectedItems =
        pool.getItemsForBlock(
            state,
            spec.getGenesisSpecConfig().getMaxVoluntaryExits(),
            exitsToAccept::contains,
            exit -> {
              // Only allow the first exit to be added
              exitsToAccept.clear();
            });
    assertThat(selectedItems).hasSize(1);
  }

  @Test
  void shouldRemoveAllItemsFromPool() {
    OperationValidator<AttesterSlashing> validator = mock(OperationValidator.class);
    SszListSchema<AttesterSlashing, ?> attesterSlashingsSchema =
        beaconBlockSchemaSupplier
            .andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema)
            .apply(state.getSlot());
    OperationPool<AttesterSlashing> pool =
        new SimpleOperationPool<>(
            "AttesterSlashingPool", metricsSystem, __ -> attesterSlashingsSchema, validator);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());
    SszList<AttesterSlashing> attesterSlashings =
        Stream.generate(() -> dataStructureUtil.randomAttesterSlashing())
            .limit(attesterSlashingsSchema.getMaxLength())
            .collect(attesterSlashingsSchema.collector());
    pool.removeAll(attesterSlashings);
    assertThat(pool.getItemsForBlock(state, spec.getGenesisSpecConfig().getMaxAttesterSlashings()))
        .isEmpty();
  }

  @Test
  void shouldNotIncludeInvalidatedItemsFromPool() {
    final OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    final OperationPool<ProposerSlashing> pool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);

    final ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    final ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));

    pool.addLocal(slashing1);
    pool.addLocal(slashing2);

    when(validator.validateForBlockInclusion(any(), eq(slashing1)))
        .thenReturn(Optional.of(ExitInvalidReason.submittedTooEarly()));
    when(validator.validateForBlockInclusion(any(), eq(slashing2))).thenReturn(Optional.empty());

    assertThat(pool.getItemsForBlock(state, spec.getGenesisSpecConfig().getMaxProposerSlashings()))
        .containsOnly(slashing2);
  }

  @Test
  void shouldRemoveInvalidOptionsFromThePool() {
    // When we attempt to build a block, if we find operations that are now invalid, remove them

    final OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    final OperationPool<ProposerSlashing> pool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);

    final ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    final ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));

    pool.addLocal(slashing1);
    pool.addLocal(slashing2);

    when(validator.validateForBlockInclusion(any(), eq(slashing1)))
        .thenReturn(Optional.of(ExitInvalidReason.submittedTooEarly()));
    when(validator.validateForBlockInclusion(any(), eq(slashing2))).thenReturn(Optional.empty());

    assertThat(pool.getItemsForBlock(state, spec.getGenesisSpecConfig().getMaxProposerSlashings()))
        .containsOnly(slashing2);
    assertThat(pool.getAll()).containsOnly(slashing2);
  }

  @Test
  void subscribeOperationAdded() {
    OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    OperationPool<ProposerSlashing> pool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            beaconBlockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);

    // Set up subscriber
    final Map<ProposerSlashing, InternalValidationResult> addedSlashings = new HashMap<>();
    OperationAddedSubscriber<ProposerSlashing> subscriber =
        (key, value, fromNetwork) -> addedSlashings.put(key, value);
    pool.subscribeOperationAdded(subscriber);

    ProposerSlashing slashing1 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing2 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing3 = dataStructureUtil.randomProposerSlashing();
    ProposerSlashing slashing4 = dataStructureUtil.randomProposerSlashing();

    when(validator.validateForGossip(slashing1)).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForGossip(slashing2)).thenReturn(completedFuture(SAVE_FOR_FUTURE));
    when(validator.validateForGossip(slashing3))
        .thenReturn(completedFuture(InternalValidationResult.reject("Nah")));
    when(validator.validateForGossip(slashing4)).thenReturn(completedFuture(IGNORE));

    pool.addLocal(slashing1);
    pool.addLocal(slashing2);
    pool.addLocal(slashing3);
    pool.addLocal(slashing4);

    assertThat(addedSlashings.size()).isEqualTo(2);
    assertThat(addedSlashings).containsKey(slashing1);
    assertThat(addedSlashings.get(slashing1).isAccept()).isTrue();
    assertThat(addedSlashings).containsKey(slashing2);
    assertThat(addedSlashings.get(slashing2).isSaveForFuture()).isTrue();
  }
}
