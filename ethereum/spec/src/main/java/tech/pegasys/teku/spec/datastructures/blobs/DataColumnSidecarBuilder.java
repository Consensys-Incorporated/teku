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

package tech.pegasys.teku.spec.datastructures.blobs;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public interface DataColumnSidecarBuilder {

  DataColumnSidecarBuilder index(UInt64 index);

  DataColumnSidecarBuilder column(DataColumn column);

  DataColumnSidecarBuilder kzgCommitments(SszList<SszKZGCommitment> kzgCommitments);

  DataColumnSidecarBuilder kzgProofs(SszList<SszKZGProof> kzgProofs);

  DataColumnSidecarBuilder signedBlockHeader(SignedBeaconBlockHeader signedBlockHeader);

  DataColumnSidecarBuilder kzgCommitmentsInclusionProof(List<Bytes32> kzgCommitmentsInclusionProof);

  DataColumnSidecarBuilder slot(UInt64 slot);

  DataColumnSidecarBuilder beaconBlockRoot(Bytes32 beaconBlockRoot);

  DataColumnSidecar build();
}
