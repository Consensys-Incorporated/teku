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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarBuilder;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarBuilderFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class DataColumnSidecarBuilderGloas extends DataColumnSidecarBuilderFulu {

  private DataColumnSidecarSchemaGloas schema;

  protected UInt64 slot;
  protected Bytes32 beaconBlockRoot;

  public DataColumnSidecarBuilderGloas schema(final DataColumnSidecarSchemaGloas schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder kzgCommitments(final SszList<SszKZGCommitment> kzgCommitments) {
    // NO-OP for Gloas
    return this;
  }

  @Override
  public DataColumnSidecarBuilder signedBlockHeader(
      final SignedBeaconBlockHeader signedBlockHeader) {
    // NO-OP for Gloas
    return this;
  }

  @Override
  public DataColumnSidecarBuilder kzgCommitmentsInclusionProof(
      final List<Bytes32> kzgCommitmentsInclusionProof) {
    // NO-OP for Gloas
    return this;
  }

  @Override
  public DataColumnSidecarBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  @Override
  public DataColumnSidecarBuilder beaconBlockRoot(final Bytes32 beaconBlockRoot) {
    this.beaconBlockRoot = beaconBlockRoot;
    return this;
  }

  @Override
  public DataColumnSidecar build() {
    validate();
    return new DataColumnSidecarGloas(schema, index, column, kzgProofs, slot, beaconBlockRoot);
  }

  @Override
  protected void validate() {
    checkNotNull(schema, "schema must be specified");
    checkNotNull(index, "index must be specified");
    checkNotNull(column, "column must be specified");
    checkNotNull(kzgProofs, "kzgProofs must be specified");
    checkNotNull(slot, "slot must be specified");
    checkNotNull(beaconBlockRoot, "beaconBlockRoot must be specified");
  }
}
