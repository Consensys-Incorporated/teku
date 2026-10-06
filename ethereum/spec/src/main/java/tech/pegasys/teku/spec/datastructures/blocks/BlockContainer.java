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

package tech.pegasys.teku.spec.datastructures.blocks;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

/**
 * Interface used to represent both {@link BeaconBlock}, all BlockContents[Milestone] and their
 * blinded variants: <a
 * href="https://github.com/ethereum/beacon-APIs/tree/master/types/deneb">beacon-APIs/types/deneb</a>
 */
public interface BlockContainer extends SszData, SszContainer {

  BeaconBlock getBlock();

  default UInt64 getSlot() {
    return getBlock().getSlot();
  }

  default Bytes32 getRoot() {
    return getBlock().getRoot();
  }

  default Optional<ExecutionPayloadEnvelope> getExecutionPayloadEnvelope() {
    return Optional.empty();
  }

  default Optional<SszList<SszKZGProof>> getKzgProofs() {
    return Optional.empty();
  }

  default Optional<SszList<Blob>> getBlobs() {
    return Optional.empty();
  }

  default boolean isBlinded() {
    return getBlock().isBlinded();
  }
}
