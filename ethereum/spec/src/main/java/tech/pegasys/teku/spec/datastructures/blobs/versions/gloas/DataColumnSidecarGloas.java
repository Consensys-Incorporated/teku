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

package tech.pegasys.teku.spec.datastructures.blobs.versions.gloas;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class DataColumnSidecarGloas
    extends Container5<
        DataColumnSidecarGloas, SszUInt64, DataColumn, SszList<SszKZGProof>, SszUInt64, SszBytes32>
    implements DataColumnSidecar {

  DataColumnSidecarGloas(final DataColumnSidecarSchemaGloas schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  DataColumnSidecarGloas(
      final DataColumnSidecarSchemaGloas schema,
      final UInt64 index,
      final DataColumn column,
      final SszList<SszKZGProof> kzgProofs,
      final UInt64 slot,
      final Bytes32 beaconBlockRoot) {
    super(
        schema,
        SszUInt64.of(index),
        column,
        kzgProofs,
        SszUInt64.of(slot),
        SszBytes32.of(beaconBlockRoot));
  }

  @Override
  public DataColumnSidecarSchemaGloas getSchema() {
    return (DataColumnSidecarSchemaGloas) super.getSchema();
  }

  @Override
  public UInt64 getIndex() {
    return getField0().get();
  }

  @Override
  public DataColumn getColumn() {
    return getField1();
  }

  @Override
  public Optional<SszList<SszKZGCommitment>> getMaybeKzgCommitments() {
    return Optional.empty();
  }

  @Override
  public SszList<SszKZGProof> getKzgProofs() {
    return getField2();
  }

  @Override
  public UInt64 getSlot() {
    return getField3().get();
  }

  @Override
  public Bytes32 getBeaconBlockRoot() {
    return getField4().get();
  }

  @Override
  public Optional<SignedBeaconBlockHeader> getMaybeSignedBlockHeader() {
    return Optional.empty();
  }
}
