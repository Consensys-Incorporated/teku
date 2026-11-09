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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class TestStoreFactory {
  private final Spec spec;
  private final DataStructureUtil dataStructureUtil;

  public TestStoreFactory() {
    this(TestSpecFactory.createDefault());
  }

  public TestStoreFactory(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(spec);
  }

  public TestStoreImpl createGenesisStore() {
    return getForkChoiceStore(createAnchorForGenesis());
  }

  public TestStoreImpl createAnchorStore(final AnchorPoint anchor) {
    return getForkChoiceStore(anchor);
  }

  private AnchorPoint createAnchorForGenesis() {
    return createAnchorFromState(createRandomGenesisState());
  }

  private AnchorPoint createAnchorFromState(final BeaconState anchorState) {
    return dataStructureUtil.createAnchorFromState(anchorState);
  }

  private TestStoreImpl getForkChoiceStore(final AnchorPoint anchor) {
    final BeaconState anchorState = anchor.getState();
    final Bytes32 anchorRoot = anchor.getRoot();
    final Checkpoint anchorCheckpoint = anchor.getCheckpoint();

    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BlockCheckpoints> blockCheckpoints = new HashMap<>();
    Map<Bytes32, BeaconState> blockStates = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpointStates = new HashMap<>();
    Map<UInt64, VoteTracker> votes = new HashMap<>();
    Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars = new HashMap<>();
    Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloads = new HashMap<>();

    blocks.put(anchorRoot, anchor.getSignedBeaconBlock().orElseThrow());
    blockStates.put(anchorRoot, anchorState);
    blockCheckpoints.put(
        anchorRoot,
        new BlockCheckpoints(
            anchorCheckpoint, anchorCheckpoint, anchorCheckpoint, anchorCheckpoint));
    checkpointStates.put(anchorCheckpoint, anchorState);

    return new TestStoreImpl(
        spec,
        spec.computeTimeAtSlot(anchorState.getSlot(), anchorState.getGenesisTime()),
        anchorState.getGenesisTime(),
        Optional.of(anchorCheckpoint),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        blockStates,
        blockCheckpoints,
        checkpointStates,
        votes,
        blobSidecars,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        executionPayloads);
  }

  private BeaconState createRandomGenesisState() {
    return dataStructureUtil.randomBeaconState(SpecConfig.GENESIS_SLOT);
  }
}
