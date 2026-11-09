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

package tech.pegasys.teku.weaksubjectivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidatorTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final WeakSubjectivityConfig config =
      WeakSubjectivityConfig.builder().specProvider(spec).build();

  // Set up mocks
  private final WeakSubjectivityCalculator calculator = mock(WeakSubjectivityCalculator.class);
  private final ReadOnlyForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final WeakSubjectivityViolationPolicy policy =
      mock(WeakSubjectivityViolationPolicy.class);
  private final CheckpointState checkpointState = mock(CheckpointState.class);
  private final UInt64 currentSlot = UInt64.valueOf(10_000);
  private final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
  private final UInt64 mockWsPeriod = UInt64.valueOf(3);

  @BeforeEach
  public void setup() {
    when(checkpointState.getState()).thenReturn(mock(BeaconState.class));
  }

  @Test
  public void validateLatestFinalizedCheckpoint_noWSCheckpoint_validationShouldFail() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.computeWeakSubjectivityPeriod(checkpointState)).thenReturn(mockWsPeriod);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            spec.computeEpochAtSlot(currentSlot), checkpointState, mockWsPeriod);

    verifyNoMoreInteractions(policy);
  }

  @Test
  public void validateLatestFinalizedCheckpoint_noWSCheckpoint_validationShouldPass() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldSkipChecksWhenFinalizedPriorToCheckpoint() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().minus(1));

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator, never()).isWithinWeakSubjectivityPeriod(any(), any());
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizedAfterCheckpoint_shouldFail() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().plus(1));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.computeWeakSubjectivityPeriod(checkpointState)).thenReturn(mockWsPeriod);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            spec.computeEpochAtSlot(currentSlot), checkpointState, mockWsPeriod);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizedAfterCheckpoint_shouldPass() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch().plus(1));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizedAtCheckpoint_failDueToInconsistency() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    // Checkpoint is at the ws epoch, but has a different root
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(Bytes32.fromHexStringLenient("0x02"));
    when(checkpointState.getBlockSlot()).thenReturn(wsCheckpoint.getEpochStartSlot(spec));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onChainInconsistentWithWeakSubjectivityCheckpoint(
            wsCheckpoint, checkpointState.getRoot(), checkpointState.getBlockSlot());
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizedAtCheckpoint_failDueToWSPeriod() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(wsCheckpoint.getRoot());
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.computeWeakSubjectivityPeriod(checkpointState)).thenReturn(mockWsPeriod);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            spec.computeEpochAtSlot(currentSlot), checkpointState, mockWsPeriod);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizedAtCheckpoint_shouldFailMultipleChecks() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    // Checkpoint is at the ws epoch, but has a different root
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(Bytes32.fromHexStringLenient("0x02"));
    when(checkpointState.getBlockSlot()).thenReturn(wsCheckpoint.getEpochStartSlot(spec));
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.computeWeakSubjectivityPeriod(checkpointState)).thenReturn(mockWsPeriod);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onChainInconsistentWithWeakSubjectivityCheckpoint(
            wsCheckpoint, checkpointState.getRoot(), checkpointState.getBlockSlot());
    verify(policy)
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            spec.computeEpochAtSlot(currentSlot), checkpointState, mockWsPeriod);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void
      validateLatestFinalizedCheckpoint_withWSCheckpoint_shouldRunChecksWhenFinalizeAtCheckpoint_shouldPass() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    // Checkpoint is at the ws epoch, with the same root
    when(checkpointState.getEpoch()).thenReturn(wsCheckpoint.getEpoch());
    when(checkpointState.getRoot()).thenReturn(wsCheckpoint.getRoot());
    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(true);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(calculator).isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void validateLatestFinalizedCheckpoint_suppressWarnings_shouldSuppress() {
    final UInt64 configuredEpoch = currentEpoch.plus(1);
    final WeakSubjectivityConfig config =
        configBuilder().suppressWSPeriodChecksUntilEpoch(configuredEpoch).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verifyNoMoreInteractions(policy);
  }

  @Test
  public void validateLatestFinalizedCheckpoint_suppressWarnings_shouldNotSuppressAtEpochLimit() {
    final UInt64 configuredEpoch = currentEpoch;
    final WeakSubjectivityConfig config =
        configBuilder().suppressWSPeriodChecksUntilEpoch(configuredEpoch).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    when(calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot)).thenReturn(false);
    when(calculator.computeWeakSubjectivityPeriod(checkpointState)).thenReturn(mockWsPeriod);

    validator.validateLatestFinalizedCheckpoint(checkpointState, currentSlot);

    verify(policy)
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            spec.computeEpochAtSlot(currentSlot), checkpointState, mockWsPeriod);
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void getSuppressWSPeriodChecksUntilEpoch_shouldNotModifyConfiguredEpoch() {
    final UInt64 configuredEpoch =
        currentEpoch.plus(WeakSubjectivityValidator.MAX_SUPPRESSED_EPOCHS).minus(1);
    final WeakSubjectivityConfig config =
        configBuilder().suppressWSPeriodChecksUntilEpoch(configuredEpoch).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot))
        .contains(configuredEpoch);
    // Subsequent calls should return the same value regardless of slot
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot.plus(10_000)))
        .contains(configuredEpoch);
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(UInt64.ZERO))
        .contains(configuredEpoch);
  }

  @Test
  public void getSuppressWSPeriodChecksUntilEpoch_shouldModifyConfiguredEpoch() {
    final UInt64 configuredEpoch =
        currentEpoch.plus(WeakSubjectivityValidator.MAX_SUPPRESSED_EPOCHS).plus(1);
    final WeakSubjectivityConfig config =
        configBuilder().suppressWSPeriodChecksUntilEpoch(configuredEpoch).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    final UInt64 expected = currentEpoch.plus(WeakSubjectivityValidator.MAX_SUPPRESSED_EPOCHS);
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot)).contains(expected);
    // Subsequent calls should return the same value regardless of slot
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot.plus(10_000)))
        .contains(expected);
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(UInt64.ZERO)).contains(expected);
  }

  @Test
  public void getSuppressWSPeriodChecksUntilEpoch_shouldReturnEmptyWhenConfigIsEmpty() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot)).isEmpty();
    // Subsequent calls should return the same value regardless of slot
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(currentSlot.plus(10_000))).isEmpty();
    assertThat(validator.getSuppressWSPeriodChecksUntilEpoch(UInt64.ZERO)).isEmpty();
  }

  @Test
  public void validateChainIsConsistentWithWSCheckpoint_noCheckpointSet() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final CombinedChainDataClient chainData = mockChainDataClientPriorToCheckpoint();

    SafeFuture<Void> result = validator.validateChainIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    verifyNoMoreInteractions(policy);
    verify(chainData, never()).isFinalizedEpoch(any());
  }

  @Test
  public void validateChainIsConsistentWithWSCheckpoint_checkpointNotFinalized() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final CombinedChainDataClient chainData = mockChainDataClientPriorToCheckpoint();

    SafeFuture<Void> result = validator.validateChainIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    verifyNoMoreInteractions(policy);
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
  }

  @Test
  public void validateChainIsConsistentWithWSCheckpoint_checkpointFinalizedWithMatchingBlock() {
    final UInt64 checkpointEpoch = UInt64.valueOf(100);
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(checkpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final Checkpoint wsCheckpoint = new Checkpoint(checkpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final CombinedChainDataClient chainData =
        mockChainDataClientAfterCheckpoint(wsCheckpoint, checkpointBlock);

    SafeFuture<Void> result = validator.validateChainIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    verifyNoMoreInteractions(policy);
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
    verify(chainData).getFinalizedBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot(spec));
  }

  @Test
  public void validateChainIsConsistentWithWSCheckpoint_checkpointFinalizedWithNonMatchingBlock() {
    final UInt64 checkpointEpoch = UInt64.valueOf(100);
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(checkpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final SignedBeaconBlock otherBlock = dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final Checkpoint wsCheckpoint = new Checkpoint(checkpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final CombinedChainDataClient chainData =
        mockChainDataClientAfterCheckpoint(wsCheckpoint, otherBlock);

    SafeFuture<Void> result = validator.validateChainIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompleted();
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
    verify(chainData).getFinalizedBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot(spec));

    verify(policy)
        .onChainInconsistentWithWeakSubjectivityCheckpoint(
            wsCheckpoint, otherBlock.getRoot(), otherBlock.getSlot());
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void validateChainIsConsistentWithWSCheckpoint_checkpointFinalizedWithMissingBlock() {
    final UInt64 checkpointEpoch = UInt64.valueOf(100);
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(checkpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(checkpointSlot);
    final Checkpoint wsCheckpoint = new Checkpoint(checkpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final CombinedChainDataClient chainData =
        mockChainDataClientAfterCheckpoint(wsCheckpoint, Optional.empty());

    SafeFuture<Void> result = validator.validateChainIsConsistentWithWSCheckpoint(chainData);

    assertThat(result).isCompletedExceptionally();
    verify(chainData).isFinalizedEpoch(wsCheckpoint.getEpoch());
    verify(chainData).getFinalizedBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot(spec));
    verifyNoMoreInteractions(policy);
  }

  @Test
  public void isBlockValid_noWSCheckpoint() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final SignedBeaconBlock block =
        mockBlock(10, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockPriorToCheckpoint() {
    final Checkpoint wsCheckpoint =
        new Checkpoint(UInt64.valueOf(100), Bytes32.fromHexStringLenient("0x01"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final SignedBeaconBlock block =
        mockBlock(10, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAtCheckpointEpochBoundary_matches() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = spec.computeStartSlotAtEpoch(wsCheckpointEpoch);
    final Checkpoint wsCheckpoint =
        new Checkpoint(wsCheckpointEpoch, Bytes32.fromHexStringLenient("0x0100"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final SignedBeaconBlock block =
        mockBlock(wsCheckpointSlot, wsCheckpoint.getRoot(), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAtCheckpointEpochBoundary_mismatch() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = spec.computeStartSlotAtEpoch(wsCheckpointEpoch);
    final Checkpoint wsCheckpoint =
        new Checkpoint(wsCheckpointEpoch, Bytes32.fromHexStringLenient("0x0100"));
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);
    final SignedBeaconBlock block =
        mockBlock(wsCheckpointSlot, Bytes32.fromHexString("0x02"), Bytes32.fromHexString("0x01"));

    assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isFalse();
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAfterCheckpointEpochBoundary_matchingAncestor() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = spec.computeStartSlotAtEpoch(wsCheckpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);

    final Checkpoint wsCheckpoint = new Checkpoint(wsCheckpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    final List<SignedBeaconBlock> blocks =
        dataStructureUtil.randomSignedBeaconBlockSequence(checkpointBlock, 3);
    mockForkChoice(checkpointBlock);
    mockForkChoice(blocks);

    for (SignedBeaconBlock block : blocks) {
      assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isTrue();
    }
  }

  @Test
  public void isBlockValid_withWSCheckpoint_blockAfterCheckpointEpochBoundary_mismatchedAncestor() {
    final UInt64 wsCheckpointEpoch = UInt64.valueOf(100);
    final UInt64 wsCheckpointSlot = spec.computeStartSlotAtEpoch(wsCheckpointEpoch);
    final SignedBeaconBlock checkpointBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);
    final SignedBeaconBlock conflictingBlock =
        dataStructureUtil.randomSignedBeaconBlock(wsCheckpointSlot);

    final Checkpoint wsCheckpoint = new Checkpoint(wsCheckpointEpoch, checkpointBlock.getRoot());
    final WeakSubjectivityConfig config =
        configBuilder().weakSubjectivityCheckpoint(wsCheckpoint).build();
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    final List<SignedBeaconBlock> blocks =
        dataStructureUtil.randomSignedBeaconBlockSequence(conflictingBlock, 3);
    mockForkChoice(conflictingBlock);
    mockForkChoice(blocks);

    for (SignedBeaconBlock block : blocks) {
      assertThat(validator.isBlockValid(block, forkChoiceStrategy)).isFalse();
    }
  }

  @Test
  public void handleValidationFailure_withThrowable() {
    final WeakSubjectivityValidator validator =
        new WeakSubjectivityValidator(config, calculator, policy);

    final String message = "Oops";
    final Throwable error = new RuntimeException("fail");
    validator.handleValidationFailure(message, error);

    verify(policy).onFailedToPerformValidation(message, error);
    verifyNoMoreInteractions(policy);
  }

  private SignedBeaconBlock mockBlock(
      final long slot, final Bytes32 root, final Bytes32 parentRoot) {
    return mockBlock(UInt64.valueOf(slot), root, parentRoot);
  }

  private SignedBeaconBlock mockBlock(
      final UInt64 slot, final Bytes32 root, final Bytes32 parentRoot) {
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    when(block.getSlot()).thenReturn(slot);
    when(block.getRoot()).thenReturn(root);
    when(block.getParentRoot()).thenReturn(parentRoot);

    return block;
  }

  private void mockForkChoice(final SignedBeaconBlock... blocks) {
    mockForkChoice(Arrays.asList(blocks));
  }

  private void mockForkChoice(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      when(forkChoiceStrategy.blockSlot(block.getRoot())).thenReturn(Optional.of(block.getSlot()));
      when(forkChoiceStrategy.blockParentRoot(block.getRoot()))
          .thenReturn(Optional.of(block.getParentRoot()));
      when(forkChoiceStrategy.getAncestor(any(), eq(block.getSlot())))
          .thenReturn(Optional.of(block.getRoot()));
    }
  }

  private CombinedChainDataClient mockChainDataClientPriorToCheckpoint() {
    final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
    when(client.isFinalizedEpoch(any())).thenReturn(false);
    return client;
  }

  private CombinedChainDataClient mockChainDataClientAfterCheckpoint(
      final Checkpoint wsCheckpoint, final SignedBeaconBlock block) {
    return mockChainDataClientAfterCheckpoint(wsCheckpoint, Optional.of(block));
  }

  private CombinedChainDataClient mockChainDataClientAfterCheckpoint(
      final Checkpoint wsCheckpoint, final Optional<SignedBeaconBlock> block) {
    final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
    when(client.isFinalizedEpoch(any())).thenReturn(true);
    when(client.getFinalizedBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot(spec)))
        .thenReturn(SafeFuture.completedFuture(block));

    return client;
  }

  private WeakSubjectivityConfig.Builder configBuilder() {
    return WeakSubjectivityConfig.builder().specProvider(spec);
  }
}
