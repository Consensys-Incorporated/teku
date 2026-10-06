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

package tech.pegasys.teku.spec.datastructures.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class AnchorPointTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void create_withCheckpointPriorToState() {
    final UInt64 epoch = UInt64.valueOf(10);
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(epoch);

    final BeaconBlockAndState blockAndState =
        dataStructureUtil.randomBlockAndState(epochStartSlot.plus(1));
    final Checkpoint checkpoint = new Checkpoint(epoch, blockAndState.getRoot());

    assertThatThrownBy(
            () -> AnchorPoint.create(spec, checkpoint, blockAndState.getState(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Block must be at or prior to the start of the checkpoint epoch");
  }

  @Test
  public void shouldDetectInvalidGenesisStateWithMismatchedBlockHeader() {
    final BeaconState beaconState = dataStructureUtil.genesisBeaconState(12);
    final BeaconState brokenGenesisState =
        beaconState.updated(
            s -> s.setLatestBlockHeader(dataStructureUtil.randomBeaconBlockHeader()));
    assertThatThrownBy(() -> AnchorPoint.fromGenesisState(spec, brokenGenesisState))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not match genesis block body root");
  }

  @Test
  public void fromGenesisState_shouldEmbedLatestExecutionPayloadBidInGloasGenesisBlock() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final BeaconState genesisState = gloasDataStructureUtil.genesisBeaconState(12);

    final AnchorPoint anchor = AnchorPoint.fromGenesisState(gloasSpec, genesisState);

    final BeaconBlock genesisBlock = anchor.getSignedBeaconBlock().orElseThrow().getMessage();
    final BeaconBlockBodyGloas genesisBlockBody =
        BeaconBlockBodyGloas.required(genesisBlock.getBody());
    assertThat(genesisBlockBody.getSignedExecutionPayloadBid().getMessage())
        .isEqualTo(BeaconStateGloas.required(genesisState).getLatestExecutionPayloadBid());
    assertThat(genesisBlockBody.getSignedExecutionPayloadBid().getSignature())
        .isEqualTo(BLSSignature.empty());
    assertThat(anchor.getRoot()).isEqualTo(expectedGenesisBlockRoot(genesisState));
  }

  @Test
  public void fromGenesisState_shouldAcceptGloasGenesisFromEthpandaopsGenesisGenerator()
      throws IOException {
    // genesis.ssz and config.yaml produced by ethpandaops/ethereum-genesis-generator:6.2.1 for a
    // minimal preset devnet with GLOAS_FORK_EPOCH: 0
    final Spec gloasSpec =
        SpecFactory.create(
            Resources.getResource(AnchorPointTest.class, "gloas-genesis-minimal-config.yaml")
                .toString());
    final BeaconState genesisState =
        gloasSpec.deserializeBeaconState(
            Bytes.wrap(
                Resources.toByteArray(
                    Resources.getResource(AnchorPointTest.class, "gloas-genesis-minimal.ssz"))));

    final AnchorPoint anchor = AnchorPoint.fromGenesisState(gloasSpec, genesisState);

    assertThat(anchor.getRoot()).isEqualTo(expectedGenesisBlockRoot(genesisState));
  }

  /**
   * The genesis block root is the root of the state's {@code latest_block_header} once {@code
   * process_slots} has filled in its {@code state_root}.
   */
  private static Bytes32 expectedGenesisBlockRoot(final BeaconState genesisState) {
    final BeaconBlockHeader header = genesisState.getLatestBlockHeader();
    return new BeaconBlockHeader(
            header.getSlot(),
            header.getProposerIndex(),
            header.getParentRoot(),
            genesisState.hashTreeRoot(),
            header.getBodyRoot())
        .hashTreeRoot();
  }
}
