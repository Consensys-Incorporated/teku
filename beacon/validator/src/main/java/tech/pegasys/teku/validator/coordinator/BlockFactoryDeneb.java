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

package tech.pegasys.teku.validator.coordinator;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class BlockFactoryDeneb extends BlockFactoryPhase0 {

  public BlockFactoryDeneb(final Spec spec, final BlockOperationSelectorFactory operationSelector) {
    super(spec, operationSelector);
  }

  @Override
  public SafeFuture<BlockContainerAndMetaData> createUnsignedBlock(
      final BlockProductionContext blockProductionContext) {
    return createNewUnsignedBlock(blockProductionContext)
        .thenCompose(
            blockAndState -> {
              final BeaconBlock block = blockAndState.getBlock();
              final BeaconState state = blockAndState.getState();
              if (block.isBlinded()) {
                return SafeFuture.completedFuture(
                    createBlockContainerAndMetaDataBuilder(state).blockContainer(block).build());
              }
              return createBlockContents(block)
                  .thenApply(
                      blockContents ->
                          createBlockContainerAndMetaDataBuilder(state)
                              .blockContainer(blockContents)
                              .build());
            });
  }

  @Override
  public List<BlobSidecar> createBlobSidecars(final SignedBlockContainer blockContainer) {
    return operationSelector.createBlobSidecarsSelector().apply(blockContainer);
  }

  private SafeFuture<BlockContainer> createBlockContents(final BeaconBlock block) {
    // The execution BlobsBundle has been cached as part of the block creation
    return operationSelector
        .createBlobsBundleSelector()
        .apply(block)
        .thenApply(blobsBundle -> createBlockContents(block, blobsBundle));
  }

  private BlockContainer createBlockContents(
      final BeaconBlock block, final BlobsBundle blobsBundle) {
    return SchemaDefinitionsDeneb.required(spec.atSlot(block.getSlot()).getSchemaDefinitions())
        .getBlockContentsSchema()
        .create(block, blobsBundle.getProofs(), blobsBundle.getBlobs(), Optional.empty());
  }
}
