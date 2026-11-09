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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public class DataColumnSidecarBuilderFulu implements DataColumnSidecarBuilder {

  private DataColumnSidecarSchemaFulu schema;

  protected UInt64 index;
  protected DataColumn column;
  protected SszList<SszKZGCommitment> kzgCommitments;
  protected SszList<SszKZGProof> kzgProofs;
  private SignedBeaconBlockHeader signedBlockHeader;
  private List<Bytes32> kzgCommitmentsInclusionProof;

  public DataColumnSidecarBuilderFulu schema(final DataColumnSidecarSchemaFulu schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder index(final UInt64 index) {
    this.index = index;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder column(final DataColumn column) {
    this.column = column;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder kzgCommitments(final SszList<SszKZGCommitment> kzgCommitments) {
    this.kzgCommitments = kzgCommitments;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder kzgProofs(final SszList<SszKZGProof> kzgProofs) {
    this.kzgProofs = kzgProofs;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder signedBlockHeader(
      final SignedBeaconBlockHeader signedBlockHeader) {
    this.signedBlockHeader = signedBlockHeader;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder kzgCommitmentsInclusionProof(
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    this.kzgCommitmentsInclusionProof = kzgCommitmentsInclusionProof;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder slot(final UInt64 slot) {
    // NO-OP for Fulu
    return this;
  }

  @Override
  public DataColumnSidecarBuilder beaconBlockRoot(final Bytes32 beaconBlockRoot) {
    // NO-OP for Fulu
    return this;
  }

  @Override
  public DataColumnSidecar build() {
    validate();
    return new DataColumnSidecarFulu(
        schema,
        index,
        column,
        kzgCommitments,
        kzgProofs,
        signedBlockHeader,
        kzgCommitmentsInclusionProof);
  }

  protected void validate() {
    checkNotNull(schema, "schema must be specified");
    checkNotNull(index, "index must be specified");
    checkNotNull(column, "column must be specified");
    checkNotNull(kzgCommitments, "kzgCommitments must be specified");
    checkNotNull(kzgProofs, "kzgProofs must be specified");
    checkNotNull(signedBlockHeader, "signedBlockHeader must be specified");
    checkNotNull(kzgCommitmentsInclusionProof, "kzgCommitmentsInclusionProof must be specified");
  }
}
