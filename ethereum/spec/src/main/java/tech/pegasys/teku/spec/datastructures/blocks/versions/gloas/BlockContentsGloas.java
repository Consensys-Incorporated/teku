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

package tech.pegasys.teku.spec.datastructures.blocks.versions.gloas;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

/**
 * Compound block container for Gloas self-built blocks, carrying the beacon block, the execution
 * payload envelope (unsigned), KZG proofs, and blobs together.
 *
 * <p>Only returned by the V4 block-production endpoint when the proposer is self-building (local EL
 * payload) and {@code include_payload=true} is requested. External-builder bids produce a plain
 * {@link BeaconBlock} because the builder withholds the payload until after attestation.
 */
public class BlockContentsGloas
    extends Container4<
        BlockContentsGloas,
        BeaconBlock,
        ExecutionPayloadEnvelope,
        SszList<SszKZGProof>,
        SszList<Blob>>
    implements BlockContainer {

  BlockContentsGloas(final BlockContentsSchemaGloas type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  BlockContentsGloas(
      final BlockContentsSchemaGloas schema,
      final BeaconBlock beaconBlock,
      final ExecutionPayloadEnvelope executionPayloadEnvelope,
      final SszList<SszKZGProof> kzgProofs,
      final SszList<Blob> blobs) {
    super(schema, beaconBlock, executionPayloadEnvelope, kzgProofs, blobs);
  }

  @Override
  public BeaconBlock getBlock() {
    return getField0();
  }

  @Override
  public Optional<ExecutionPayloadEnvelope> getExecutionPayloadEnvelope() {
    return Optional.of(getField1());
  }

  @Override
  public Optional<SszList<SszKZGProof>> getKzgProofs() {
    return Optional.of(getField2());
  }

  @Override
  public Optional<SszList<Blob>> getBlobs() {
    return Optional.of(getField3());
  }
}
