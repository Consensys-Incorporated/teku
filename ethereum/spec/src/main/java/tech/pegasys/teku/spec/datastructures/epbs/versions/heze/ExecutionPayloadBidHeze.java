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

package tech.pegasys.teku.spec.datastructures.epbs.versions.heze;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container13;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class ExecutionPayloadBidHeze
    extends Container13<
        ExecutionPayloadBidHeze,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszList<SszKZGCommitment>,
        SszBytes32,
        SszBitvector>
    implements ExecutionPayloadBid {

  ExecutionPayloadBidHeze(
      final ExecutionPayloadBidSchemaHeze schema,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final Bytes32 blockHash,
      final Bytes32 prevRandao,
      final Bytes20 feeRecipient,
      final UInt64 gasLimit,
      final UInt64 builderIndex,
      final UInt64 slot,
      final UInt64 value,
      final UInt64 executionPayment,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final Bytes32 executionRequestsRoot,
      final SszBitvector inclusionListBits) {
    super(
        schema,
        SszBytes32.of(parentBlockHash),
        SszBytes32.of(parentBlockRoot),
        SszBytes32.of(blockHash),
        SszBytes32.of(prevRandao),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszUInt64.of(gasLimit),
        SszUInt64.of(builderIndex),
        SszUInt64.of(slot),
        SszUInt64.of(value),
        SszUInt64.of(executionPayment),
        blobKzgCommitments,
        SszBytes32.of(executionRequestsRoot),
        inclusionListBits);
  }

  ExecutionPayloadBidHeze(final ExecutionPayloadBidSchemaHeze schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  @Override
  public Bytes32 getParentBlockHash() {
    return getField0().get();
  }

  @Override
  public Bytes32 getParentBlockRoot() {
    return getField1().get();
  }

  @Override
  public Bytes32 getBlockHash() {
    return getField2().get();
  }

  @Override
  public Bytes32 getPrevRandao() {
    return getField3().get();
  }

  @Override
  public Eth1Address getFeeRecipient() {
    return Eth1Address.fromBytes(getField4().getBytes());
  }

  @Override
  public UInt64 getGasLimit() {
    return getField5().get();
  }

  @Override
  public UInt64 getBuilderIndex() {
    return getField6().get();
  }

  @Override
  public UInt64 getSlot() {
    return getField7().get();
  }

  @Override
  public UInt64 getValue() {
    return getField8().get();
  }

  @Override
  public UInt64 getExecutionPayment() {
    return getField9().get();
  }

  @Override
  public SszList<SszKZGCommitment> getBlobKzgCommitments() {
    return getField10();
  }

  @Override
  public Bytes32 getExecutionRequestsRoot() {
    return getField11().get();
  }

  public SszBitvector getInclusionListBits() {
    return getField12();
  }

  @Override
  public Optional<SszBitvector> getOptionalInclusionListBits() {
    return Optional.of(getInclusionListBits());
  }

  @Override
  public ExecutionPayloadBidSchemaHeze getSchema() {
    return (ExecutionPayloadBidSchemaHeze) super.getSchema();
  }
}
