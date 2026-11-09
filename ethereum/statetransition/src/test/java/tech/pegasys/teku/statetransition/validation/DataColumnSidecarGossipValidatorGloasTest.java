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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.util.GloasTrackingKey;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

public class DataColumnSidecarGossipValidatorGloasTest
    extends AbstractDataColumnSidecarGossipValidatorTest {
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);

  private Spec spec;
  private Bytes32 beaconBlockRoot;

  @Override
  public Spec createSpec(final Consumer<SpecConfigBuilder> configAdapter) {
    return TestSpecFactory.createMinimalGloas(configAdapter);
  }

  @BeforeEach
  void setup() {
    spec = createSpec(builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
    this.dataStructureUtil = new DataStructureUtil(spec);

    this.dataColumnSidecarGossipValidator =
        DataColumnSidecarGossipValidator.create(
            spec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProvider,
            metricsSystemStub,
            stubTimeProvider);
    slot = UInt64.valueOf(2);
    index = UInt64.valueOf(1);
    beaconBlockRoot = dataStructureUtil.randomBytes32();

    // Create a Gloas data column sidecar with fixed slot and index
    dataColumnSidecar =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(index)
            .beaconBlockRoot(beaconBlockRoot)
            .build();

    // Default mocks for ACCEPT
    when(gossipValidationHelper.isSlotFinalized(slot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(slot)).thenReturn(false);
    // Gloas creates synthetic header with Bytes32.ZERO parent, need to mock these checks
    when(gossipValidationHelper.isBlockAvailable(any(Bytes32.class))).thenReturn(true);
    // Mock getSlotForBlockRoot to return the sidecar's slot (not parent slot)
    when(gossipValidationHelper.getSlotForBlockRoot(any(Bytes32.class)))
        .thenReturn(Optional.of(slot));
    when(gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(any(), any()))
        .thenReturn(true);
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(any(DataColumnSidecar.class)))
        .thenAnswer(
            invocation -> {
              final DataColumnSidecar sidecar = invocation.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.of(createCommitments(sidecar.getColumn().size())));
            });
  }

  private SszList<SszKZGCommitment> createCommitments(final int commitmentCount) {
    return dataStructureUtil
        .randomBeaconBlockBodyWithCommitments(commitmentCount)
        .getOptionalBlobKzgCommitments()
        .orElseThrow();
  }

  @Test
  void shouldAcceptValidGloasDataColumnSidecar() {
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void shouldResolveBlobKzgCommitmentsProviderFromSupplierAtValidationTime() {
    final AtomicReference<BlobKzgCommitmentsProvider> blobKzgCommitmentsProviderRef =
        new AtomicReference<>();
    final DataColumnSidecarGossipValidator validator =
        DataColumnSidecarGossipValidator.create(
            spec,
            invalidBlocks,
            gossipValidationHelper,
            blobKzgCommitmentsProviderRef::get,
            new StubMetricsSystem(),
            stubTimeProvider);

    blobKzgCommitmentsProviderRef.set(blobKzgCommitmentsProvider);

    SafeFutureAssert.assertThatSafeFuture(validator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void shouldRejectWhenDataColumnSidecarKzgCommitmentDoNotMatchBid() {
    // block with fewer commitments than the sidecar's column size
    final int insufficientCommitmentCount = dataColumnSidecar.getColumn().size() - 1;
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(dataColumnSidecar))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(createCommitments(insufficientCommitmentCount))));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(reject("Invalid DataColumnSidecar"));
  }

  @Test
  void shouldIgnoreWhenIsNotFirstValidForGloasTrackingKey() {
    // First validation - should accept and add tracking key to the set
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Second validation with same sidecar - should ignore
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldNotValidateHeaderSignatureForGloas() {
    // no header signature validation in Gloas
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @Test
  void shouldNotValidateStateForGloas() {
    // no state validation in Gloas
    // State-related helpers should never be called
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify that state validation helpers were never invoked
    verify(gossipValidationHelper, never()).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper, never()).isProposerTheExpectedProposer(any(), any(), any());
  }

  @Test
  void shouldTrackByBeaconBlockRootAndColumnIndex() {
    // First sidecar - should accept
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify the tracking key was added (beaconBlockRoot + columnIndex)
    assertThat(dataColumnSidecarGossipValidator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index));

    // Same beaconBlockRoot and columnIndex - should ignore (already in set from first validation)
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @Test
  void shouldAcceptDifferentColumnIndexForSameBlock() {
    // First sidecar
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Create another sidecar with different column index but same slot and block root
    final UInt64 differentIndex = UInt64.valueOf(2);
    final DataColumnSidecar sidecarDifferentIndex =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(differentIndex)
            .beaconBlockRoot(beaconBlockRoot)
            .build();

    // Different column index, should accept
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(sidecarDifferentIndex))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify both tracking keys are present
    assertThat(dataColumnSidecarGossipValidator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index))
        .contains(new GloasTrackingKey(beaconBlockRoot, differentIndex));
  }

  @Test
  void shouldAcceptDifferentBlockForSameColumnIndex() {
    // First sidecar
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Different block (different beacon block root) but same slot and column index
    final Bytes32 differentBlockRoot = dataStructureUtil.randomBytes32();
    final DataColumnSidecar sidecarDifferentBlock =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(slot)
            .index(index)
            .beaconBlockRoot(differentBlockRoot)
            .build();

    // Should accept - different block root means different tracking key
    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(sidecarDifferentBlock))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);

    // Verify both tracking keys are present
    assertThat(dataColumnSidecarGossipValidator.getReceivedValidDataColumnSidecarInfoSet())
        .contains(new GloasTrackingKey(beaconBlockRoot, index))
        .contains(new GloasTrackingKey(differentBlockRoot, index));
  }

  @Test
  void shouldRejectWhenBeaconBlockRootNotKnown() {
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(dataColumnSidecar))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @Test
  void shouldRejectWhenSlotDoesNotMatchBlock() {
    final UInt64 differentSlot = UInt64.valueOf(3);
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot))
        .thenReturn(Optional.of(differentSlot));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(
            reject(
                "DataColumnSidecar's slot %s does not match the block slot %s for beacon_block_root %s",
                slot, differentSlot, beaconBlockRoot));
  }

  @Test
  void shouldAcceptWhenSlotMatchesBlock() {
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void shouldBeSavedForFutureWhenBlockUnavailable() {
    when(gossipValidationHelper.getSlotForBlockRoot(beaconBlockRoot)).thenReturn(Optional.of(slot));
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(dataColumnSidecar))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @Test
  void shouldRejectWhenKzgProofsDoNotMatch() {
    // block with bid that has a different number of commitments than the sidecar's column size
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(dataColumnSidecar))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(createCommitments(dataColumnSidecar.getColumn().size() + 1))));

    SafeFutureAssert.assertThatSafeFuture(
            dataColumnSidecarGossipValidator.validate(dataColumnSidecar))
        .isCompletedWithValue(reject("Invalid DataColumnSidecar"));
  }
}
