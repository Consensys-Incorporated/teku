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

package tech.pegasys.teku.spec.datastructures.blobs.versions.fulu;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class DataColumnSidecarFulu
    extends Container6<
        DataColumnSidecarFulu,
        SszUInt64,
        DataColumn,
        SszList<SszKZGCommitment>,
        SszList<SszKZGProof>,
        SignedBeaconBlockHeader,
        SszBytes32Vector>
    implements DataColumnSidecar {

  public static DataColumnSidecarFulu required(final DataColumnSidecar dataColumnSidecar) {
    try {
      return (DataColumnSidecarFulu) dataColumnSidecar;
    } catch (final ClassCastException __) {
      throw new IllegalArgumentException(
          "Expected DataColumnSidecarFulu but got: "
              + dataColumnSidecar.getClass().getSimpleName());
    }
  }

  DataColumnSidecarFulu(final DataColumnSidecarSchemaFulu schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  DataColumnSidecarFulu(
      final DataColumnSidecarSchemaFulu schema,
      final UInt64 index,
      final DataColumn column,
      final SszList<SszKZGCommitment> kzgCommitments,
      final SszList<SszKZGProof> kzgProofs,
      final SignedBeaconBlockHeader signedBlockHeader,
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    super(
        schema,
        SszUInt64.of(index),
        column,
        kzgCommitments,
        kzgProofs,
        signedBlockHeader,
        schema
            .getKzgCommitmentsInclusionProofSchema()
            .createFromElements(
                kzgCommitmentsInclusionProof.stream().map(SszBytes32::of).toList()));
  }

  @Override
  public DataColumnSidecarSchemaFulu getSchema() {
    return (DataColumnSidecarSchemaFulu) super.getSchema();
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
    return Optional.of(getField2());
  }

  public SszList<SszKZGCommitment> getKzgCommitments() {
    return getField2();
  }

  @Override
  public SszList<SszKZGProof> getKzgProofs() {
    return getField3();
  }

  public SignedBeaconBlockHeader getSignedBlockHeader() {
    return getField4();
  }

  @Override
  public Optional<SignedBeaconBlockHeader> getMaybeSignedBlockHeader() {
    return Optional.of(getSignedBlockHeader());
  }

  public SszBytes32Vector getKzgCommitmentsInclusionProof() {
    return getField5();
  }

  @Override
  public UInt64 getSlot() {
    return getSignedBlockHeader().getMessage().getSlot();
  }

  @Override
  public Bytes32 getBeaconBlockRoot() {
    return getSignedBlockHeader().getMessage().getRoot();
  }

  public Bytes32 getBlockBodyRoot() {
    return getSignedBlockHeader().getMessage().getBodyRoot();
  }
}
