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

package tech.pegasys.teku.spec.datastructures.blocks.versions.deneb;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class BlockContentsDeneb
    extends Container3<BlockContentsDeneb, BeaconBlock, SszList<SszKZGProof>, SszList<Blob>>
    implements BlockContainer {

  BlockContentsDeneb(final BlockContentsSchemaDeneb type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlockContentsDeneb(
      final BlockContentsSchemaDeneb schema,
      final BeaconBlock beaconBlock,
      final List<KZGProof> kzgProofs,
      final List<Blob> blobs) {
    super(
        schema,
        beaconBlock,
        schema
            .getKzgProofsSchema()
            .createFromElements(kzgProofs.stream().map(SszKZGProof::new).toList()),
        schema.getBlobsSchema().createFromElements(blobs));
  }

  @Override
  public BeaconBlock getBlock() {
    return getField0();
  }

  @Override
  public Optional<SszList<SszKZGProof>> getKzgProofs() {
    return Optional.of(getField1());
  }

  @Override
  public Optional<SszList<Blob>> getBlobs() {
    return Optional.of(getField2());
  }
}
