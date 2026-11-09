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
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodySchemaCapella;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.OperationValidator;

@SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
@TestSpecContext(allMilestones = true)
class SimpleOperationPoolForkTest {

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private BeaconState state;
  private SpecConfig specConfig;
  private Function<UInt64, BeaconBlockBodySchema<?>> blockSchemaSupplier;
  private StubTimeProvider timeProvider;
  private StubAsyncRunner asyncRunner;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    state = mock(BeaconState.class);
    when(state.getSlot()).thenReturn(UInt64.ZERO);
    specConfig = spec.forMilestone(specContext.getSpecMilestone()).getConfig();
    blockSchemaSupplier =
        slot -> spec.atSlot(slot).getSchemaDefinitions().getBeaconBlockBodySchema();
    timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000);
    asyncRunner = new StubAsyncRunner(timeProvider);
  }

  @TestTemplate
  void shouldLimitAttesterSlashingsToForkSpecificConsensusMaximum() {
    final OperationValidator<AttesterSlashing> validator = mock(OperationValidator.class);
    final OperationPool<AttesterSlashing> pool =
        new SimpleOperationPool<>(
            "AttesterSlashingPool",
            metricsSystem,
            blockSchemaSupplier.andThen(BeaconBlockBodySchema::getAttesterSlashingsSchema),
            validator);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());

    final int maxAttesterSlashings =
        specConfig
            .toVersionElectra()
            .map(SpecConfigElectra::getMaxAttesterSlashingsElectra)
            .orElseGet(specConfig::getMaxAttesterSlashings);
    assertPoolSelectionLimit(pool, dataStructureUtil::randomAttesterSlashing, maxAttesterSlashings);
  }

  @TestTemplate
  void shouldLimitProposerSlashingsToConsensusMaximum() {
    final OperationValidator<ProposerSlashing> validator = mock(OperationValidator.class);
    final OperationPool<ProposerSlashing> pool =
        new SimpleOperationPool<>(
            "ProposerSlashingPool",
            metricsSystem,
            blockSchemaSupplier.andThen(BeaconBlockBodySchema::getProposerSlashingsSchema),
            validator);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());

    assertPoolSelectionLimit(
        pool, dataStructureUtil::randomProposerSlashing, specConfig.getMaxProposerSlashings());
  }

  @TestTemplate
  void shouldLimitVoluntaryExitsToConsensusMaximum() {
    final OperationValidator<SignedVoluntaryExit> validator = mock(OperationValidator.class);
    final OperationPool<SignedVoluntaryExit> pool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            blockSchemaSupplier.andThen(BeaconBlockBodySchema::getVoluntaryExitsSchema),
            validator,
            asyncRunner,
            timeProvider);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());

    assertPoolSelectionLimit(
        pool, dataStructureUtil::randomSignedVoluntaryExit, specConfig.getMaxVoluntaryExits());
  }

  @TestTemplate
  void shouldLimitBlsToExecutionChangesToConsensusMaximum(final SpecContext specContext) {
    specContext.assumeIsNotOneOf(PHASE0, ALTAIR, BELLATRIX);
    final OperationValidator<SignedBlsToExecutionChange> validator = mock(OperationValidator.class);
    final OperationPool<SignedBlsToExecutionChange> pool =
        new MappedOperationPool<>(
            "BlsToExecutionChangePool",
            metricsSystem,
            blockSchemaSupplier
                .andThen(BeaconBlockBodySchema::toVersionCapella)
                .andThen(Optional::orElseThrow)
                .andThen(BeaconBlockBodySchemaCapella::getBlsToExecutionChangesSchema),
            validator,
            asyncRunner,
            timeProvider);
    when(validator.validateForGossip(any())).thenReturn(completedFuture(ACCEPT));
    when(validator.validateForBlockInclusion(any(), any())).thenReturn(Optional.empty());

    assertPoolSelectionLimit(
        pool,
        dataStructureUtil::randomSignedBlsToExecutionChange,
        SpecConfigCapella.required(specConfig).getMaxBlsToExecutionChanges());
  }

  private <T extends SszData> void assertPoolSelectionLimit(
      final OperationPool<T> pool, final Supplier<T> operationFactory, final int maximum) {
    while (pool.size() <= maximum) {
      assertThat(pool.addLocal(operationFactory.get())).isCompleted();
    }

    assertThat(pool.size()).isEqualTo(maximum + 1);
    assertThat(pool.getItemsForBlock(state, maximum)).hasSize(maximum);
  }
}
