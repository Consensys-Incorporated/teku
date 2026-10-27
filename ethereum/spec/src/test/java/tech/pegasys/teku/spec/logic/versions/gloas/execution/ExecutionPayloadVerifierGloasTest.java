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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerificationException;

class ExecutionPayloadVerifierGloasTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ordinaryVerificationShouldRequirePostBlockState(final boolean advanceState)
      throws Exception {
    chainBuilder.generateGenesis();
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final BeaconState postState =
        chainBuilder.generateBlockAtSlot(checkpointSlot.minus(1)).getState();
    final SignedExecutionPayloadEnvelope envelope =
        chainBuilder.getExecutionPayloadAtSlot(postState.getSlot()).orElseThrow();
    final BeaconState state =
        advanceState ? spec.processSlots(postState, checkpointSlot) : postState;

    assertDoesNotThrow(
        () ->
            spec.getExecutionPayloadVerifier(envelope.getSlot())
                .verifyCheckpointAnchorExecutionPayloadEnvelope(
                    envelope,
                    AnchorPoint.fromInitialState(spec, state),
                    BLSSignatureVerifier.NOOP,
                    Optional.empty()));

    if (advanceState) {
      assertThatThrownBy(
              () ->
                  spec.getExecutionPayloadVerifier(envelope.getSlot())
                      .verifyExecutionPayloadEnvelope(
                          envelope, state, BLSSignatureVerifier.NOOP, Optional.empty()))
          .isInstanceOf(ExecutionPayloadVerificationException.class)
          .hasMessageContaining("slot");
      return;
    }
    assertDoesNotThrow(
        () ->
            spec.getExecutionPayloadVerifier(envelope.getSlot())
                .verifyExecutionPayloadEnvelope(
                    envelope, state, BLSSignatureVerifier.NOOP, Optional.empty()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldUseBlockEpochOnlyForAnchorVerification(final boolean checkpoint) throws Exception {
    chainBuilder.generateGenesis();
    final UInt64 checkpointSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final BeaconState postState =
        chainBuilder.generateBlockAtSlot(checkpointSlot.minus(1)).getState();
    final SignedExecutionPayloadEnvelope envelope =
        chainBuilder.getExecutionPayloadAtSlot(postState.getSlot()).orElseThrow();
    final BeaconState state =
        spec.processSlots(postState, checkpointSlot)
            .updated(
                mutable ->
                    mutable.setFork(
                        new Fork(
                            Bytes4.fromHexString("0x01000000"),
                            Bytes4.fromHexString("0x02000000"),
                            UInt64.ONE)));
    final BLSSignatureVerifier signatureVerifier = mock(BLSSignatureVerifier.class);
    when(signatureVerifier.verify(any(BLSPublicKey.class), any(), any())).thenReturn(true);
    final Bytes32 domain =
        spec.atSlot(envelope.getSlot())
            .beaconStateAccessors()
            .getDomain(
                state.getForkInfo(), Domain.BEACON_BUILDER, checkpoint ? UInt64.ZERO : UInt64.ONE);
    final Bytes signingRoot =
        spec.atSlot(envelope.getSlot())
            .miscHelpers()
            .computeSigningRoot(envelope.getMessage(), domain);

    if (checkpoint) {
      spec.getExecutionPayloadVerifier(envelope.getSlot())
          .verifyCheckpointAnchorExecutionPayloadEnvelope(
              envelope,
              AnchorPoint.fromInitialState(spec, state),
              signatureVerifier,
              Optional.empty());
    } else {
      assertThat(
              spec.getExecutionPayloadVerifier(envelope.getSlot())
                  .verifyExecutionPayloadEnvelopeSignature(state, envelope, signatureVerifier))
          .isTrue();
    }
    verify(signatureVerifier)
        .verify(any(BLSPublicKey.class), eq(signingRoot), eq(envelope.getSignature()));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void anchorVerificationShouldRejectWrongRootOrBlockBackedAnchor(final boolean blockBacked) {
    chainBuilder.generateGenesis();
    final SignedBlockAndState block = chainBuilder.generateBlockAtSlot(8);
    final SignedExecutionPayloadEnvelope envelope =
        chainBuilder.getExecutionPayloadAtSlot(block.getSlot()).orElseThrow();
    final AnchorPoint stateOnlyAnchor = AnchorPoint.fromInitialState(spec, block.getState());
    final AnchorPoint invalidAnchor =
        blockBacked
            ? AnchorPoint.create(spec, stateOnlyAnchor.getCheckpoint(), block)
            : AnchorPoint.fromInitialState(spec, chainBuilder.generateBlockAtSlot(16).getState());

    assertThatThrownBy(
            () ->
                spec.getExecutionPayloadVerifier(envelope.getSlot())
                    .verifyCheckpointAnchorExecutionPayloadEnvelope(
                        envelope, invalidAnchor, BLSSignatureVerifier.NOOP, Optional.empty()))
        .isInstanceOf(ExecutionPayloadVerificationException.class)
        .hasMessageContaining("state-only Gloas checkpoint anchor");
  }
}
