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
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.spec.constants.PqConstants.LEAF_OFFSET_BEACON_PROPOSER;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.generator.PqTestKeys;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/** Gossip validation of PQ blocks, whose proposers sign with XMSS. */
class BlockGossipValidatorPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  /**
   * Test blocks carry stub XMSS signatures holding only their leaf index, so this verifier accepts
   * a signature when it was made at the expected leaf under the proposer's registered key.
   */
  private static final XmssSignatureVerifier LEAF_CHECKING_VERIFIER =
      (publicKey, leafIndex, message, signature) ->
          XmssSignatureVerifier.getLeafIndex(signature).equals(leafIndex);

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private StorageSystem storageSystem;
  private RecentChainData recentChainData;
  private BlockGossipValidator blockGossipValidator;

  @BeforeEach
  void setUp() {
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(
                spec, recentChainData, storageSystem.getMetricsSystem(), LEAF_CHECKING_VERIFIER),
            mock(ReceivedBlockEventsChannel.class));
    // Run the chain into the PQ fork
    storageSystem
        .chainUpdater()
        .updateBestBlock(
            storageSystem.chainUpdater().advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH)));
  }

  @Test
  void acceptsPqBlockSignedAtTheProposerLeaf() {
    final SignedBeaconBlock block = nextBlock();
    assertThat(spec.atSlot(block.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    assertThat(block.isXmssSigned()).isTrue();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
  }

  @Test
  void rejectsPqBlockSignedAtAnotherLeaf() {
    final SignedBeaconBlock block = nextBlock();
    final UInt64 otherLeaf =
        MiscHelpersPq.required(spec.atSlot(block.getSlot()).miscHelpers())
            .computeXmssLeafIndex(block.getSlot(), LEAF_OFFSET_BEACON_PROPOSER)
            .plus(1);
    final SignedBeaconBlock wrongLeaf =
        spec.atSlot(block.getSlot())
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .create(block.getMessage(), PqTestKeys.stubSignature(otherLeaf));

    assertThat(blockGossipValidator.validate(wrongLeaf, true))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  @Test
  void rejectsFutureSlotPqBlockSignedAtAnotherLeaf() {
    // A block for a later slot is checked before being saved for future processing
    final UInt64 futureSlot = recentChainData.getHeadSlot().plus(2);
    final SignedBeaconBlock block =
        storageSystem.chainBuilder().generateBlockAtSlot(futureSlot).getBlock();
    final SignedBeaconBlock wrongLeaf =
        spec.atSlot(futureSlot)
            .getSchemaDefinitions()
            .getSignedBeaconBlockSchema()
            .create(block.getMessage(), PqTestKeys.stubSignature(UInt64.ZERO));
    storageSystem.chainUpdater().setCurrentSlot(recentChainData.getHeadSlot().plus(1));

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(
            result -> result.code() == ValidationResultCode.SAVE_FOR_FUTURE);
    assertThat(blockGossipValidator.validate(wrongLeaf, true))
        .isCompletedWithValueMatching(InternalValidationResult::isReject);
  }

  private SignedBeaconBlock nextBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(1);
    final SignedBeaconBlock block =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);
    return block;
  }
}
