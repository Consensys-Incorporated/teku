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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface ExecutionPayloadBid extends SszContainer {

  @Override
  ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> getSchema();

  Bytes32 getParentBlockHash();

  Bytes32 getParentBlockRoot();

  Bytes32 getBlockHash();

  Bytes32 getPrevRandao();

  Eth1Address getFeeRecipient();

  UInt64 getGasLimit();

  UInt64 getBuilderIndex();

  UInt64 getSlot();

  UInt64 getValue();

  UInt64 getExecutionPayment();

  SszList<SszKZGCommitment> getBlobKzgCommitments();

  Bytes32 getExecutionRequestsRoot();

  default Optional<SszBitvector> getOptionalInclusionListBits() {
    return Optional.empty();
  }
}
