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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

class DefaultExecutionPayloadManagerTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final ExecutionPayloadGossipValidator executionPayloadGossipValidator =
      mock(ExecutionPayloadGossipValidator.class);
  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final ReceivedExecutionPayloadEventsChannel
      receivedExecutionPayloadEventsChannelPublisher =
          mock(ReceivedExecutionPayloadEventsChannel.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final Set<Bytes32> blockRootsWithInvalidExecutionPayload = new HashSet<>();
  private final UpdatableStore store = mock(UpdatableStore.class);

  private Optional<SignedExecutionPayloadEnvelope> publishedExecutionPayload = Optional.empty();

  private final DefaultExecutionPayloadManager executionPayloadManager =
      new DefaultExecutionPayloadManager(
          spec,
          asyncRunner,
          executionPayloadGossipValidator,
          forkChoice,
          executionLayer,
          receivedExecutionPayloadEventsChannelPublisher,
          recentChainData,
          blockRootsWithInvalidExecutionPayload,
          executionPayload -> {
            publishedExecutionPayload = Optional.of(executionPayload);
            return SafeFuture.COMPLETE;
          });

  private final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);

  @BeforeEach
  void setUp() {
    when(recentChainData.getGenesisTimeMillis()).thenReturn(UInt64.ZERO);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getTimeInMillis()).thenReturn(UInt64.ZERO);
  }

  @Test
  public void shouldDelegateIsExecutionPayloadRecentlySeenToTheGossipValidator() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    when(executionPayloadGossipValidator.isPayloadSeen(beaconBlockRoot)).thenReturn(true);
    assertThat(executionPayloadManager.isExecutionPayloadRecentlySeen(beaconBlockRoot)).isTrue();
  }

  @Test
  public void getParentExecutionRequests_shouldReturnImportedRequestsForFullParent() {
    final UInt64 slot = UInt64.valueOf(42);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final SignedBlindedExecutionPayloadEnvelope parentExecutionPayload =
        dataStructureUtil.randomSignedBlindedExecutionPayloadEnvelope(slot.longValue());
    when(recentChainData.retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot))
        .thenReturn(completedFuture(Optional.of(parentExecutionPayload)));

    final SafeFuture<ExecutionRequests> result =
        executionPayloadManager.getParentExecutionRequestsForBlock(
            slot, parentRoot, PAYLOAD_STATUS_FULL);

    assertThat(result).isCompletedWithValue(parentExecutionPayload.getExecutionRequests());
    verify(recentChainData).retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot);
  }

  @Test
  public void getParentExecutionRequestsForBlock_shouldReturnDefaultRequestsForNonFullParent() {
    final UInt64 slot = UInt64.valueOf(42);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final ExecutionRequests defaultExecutionRequests = defaultExecutionRequests(slot);

    assertThat(
            executionPayloadManager.getParentExecutionRequestsForBlock(
                slot, parentRoot, PAYLOAD_STATUS_EMPTY))
        .isCompletedWithValue(defaultExecutionRequests);
    assertThat(
            executionPayloadManager.getParentExecutionRequestsForBlock(
                slot, parentRoot, PAYLOAD_STATUS_PENDING))
        .isCompletedWithValue(defaultExecutionRequests);
    verify(recentChainData, never()).retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot);
  }

  @Test
  public void getParentExecutionRequestsForBlock_shouldThrowForFullParentWhenPayloadIsMissing() {
    final UInt64 slot = UInt64.valueOf(42);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    when(recentChainData.retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<ExecutionRequests> result =
        executionPayloadManager.getParentExecutionRequestsForBlock(
            slot, parentRoot, PAYLOAD_STATUS_FULL);

    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(IllegalStateException.class)
        .hasMessageContaining(parentRoot.toString())
        .hasMessageContaining(slot.toString());
    verify(recentChainData).retrieveSignedBlindedExecutionPayloadByBlockRoot(parentRoot);
  }

  @Test
  public void shouldValidateAndImport() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertDebugLog("Successfully imported execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);
    assertExecutionPayloadSeenBeforeDeadline(signedExecutionPayload);
  }

  @Test
  public void shouldNotMarkExecutionPayloadSeenBeforeDeadlineWhenReceivedAfterTheDeadline() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(
            signedExecutionPayload, payloadDueDeadlineMillis(signedExecutionPayload).plus(1));

    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
    assertExecutionPayloadNotSeenBeforeDeadline(signedExecutionPayload);
  }

  @Test
  public void shouldNotImportIfValidationFails() {
    final InternalValidationResult rejectedResult = reject("oopsy");
    givenValidationResult(signedExecutionPayload, rejectedResult);

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    asyncRunner.executeDueActions();

    verifyNoInteractions(forkChoice);
    assertThat(resultFuture).isCompletedWithValue(rejectedResult);
  }

  @Test
  public void shouldHandleInternalErrorsWhileImporting() {
    givenValidationResult(signedExecutionPayload, ACCEPT);
    when(forkChoice.onExecutionPayloadEnvelope(
            signedExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenThrow(new IllegalStateException("oopsy"));

    final SafeFuture<InternalValidationResult> resultFuture =
        validateAndImport(signedExecutionPayload);

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultExecutionPayloadManager.class)) {
      asyncRunner.executeDueActions();
      logCaptor.assertErrorLog("Internal error while importing execution payload");
    }

    assertThat(resultFuture).isCompletedWithValue(ACCEPT);
  }

  @Test
  public void shouldPreferPendingExecutionPayloadReceivedBeforeDeadlineOverEarlierLatePayload() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope lateExecutionPayload =
        signedExecutionPayloadForBlock(block);
    final SignedExecutionPayloadEnvelope earlyExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(lateExecutionPayload, SAVE_FOR_FUTURE);
    givenValidationResult(earlyExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(
        lateExecutionPayload, payloadDueDeadlineMillis(lateExecutionPayload).plus(1));
    validateAndImportAndJoin(earlyExecutionPayload, UInt64.ZERO);

    givenValidationResult(earlyExecutionPayload, ACCEPT);
    givenSuccessfulImport(earlyExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(earlyExecutionPayload, false);
    verify(forkChoice)
        .onExecutionPayloadEnvelope(
            earlyExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    verify(forkChoice, never())
        .onExecutionPayloadEnvelope(
            lateExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    assertThat(publishedExecutionPayload).hasValue(earlyExecutionPayload);
    assertExecutionPayloadSeenBeforeDeadline(earlyExecutionPayload);
  }

  @Test
  public void shouldNotReplacePendingExecutionPayloadReceivedBeforeDeadlineWithLatePayload() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope earlyExecutionPayload =
        signedExecutionPayloadForBlock(block);
    final SignedExecutionPayloadEnvelope lateExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(earlyExecutionPayload, SAVE_FOR_FUTURE);
    givenValidationResult(lateExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(earlyExecutionPayload, UInt64.ZERO);
    validateAndImportAndJoin(
        lateExecutionPayload, payloadDueDeadlineMillis(lateExecutionPayload).plus(1));

    givenValidationResult(earlyExecutionPayload, ACCEPT);
    givenSuccessfulImport(earlyExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(earlyExecutionPayload, false);
    verify(forkChoice)
        .onExecutionPayloadEnvelope(
            earlyExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    verify(forkChoice, never())
        .onExecutionPayloadEnvelope(
            lateExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    assertThat(publishedExecutionPayload).hasValue(earlyExecutionPayload);
    assertExecutionPayloadSeenBeforeDeadline(earlyExecutionPayload);
  }

  @Test
  public void shouldReplacePendingExecutionPayloadReceivedBeforeDeadlineWithLaterBeforeDeadline() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope firstExecutionPayload =
        signedExecutionPayloadForBlock(block);
    final SignedExecutionPayloadEnvelope secondExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(firstExecutionPayload, SAVE_FOR_FUTURE);
    givenValidationResult(secondExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(firstExecutionPayload, UInt64.ZERO);
    validateAndImportAndJoin(secondExecutionPayload, UInt64.ONE);

    givenValidationResult(firstExecutionPayload, reject("unexpected"));
    givenValidationResult(secondExecutionPayload, ACCEPT);
    givenSuccessfulImport(secondExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(secondExecutionPayload, false);
    verify(forkChoice)
        .onExecutionPayloadEnvelope(
            secondExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    verify(forkChoice, never())
        .onExecutionPayloadEnvelope(
            firstExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher));
    assertThat(publishedExecutionPayload).hasValue(secondExecutionPayload);
    assertExecutionPayloadSeenBeforeDeadline(secondExecutionPayload);
  }

  @Test
  public void shouldProcessExecutionPayloadWhichHasBeenReceivedBeforeTheBlock() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(42);
    final SignedExecutionPayloadEnvelope signedExecutionPayload =
        signedExecutionPayloadForBlock(block);
    givenValidationResult(signedExecutionPayload, SAVE_FOR_FUTURE);

    validateAndImportAndJoin(signedExecutionPayload, UInt64.ZERO);

    asyncRunner.executeDueActions();
    verifyNoInteractions(receivedExecutionPayloadEventsChannelPublisher);

    givenValidationResult(signedExecutionPayload, ACCEPT);
    givenSuccessfulImport(signedExecutionPayload);
    executionPayloadManager.onBlockImported(block, false);

    asyncRunner.executeDueActions();

    verify(receivedExecutionPayloadEventsChannelPublisher)
        .onExecutionPayloadImported(signedExecutionPayload, false);
    assertThat(publishedExecutionPayload).hasValue(signedExecutionPayload);
    assertExecutionPayloadSeenBeforeDeadline(signedExecutionPayload);
  }

  @Test
  public void shouldCacheInvalidExecutionPayloadWhenImportFailsVerification() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedVerification(new RuntimeException("invalid"));
    when(forkChoice.onExecutionPayloadEnvelope(
            signedExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenReturn(completedFuture(failedImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload, true);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(blockRootsWithInvalidExecutionPayload)
        .contains(signedExecutionPayload.getBeaconBlockRoot());
  }

  @Test
  public void shouldCacheInvalidExecutionPayloadWhenDataAvailabilityCheckIsInvalid() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedDataAvailabilityCheckInvalid(
            Optional.of(new RuntimeException("invalid")));
    when(forkChoice.onExecutionPayloadEnvelope(
            signedExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenReturn(completedFuture(failedImportResult));

    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload, true);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(blockRootsWithInvalidExecutionPayload)
        .contains(signedExecutionPayload.getBeaconBlockRoot());
  }

  @Test
  public void shouldNotCacheInvalidExecutionPayloadWhenCommitmentNotVerified() {
    final ExecutionPayloadImportResult failedImportResult =
        ExecutionPayloadImportResult.failedVerification(new RuntimeException("invalid"));
    when(forkChoice.onExecutionPayloadEnvelope(
            signedExecutionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenReturn(completedFuture(failedImportResult));

    // Imports that did not verify the payload commitment (e.g. sync or RPC-by-root) must not poison
    // the invalid-payload cache, even when the import fails verification.
    final SafeFuture<ExecutionPayloadImportResult> resultFuture =
        executionPayloadManager.importExecutionPayload(signedExecutionPayload, false);
    asyncRunner.executeDueActions();

    assertThat(resultFuture).isCompletedWithValue(failedImportResult);
    assertThat(blockRootsWithInvalidExecutionPayload)
        .doesNotContain(signedExecutionPayload.getBeaconBlockRoot());
  }

  private void givenValidationResult(
      final SignedExecutionPayloadEnvelope executionPayload,
      final InternalValidationResult validationResult) {
    when(executionPayloadGossipValidator.validate(executionPayload, Optional.empty()))
        .thenReturn(completedFuture(validationResult));
  }

  private void givenSuccessfulImport(final SignedExecutionPayloadEnvelope executionPayload) {
    when(forkChoice.onExecutionPayloadEnvelope(
            executionPayload,
            executionLayer,
            Optional.of(receivedExecutionPayloadEventsChannelPublisher)))
        .thenReturn(completedFuture(ExecutionPayloadImportResult.successful(executionPayload)));
  }

  private SafeFuture<InternalValidationResult> validateAndImport(
      final SignedExecutionPayloadEnvelope executionPayload) {
    return executionPayloadManager.validateAndImportExecutionPayload(executionPayload);
  }

  private SafeFuture<InternalValidationResult> validateAndImport(
      final SignedExecutionPayloadEnvelope executionPayload, final UInt64 arrivalTimestamp) {
    return executionPayloadManager.validateAndImportExecutionPayload(
        executionPayload, Optional.of(arrivalTimestamp));
  }

  private void validateAndImportAndJoin(
      final SignedExecutionPayloadEnvelope executionPayload, final UInt64 arrivalTimestamp) {
    SafeFutureAssert.safeJoin(validateAndImport(executionPayload, arrivalTimestamp));
  }

  private UInt64 payloadDueDeadlineMillis(final SignedExecutionPayloadEnvelope executionPayload) {
    final UInt64 slot = executionPayload.getSlot();
    return spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis())
        .plus(spec.getPayloadDueMillis(slot).orElseThrow());
  }

  private SignedExecutionPayloadEnvelope signedExecutionPayloadForBlock(
      final SignedBeaconBlock block) {
    return dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(block);
  }

  private ExecutionRequests defaultExecutionRequests(final UInt64 slot) {
    return SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
        .getExecutionRequestsSchema()
        .getDefault();
  }

  private void assertExecutionPayloadSeenBeforeDeadline(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(
                executionPayload.getBeaconBlockRoot()))
        .isTrue();
  }

  private void assertExecutionPayloadNotSeenBeforeDeadline(
      final SignedExecutionPayloadEnvelope executionPayload) {
    assertThat(
            executionPayloadManager.isExecutionPayloadSeenBeforeDeadline(
                executionPayload.getBeaconBlockRoot()))
        .isFalse();
  }
}
