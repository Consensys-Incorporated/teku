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
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_PROPOSER_PREFERENCES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_BUILDER_BID_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.PqTestKeys;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class GossipValidationHelperPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;
  private static final Bytes SIGNING_ROOT = Bytes.repeat((byte) 7, 32);

  private int expectedSigner;

  /**
   * Accepts a stub signature made at the expected leaf under the expected validator's registered
   * key: the test validators' keys are {@link PqTestKeys#xmssPublicKey(int)}.
   */
  private final XmssSignatureVerifier verifier =
      (publicKey, leafIndex, message, signature) ->
          XmssSignatureVerifier.getLeafIndex(signature).equals(leafIndex)
              && publicKey.equals(PqTestKeys.xmssPublicKey(expectedSigner));

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private StorageSystem storageSystem;
  private GossipValidationHelper helper;
  private BeaconState preForkState;
  private BeaconState pqState;

  @BeforeEach
  void setUp() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    preForkState = storageSystem.chainUpdater().initializeGenesis().getState();
    pqState =
        storageSystem
            .chainUpdater()
            .advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH))
            .getState();
    helper =
        new GossipValidationHelper(
            spec, storageSystem.recentChainData(), storageSystem.getMetricsSystem(), verifier);
  }

  @Test
  void verifiesUnderTheValidatorsXmssKeyAtTheLeaf() {
    expectedSigner = 3;
    final UInt64 leaf =
        helper.computeXmssLeafIndex(pqState.getSlot(), LEAF_OFFSET_PROPOSER_PREFERENCES);

    assertThat(isValid(UInt64.valueOf(3), leaf, PqTestKeys.stubSignature(leaf).getBytes()))
        .isTrue();
    // Another validator's key
    assertThat(isValid(UInt64.valueOf(4), leaf, PqTestKeys.stubSignature(leaf).getBytes()))
        .isFalse();
    // Another leaf
    assertThat(isValid(UInt64.valueOf(3), leaf, PqTestKeys.stubSignature(leaf.plus(1)).getBytes()))
        .isFalse();
  }

  @Test
  void rejectsUnknownValidatorsAndStatesWithoutXmssKeys() {
    expectedSigner = 3;
    final UInt64 leaf = UInt64.ONE;
    final Bytes signature = PqTestKeys.stubSignature(leaf).getBytes();

    assertThat(
            helper.isXmssSignatureValidWithRespectToValidatorIndex(
                SIGNING_ROOT,
                UInt64.valueOf(pqState.getValidators().size()),
                leaf,
                signature,
                pqState))
        .isFalse();
    // Before the fork, validators have BLS keys only
    assertThat(helper.getValidatorXmssPublicKey(preForkState, UInt64.valueOf(3))).isEmpty();
    assertThat(
            helper.isXmssSignatureValidWithRespectToValidatorIndex(
                SIGNING_ROOT, UInt64.valueOf(3), leaf, signature, preForkState))
        .isFalse();
  }

  @Test
  void selfBuiltMessagesAreSignedByTheLatestBlocksProposer() {
    final UInt64 proposer = pqState.getLatestBlockHeader().getProposerIndex();
    expectedSigner = proposer.intValue();
    final UInt64 leaf = UInt64.valueOf(42);

    assertThat(
            helper.isXmssSignatureValidWithRespectToBuilderIndex(
                SIGNING_ROOT,
                BUILDER_INDEX_SELF_BUILD,
                leaf,
                PqTestKeys.stubSignature(leaf).getBytes(),
                pqState))
        .isTrue();
  }

  @Test
  void isBuilderBidLeafIndex_onlyAcceptsTheBidRange() {
    final UInt64 firstBidLeaf = XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES);
    final UInt64 slot = pqState.getSlot();

    assertThat(helper.isBuilderBidLeafIndex(slot, firstBidLeaf)).isTrue();
    assertThat(
            helper.isBuilderBidLeafIndex(slot, firstBidLeaf.plus(XMSS_BUILDER_BID_LEAVES).minus(1)))
        .isTrue();
    assertThat(helper.isBuilderBidLeafIndex(slot, firstBidLeaf.minus(1))).isFalse();
    assertThat(helper.isBuilderBidLeafIndex(slot, firstBidLeaf.plus(XMSS_BUILDER_BID_LEAVES)))
        .isFalse();
  }

  private boolean isValid(final UInt64 validatorIndex, final UInt64 leaf, final Bytes signature) {
    return helper.isXmssSignatureValidWithRespectToValidatorIndex(
        SIGNING_ROOT, validatorIndex, leaf, signature, pqState);
  }
}
