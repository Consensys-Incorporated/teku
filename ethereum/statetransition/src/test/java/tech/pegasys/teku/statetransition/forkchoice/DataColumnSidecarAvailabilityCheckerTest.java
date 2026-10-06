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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.DelayedExecutorAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class DataColumnSidecarAvailabilityCheckerTest {
  private final Spec spec = mock(Spec.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final SignedBeaconBlock block = mock(SignedBeaconBlock.class);

  private final BeaconBlock beaconBlock = mock(BeaconBlock.class);
  final DataAvailabilitySampler das = mock(DataAvailabilitySampler.class);

  private DataColumnSidecarAvailabilityChecker checker;

  @BeforeEach
  void setup() {
    checker = new DataColumnSidecarAvailabilityChecker(das, spec, recentChainData, block);
    when(block.getMessage()).thenReturn(beaconBlock);
    when(block.getSlot()).thenReturn(UInt64.ONE);
    when(spec.getSlotDurationMillis(any())).thenReturn(10_000);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.empty());
  }

  @Test
  void shouldReturnNotRequiredWhenBeforeFulu() throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_BEFORE_FULU);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnNotRequiredWhenOldEpoch() throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_OLD_EPOCH);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnNotRequiredWhenNoBlobsInTheBlock()
      throws ExecutionException, InterruptedException {

    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.NOT_REQUIRED_NO_BLOBS);
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldReturnInvalidWhenDASSamplerReturnError()
      throws ExecutionException, InterruptedException {
    final RuntimeException exception = new RuntimeException("Error during DAS check");
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any())).thenReturn(SafeFuture.failedFuture(exception));
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notAvailable(exception));
  }

  @Test
  void shouldReturnValidWhenDASSamplerReturnListWithIndices()
      throws ExecutionException, InterruptedException {
    List<UInt64> listOfIndices = Lists.newArrayList(UInt64.valueOf(1), UInt64.valueOf(2));
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any()))
        .thenReturn(SafeFuture.completedFuture(listOfIndices));
    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();
    assertThat(checker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.validResult(listOfIndices));
  }

  @Test
  void shouldReturnNotAvailableOnTimeoutWhenBlockIsWithinDataAvailabilityWindow()
      throws ExecutionException, InterruptedException {
    final UpdatableStore store = mock(UpdatableStore.class);
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any())).thenReturn(new SafeFuture<>());
    when(recentChainData.getStore()).thenReturn(store);
    when(spec.isAvailabilityOfDataColumnSidecarsRequiredAtSlot(any(), any())).thenReturn(true);
    when(spec.getSlotDurationMillis(any())).thenReturn(1);

    final DataColumnSidecarAvailabilityChecker timedOutChecker =
        new DataColumnSidecarAvailabilityChecker(das, spec, recentChainData, block);

    assertThat(timedOutChecker.initiateDataAvailabilityCheck()).isTrue();

    final DataAndValidationResult<UInt64> result =
        timedOutChecker.getAvailabilityCheckResult().get();
    assertThat(result.isNotAvailable()).isTrue();
  }

  @Test
  void shouldReturnNotRequiredOnTimeoutWhenBlockIsOutsideDataAvailabilityWindow()
      throws ExecutionException, InterruptedException {
    final UpdatableStore store = mock(UpdatableStore.class);
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(any(), any())).thenReturn(new SafeFuture<>());
    when(recentChainData.getStore()).thenReturn(store);
    when(spec.isAvailabilityOfDataColumnSidecarsRequiredAtSlot(any(), any())).thenReturn(false);
    when(spec.getSlotDurationMillis(any())).thenReturn(1);

    final DataColumnSidecarAvailabilityChecker timedOutChecker =
        new DataColumnSidecarAvailabilityChecker(das, spec, recentChainData, block);

    assertThat(timedOutChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThat(timedOutChecker.getAvailabilityCheckResult().get())
        .isEqualTo(DataAndValidationResult.notRequired());
  }

  @Test
  void shouldNotPoisonSharedTrackerFutureOnTimeout()
      throws ExecutionException, InterruptedException {
    final UpdatableStore store = mock(UpdatableStore.class);
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    final SafeFuture<List<UInt64>> sharedTrackerFuture = new SafeFuture<>();
    when(das.checkDataAvailability(any(), any())).thenReturn(sharedTrackerFuture);
    when(recentChainData.getStore()).thenReturn(store);
    when(spec.isAvailabilityOfDataColumnSidecarsRequiredAtSlot(any(), any())).thenReturn(true);
    when(spec.getSlotDurationMillis(any())).thenReturn(1);

    final DataColumnSidecarAvailabilityChecker timedOutChecker =
        new DataColumnSidecarAvailabilityChecker(das, spec, recentChainData, block);
    timedOutChecker.initiateDataAvailabilityCheck();

    // wait for the checker to time out
    final DataAndValidationResult<UInt64> result =
        timedOutChecker.getAvailabilityCheckResult().get();
    assertThat(result.isNotAvailable()).isTrue();

    // the shared tracker future must not have been completed by the timeout
    assertThat(sharedTrackerFuture).isNotDone();
  }

  @Test
  void shouldNotExtendTimeoutForBlockExactlyOneEpochOld() {
    final UInt64 currentSlot = UInt64.valueOf(33);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));
    when(recentChainData.getStore()).thenReturn(store);
    when(spec.getSlotsPerEpoch(currentSlot)).thenReturn(32);
    when(spec.getSlotDurationMillis(UInt64.ONE)).thenReturn(1);
    when(spec.isAvailabilityOfDataColumnSidecarsRequiredAtSlot(store, UInt64.ONE)).thenReturn(true);
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(UInt64.ONE, block.getRoot())).thenReturn(new SafeFuture<>());

    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();

    assertThat(checker.getAvailabilityCheckResult())
        .succeedsWithin(Duration.ofSeconds(1))
        .satisfies(result -> assertThat(result.isNotAvailable()).isTrue());
  }

  @Test
  void shouldExtendTimeoutForBlockOlderThanOneEpoch() {
    final List<UInt64> sampledColumns = List.of(UInt64.ONE);
    when(block.getSlot()).thenReturn(UInt64.ONE);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(UInt64.valueOf(34)));
    when(spec.getSlotsPerEpoch(UInt64.valueOf(34))).thenReturn(32);
    when(spec.getSlotDurationMillis(UInt64.ONE)).thenReturn(100);
    when(das.checkSamplingEligibility(block.getMessage()))
        .thenReturn(DataAvailabilitySampler.SamplingEligibilityStatus.REQUIRED);
    when(das.checkDataAvailability(UInt64.ONE, block.getRoot()))
        .thenReturn(
            DelayedExecutorAsyncRunner.create()
                .runAfterDelay(
                    () -> SafeFuture.completedFuture(sampledColumns), Duration.ofMillis(250)));

    assertThat(checker.initiateDataAvailabilityCheck()).isTrue();

    assertThat(checker.getAvailabilityCheckResult())
        .succeedsWithin(Duration.ofSeconds(1))
        .isEqualTo(DataAndValidationResult.validResult(sampledColumns));
  }
}
