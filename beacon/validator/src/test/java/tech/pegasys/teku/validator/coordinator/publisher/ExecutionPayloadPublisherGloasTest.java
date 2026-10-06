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

package tech.pegasys.teku.validator.coordinator.publisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.networking.eth2.gossip.DataColumnSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;
import tech.pegasys.teku.validator.coordinator.DataColumnSidecarCreationException;
import tech.pegasys.teku.validator.coordinator.ExecutionPayloadFactory;

class ExecutionPayloadPublisherGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionPayloadFactory executionPayloadFactory =
      mock(ExecutionPayloadFactory.class);
  private final ExecutionPayloadGossipChannel executionPayloadGossipChannel =
      mock(ExecutionPayloadGossipChannel.class);
  private final DataColumnSidecarGossipChannel dataColumnSidecarGossipChannel =
      mock(DataColumnSidecarGossipChannel.class);
  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final ExecutionPayloadPublisherGloas executionPayloadPublisher =
      new ExecutionPayloadPublisherGloas(
          executionPayloadFactory,
          executionPayloadGossipChannel,
          dataColumnSidecarGossipChannel,
          executionPayloadManager,
          recentChainData);

  final SignedExecutionPayloadEnvelope signedExecutionPayload =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(42);
  final List<DataColumnSidecar> dataColumnSidecars =
      List.of(dataStructureUtil.randomDataColumnSidecar());

  @BeforeEach
  public void setUp() {
    // by default the block commits to blobs, so sidecars are required to publish
    when(recentChainData.retrieveBlockByRoot(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    dataStructureUtil.randomSignedBeaconBlockWithCommitments(1).getMessage())));
    when(executionPayloadManager.validateAndImportExecutionPayloadForBroadcast(
            eq(signedExecutionPayload), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ExecutionPayloadManager.ValidateAndImportResult(
                    InternalValidationResult.ACCEPT,
                    Optional.of(
                        SafeFuture.completedFuture(
                            ExecutionPayloadImportResult.successful(signedExecutionPayload))))));
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(executionPayloadGossipChannel.publishExecutionPayload(signedExecutionPayload))
        .thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void publishSignedExecutionPayload_shouldValidateAndPublish() {
    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.success(
                signedExecutionPayload.getBeaconBlockRoot()));

    verify(executionPayloadGossipChannel).publishExecutionPayload(signedExecutionPayload);
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  public void publishSignedExecutionPayload_shouldRejectWithoutPublishingWhenBlobDataIsNotCached() {
    final DataColumnSidecarCreationException error =
        DataColumnSidecarCreationException.noCachedBlobData(signedExecutionPayload.getSlot());
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.failedFuture(error));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.rejected(
                signedExecutionPayload.getBeaconBlockRoot(), error.getMessage()));

    // broadcast validation runs concurrently with the sidecar creation, but nothing is published
    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void
      publishSignedExecutionPayload_shouldRejectWithoutPublishingWhenEnvelopeDoesNotMatchCachedPayload() {
    final DataColumnSidecarCreationException error =
        DataColumnSidecarCreationException.cachedPayloadMismatch(signedExecutionPayload.getSlot());
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.failedFuture(error));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.rejected(
                signedExecutionPayload.getBeaconBlockRoot(), error.getMessage()));

    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldPublishWithoutSidecarsWhenBlockHasNoBlobs() {
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(
            SafeFuture.failedFuture(
                DataColumnSidecarCreationException.noCachedBlobData(
                    signedExecutionPayload.getSlot())));
    when(recentChainData.retrieveBlockByRoot(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments().getMessage())));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.success(
                signedExecutionPayload.getBeaconBlockRoot()));

    verify(executionPayloadGossipChannel).publishExecutionPayload(signedExecutionPayload);
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(List.of(), RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  public void
      publishSignedExecutionPayload_shouldPublishWithoutSidecarsWhenCachedPayloadDoesNotMatchAndBlockHasNoBlobs() {
    // a mismatched cache only matters when blobs have to be attached, and a block committing to no
    // blobs needs none: this is the failover case where the block was produced on another node
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(
            SafeFuture.failedFuture(
                DataColumnSidecarCreationException.cachedPayloadMismatch(
                    signedExecutionPayload.getSlot())));
    when(recentChainData.retrieveBlockByRoot(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments().getMessage())));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.success(
                signedExecutionPayload.getBeaconBlockRoot()));

    verify(executionPayloadGossipChannel).publishExecutionPayload(signedExecutionPayload);
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(List.of(), RemoteOrigin.LOCAL_PROPOSAL);
  }

  @Test
  public void publishSignedExecutionPayload_shouldNotPublishInvalidEnvelopeWhenBlockHasNoBlobs() {
    // publishing on a sidecar failure is only safe because broadcast validation is checked first,
    // so an envelope that is invalid in its own right must never reach the network
    when(executionPayloadManager.validateAndImportExecutionPayloadForBroadcast(
            eq(signedExecutionPayload), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ExecutionPayloadManager.ValidateAndImportResult(
                    InternalValidationResult.reject("oopsy"), Optional.empty())));
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(
            SafeFuture.failedFuture(
                DataColumnSidecarCreationException.cachedPayloadMismatch(
                    signedExecutionPayload.getSlot())));
    when(recentChainData.retrieveBlockByRoot(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments().getMessage())));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.rejected(
                signedExecutionPayload.getBeaconBlockRoot(), "Failed broadcast validation: oopsy"));

    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldRejectWhenBlobDataIsNotCachedAndBlockIsUnknown() {
    final DataColumnSidecarCreationException error =
        DataColumnSidecarCreationException.noCachedBlobData(signedExecutionPayload.getSlot());
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.failedFuture(error));
    when(recentChainData.retrieveBlockByRoot(signedExecutionPayload.getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.rejected(
                signedExecutionPayload.getBeaconBlockRoot(), error.getMessage()));

    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldFailBeforePublishingOnUnexpectedSidecarError() {
    final IllegalStateException error = new IllegalStateException("boom");
    when(executionPayloadFactory.createDataColumnSidecars(signedExecutionPayload))
        .thenReturn(SafeFuture.failedFuture(error));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedExceptionallyWith(error);

    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldReturnRejectedResultIfBroadcastValidationFails() {
    when(executionPayloadManager.validateAndImportExecutionPayloadForBroadcast(
            eq(signedExecutionPayload), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ExecutionPayloadManager.ValidateAndImportResult(
                    InternalValidationResult.reject("oopsy"), Optional.empty())));
    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.rejected(
                signedExecutionPayload.getBeaconBlockRoot(), "Failed broadcast validation: oopsy"));

    verifyNoInteractions(executionPayloadGossipChannel, dataColumnSidecarGossipChannel);
  }

  @Test
  public void publishSignedExecutionPayload_shouldReturnNotImportedWhenIntegrationFails() {
    when(executionPayloadManager.validateAndImportExecutionPayloadForBroadcast(
            eq(signedExecutionPayload), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new ExecutionPayloadManager.ValidateAndImportResult(
                    InternalValidationResult.ACCEPT,
                    Optional.of(
                        SafeFuture.completedFuture(
                            ExecutionPayloadImportResult.failedExecution(
                                new RuntimeException("el error")))))));

    SafeFutureAssert.assertThatSafeFuture(
            executionPayloadPublisher.publishSignedExecutionPayload(signedExecutionPayload))
        .isCompletedWithValue(
            PublishSignedExecutionPayloadResult.notImported(
                signedExecutionPayload.getBeaconBlockRoot(), "FAILED_EXECUTION"));

    verify(executionPayloadGossipChannel).publishExecutionPayload(signedExecutionPayload);
    verify(dataColumnSidecarGossipChannel)
        .publishDataColumnSidecars(dataColumnSidecars, RemoteOrigin.LOCAL_PROPOSAL);
  }
}
