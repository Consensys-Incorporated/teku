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

package tech.pegasys.teku.spec.generator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.pq.BeaconBlockBodyPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.xmss.XmssConstants;

/** Building chains that run through the PQ fork, the basis for testing PQ's pipelines. */
class PqChainBuilderTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @BeforeEach
  void setUp() {
    // As in the spec's own tests: blocks carry stub XMSS signatures
    XmssConstants.disableXmssVerification();
  }

  @AfterEach
  void tearDown() {
    XmssConstants.enableXmssVerification();
  }

  @Test
  void generatesPqBlocksAfterTheFork() {
    chainBuilder.generateGenesis();
    final UInt64 lastSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH.plus(2));
    final List<SignedBlockAndState> blocks = chainBuilder.generateBlocksUpToSlot(lastSlot);

    final UInt64 pqForkSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH);
    for (final SignedBlockAndState blockAndState : blocks) {
      final boolean isPq = blockAndState.getSlot().isGreaterThanOrEqualTo(pqForkSlot);
      assertThat(blockAndState.getBlock().getMessage().getBody() instanceof BeaconBlockBodyPq)
          .describedAs("block at slot %s", blockAndState.getSlot())
          .isEqualTo(isPq);
      assertThat(blockAndState.getBlock().isXmssSigned()).isEqualTo(isPq);
    }

    // Every validator registered its XMSS key and VRF root, so they all carry on through the fork
    final BeaconState state = chainBuilder.getLatestBlockAndState().getState();
    assertThat(spec.atSlot(state.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    for (int index = 0; index < state.getValidators().size(); index++) {
      final ValidatorPq validator = (ValidatorPq) state.getValidators().get(index);
      assertThat(validator.getXmssPublicKey()).isEqualTo(PqTestKeys.xmssPublicKey(index));
      assertThat(validator.getVrfRoot()).isEqualTo(PqTestKeys.vrfRoot(index));
    }
  }

  @Test
  void pqBlocksBuildOnTheirParentsPayload() {
    chainBuilder.generateGenesis();
    final UInt64 pqForkSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH);
    final List<SignedBlockAndState> blocks =
        chainBuilder.generateBlocksUpToSlot(pqForkSlot.plus(4));

    // Each block builds on its parent's payload, including the first PQ block on an EIP-8483
    // parent: the parent's bid must be read from a PQ body as well as a Gloas one
    for (int i = 1; i < blocks.size(); i++) {
      final SignedBlockAndState parent = blocks.get(i - 1);
      final SignedBlockAndState child = blocks.get(i);
      if (child.getSlot().isGreaterThanOrEqualTo(pqForkSlot)) {
        assertThat(MiscHelpersGloas.isParentBlockFull(parent.getBlock(), child.getBlock()))
            .describedAs("block at slot %s", child.getSlot())
            .isTrue();
      }
    }
  }

  @Test
  void pqBlocksPassFullValidation() throws Exception {
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    final UInt64 lastSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH.plus(1));
    final List<SignedBlockAndState> blocks = chainBuilder.generateBlocksUpToSlot(lastSlot);

    // Re-import every block with signature verification, as block import does
    BeaconState state = genesis.getState();
    for (final SignedBlockAndState blockAndState : blocks) {
      state =
          spec.processBlock(
              state, blockAndState.getBlock(), BLSSignatureVerifier.SIMPLE, Optional.empty());
      assertThat(state.hashTreeRoot()).isEqualTo(blockAndState.getState().hashTreeRoot());
    }
  }
}
