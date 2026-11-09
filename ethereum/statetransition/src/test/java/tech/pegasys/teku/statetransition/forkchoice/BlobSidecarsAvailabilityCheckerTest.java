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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import com.google.common.collect.ImmutableSortedMap;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityValidationResult;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BlobSidecarsAvailabilityCheckerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalDeneb());

  private final Spec spec = mock(Spec.class);
  private final SpecVersion specVersion = mock(SpecVersion.class);
  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
      mock(BlockBlobSidecarsTracker.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private SignedBeaconBlock block;
  private List<BlobSidecar> blobSidecarsComplete;

  private final SafeFuture<Void> trackerCompletionFuture = new SafeFuture<>();

  private BlobSidecarsAvailabilityChecker blobSidecarsAvailabilityChecker;

  @BeforeEach
  void setUp() {
    when(spec.atSlot(any())).thenReturn(specVersion);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(recentChainData.getStore()).thenReturn(store);
    when(blockBlobSidecarsTracker.getCompletionFuture()).thenReturn(trackerCompletionFuture);
  }

  @Test
  void shouldVerifyValidAvailableBlobs() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        runAvailabilityCheck();

    assertAvailable(availabilityCheckResult);
  }

  @Test
  void shouldVerifyInvalidBlobsDueToWrongBlockHeader() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(false);

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(
        availabilityCheckResult,
        blobSidecarsComplete,
        Optional.of(
            new IllegalStateException("Blob sidecars block header does not match signed block")));
  }

  @Test
  void shouldVerifyInvalidBlobsDueToWrongKzg() throws Exception {
    prepareInitialAvailability();

    when(miscHelpers.verifyBlobKzgProofBatch(any())).thenReturn(false);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.empty());
  }

  @Test
  void shouldVerifyInvalidBlobsWhenKzgValidationThrows() throws Exception {
    prepareInitialAvailability();

    final RuntimeException error = new RuntimeException("oops");

    when(miscHelpers.verifyBlobKzgProofBatch(any())).thenThrow(error);

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.of(error));
  }

  @Test
  void shouldVerifyInvalidBlobsWhenCompletenessValidationThrows() throws Exception {
    prepareInitialAvailability();

    final RuntimeException error = new RuntimeException("oops");

    when(miscHelpers.verifyBlobKzgProofBatch(any())).thenReturn(true);
    when(miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(anyList(), any()))
        .thenReturn(true);
    doThrow(error).when(miscHelpers).verifyBlobSidecarCompleteness(anyList(), any());

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        runAvailabilityCheck();

    assertInvalid(availabilityCheckResult, blobSidecarsComplete, Optional.of(error));
  }

  @Test
  void shouldFailIfTrackerCompletesWithFailure() {
    prepareInitialAvailability();

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // let the tracker complete with all blobSidecars
    trackerCompletionFuture.completeExceptionally(new RuntimeException("oops"));

    assertThatSafeFuture(availabilityCheckResult).isCompletedExceptionallyWithMessage("oops");
  }

  @Test
  void shouldReturnNotAvailableOnTimeout() throws Exception {
    prepareForImmediateTimeout();

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    Waiter.waitFor(availabilityCheckResult);

    assertNotAvailableDueToTimeout(availabilityCheckResult);
  }

  @Test
  void shouldReturnInvalidWhenBlockIsOutsideAvailabilityWindowButInvalidBlobsAreProvided()
      throws Exception {
    prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow();

    trackerCompletionFuture.complete(null);

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    Waiter.waitFor(availabilityCheckResult);

    assertInvalid(availabilityCheckResult, List.of(), Optional.empty());
  }

  @Test
  void shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWindowNoWait() throws Exception {
    prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow();

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertNotRequired(availabilityCheckResult);
  }

  @Test
  void shouldReturnNotRequiredWhenBlockIsOutsideAvailabilityWhileWaiting() throws Exception {
    prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow();

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot()))
        .thenReturn(true) // first check, inside DA
        .thenReturn(false); // after timeout, outside DA

    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    Waiter.waitFor(availabilityCheckResult);

    assertNotRequired(availabilityCheckResult);
  }

  private SafeFuture<DataAndValidationResult<BlobSidecar>> runAvailabilityCheck() throws Exception {
    final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityCheckResult =
        blobSidecarsAvailabilityChecker.getAvailabilityCheckResult();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();

    // initiate availability check
    assertThat(blobSidecarsAvailabilityChecker.initiateDataAvailabilityCheck()).isTrue();

    assertThatSafeFuture(availabilityCheckResult).isNotCompleted();
    verify(blockBlobSidecarsTracker, never()).getBlobSidecars();

    // let the tracker complete with all blobSidecars
    completeTrackerWith(blobSidecarsComplete);

    Waiter.waitFor(availabilityCheckResult);

    return availabilityCheckResult;
  }

  private void assertInvalid(
      final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityOrValidityCheck,
      final List<BlobSidecar> invalidBlobs,
      final Optional<Throwable> cause) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.validationResult() == AvailabilityValidationResult.INVALID,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.data().equals(invalidBlobs), "doesn't have blob sidecars")
        .isCompletedWithValueMatching(
            result -> {
              if (cause.isEmpty() != result.cause().isEmpty()) {
                return false;
              }
              return result
                  .cause()
                  .map(
                      resultCause ->
                          resultCause.getClass().equals(cause.get().getClass())
                              && Objects.equals(resultCause.getMessage(), cause.get().getMessage())
                              && Objects.equals(resultCause.getCause(), cause.get().getCause()))
                  .orElse(true);
            },
            "matches the cause");
  }

  private void assertNotRequired(
      final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(DataAndValidationResult::isNotRequired, "is not required")
        .isCompletedWithValueMatching(
            result -> result.data().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertNotAvailableDueToTimeout(
      final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityOrValidityCheck) {
    assertNotAvailable(availabilityOrValidityCheck);
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(
            result -> result.cause().orElseThrow() instanceof TimeoutException);
  }

  private void assertNotAvailable(
      final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(result -> !result.isValid(), "is not valid")
        .isCompletedWithValueMatching(
            result -> result.validationResult() == AvailabilityValidationResult.NOT_AVAILABLE,
            "is not available")
        .isCompletedWithValueMatching(
            result -> result.data().isEmpty(), "doesn't have blob sidecars");
  }

  private void assertAvailable(
      final SafeFuture<DataAndValidationResult<BlobSidecar>> availabilityOrValidityCheck) {
    assertThat(availabilityOrValidityCheck)
        .isCompletedWithValueMatching(DataAndValidationResult::isValid, "is valid")
        .isCompletedWithValueMatching(
            result -> result.validationResult() == AvailabilityValidationResult.VALID, "is valid")
        .isCompletedWithValueMatching(
            result -> result.data().equals(blobSidecarsComplete), "has blob sidecars");
  }

  private void prepareForImmediateTimeout() {
    prepareInitialAvailability(Optional.empty(), Duration.ZERO);
  }

  private void prepareInitialAvailability() {
    prepareInitialAvailability(Optional.empty(), Duration.ofSeconds(30));
  }

  private void prepareInitialAvailability(
      final Optional<SignedBeaconBlock> providedBlock, final Duration timeout) {
    block = providedBlock.orElse(dataStructureUtil.randomSignedBeaconBlockWithCommitments(4));
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(true);

    when(blockBlobSidecarsTracker.getBlock()).thenReturn(Optional.of(block));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(ImmutableSortedMap.of());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new BlobSidecarsAvailabilityChecker(
            spec, recentChainData, blockBlobSidecarsTracker, timeout);
  }

  private void completeTrackerWith(final List<BlobSidecar> blobSidecars) {
    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecars.forEach(blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(mapBuilder.build());
    when(blockBlobSidecarsTracker.isComplete()).thenReturn(true);
    trackerCompletionFuture.complete(null);
  }

  private void prepareForImmediateTimeoutWithBlockAndBlobSidecarsOutsideAvailabilityWindow() {
    block = dataStructureUtil.randomSignedBeaconBlock();
    blobSidecarsComplete = dataStructureUtil.randomBlobSidecarsForBlock(block);

    final ImmutableSortedMap.Builder<UInt64, BlobSidecar> mapBuilder =
        ImmutableSortedMap.naturalOrder();
    blobSidecarsComplete.forEach(
        blobSidecar -> mapBuilder.put(blobSidecar.getIndex(), blobSidecar));

    when(spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(store, block.getSlot())).thenReturn(false);
    when(blockBlobSidecarsTracker.getBlock()).thenReturn(Optional.of(block));
    when(blockBlobSidecarsTracker.getBlobSidecars()).thenReturn(ImmutableSortedMap.of());
    when(blockBlobSidecarsTracker.getSlotAndBlockRoot()).thenReturn(block.getSlotAndBlockRoot());

    blobSidecarsAvailabilityChecker =
        new BlobSidecarsAvailabilityChecker(
            spec, recentChainData, blockBlobSidecarsTracker, Duration.ZERO);
  }
}
