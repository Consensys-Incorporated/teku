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

package tech.pegasys.teku.spec.datastructures.execution.versions.bellatrix;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeaderBuilder;

public class ExecutionPayloadHeaderBuilderBellatrix implements ExecutionPayloadHeaderBuilder {
  private ExecutionPayloadHeaderSchemaBellatrix schema;
  protected Bytes32 parentHash;
  protected Bytes20 feeRecipient;
  protected Bytes32 stateRoot;
  protected Bytes32 receiptsRoot;
  protected Bytes logsBloom;
  protected Bytes32 prevRandao;
  protected UInt64 blockNumber;
  protected UInt64 gasLimit;
  protected UInt64 gasUsed;
  protected UInt64 timestamp;
  protected Bytes extraData;
  protected UInt256 baseFeePerGas;
  protected Bytes32 blockHash;
  protected Bytes32 transactionsRoot;

  public ExecutionPayloadHeaderBuilderBellatrix schema(
      final ExecutionPayloadHeaderSchemaBellatrix schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder parentHash(final Bytes32 parentHash) {
    this.parentHash = parentHash;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder feeRecipient(final Bytes20 feeRecipient) {
    this.feeRecipient = feeRecipient;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder stateRoot(final Bytes32 stateRoot) {
    this.stateRoot = stateRoot;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder receiptsRoot(final Bytes32 receiptsRoot) {
    this.receiptsRoot = receiptsRoot;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder logsBloom(final Bytes logsBloom) {
    this.logsBloom = logsBloom;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder prevRandao(final Bytes32 prevRandao) {
    this.prevRandao = prevRandao;

    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder blockNumber(final UInt64 blockNumber) {
    this.blockNumber = blockNumber;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder gasLimit(final UInt64 gasLimit) {
    this.gasLimit = gasLimit;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder gasUsed(final UInt64 gasUsed) {
    this.gasUsed = gasUsed;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder timestamp(final UInt64 timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder extraData(final Bytes extraData) {
    this.extraData = extraData;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder baseFeePerGas(final UInt256 baseFeePerGas) {
    this.baseFeePerGas = baseFeePerGas;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder blockHash(final Bytes32 blockHash) {
    this.blockHash = blockHash;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder transactionsRoot(final Bytes32 transactionsRoot) {
    this.transactionsRoot = transactionsRoot;
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder withdrawalsRoot(
      final Supplier<Bytes32> withdrawalsRootSupplier) {
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder blobGasUsed(final Supplier<UInt64> blobGasUsedSupplier) {
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder excessBlobGas(final Supplier<UInt64> excessBlobGasSupplier) {
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder blockAccessListRoot(
      final Supplier<Bytes32> blockAccessListRootSupplier) {
    return this;
  }

  @Override
  public ExecutionPayloadHeaderBuilder slotNumber(final Supplier<UInt64> slotNumberSupplier) {
    return this;
  }

  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }

  protected void validate() {
    checkNotNull(parentHash, "parentHash must be specified");
    checkNotNull(feeRecipient, "feeRecipient must be specified");
    checkNotNull(stateRoot, "stateRoot must be specified");
    checkNotNull(receiptsRoot, "receiptsRoot must be specified");
    checkNotNull(logsBloom, "logsBloom must be specified");
    checkNotNull(prevRandao, "prevRandao must be specified");
    checkNotNull(blockNumber, "blockNumber must be specified");
    checkNotNull(gasLimit, "gasLimit must be specified");
    checkNotNull(gasUsed, "gasUsed must be specified");
    checkNotNull(timestamp, "timestamp must be specified");
    checkNotNull(extraData, "extraData must be specified");
    checkNotNull(baseFeePerGas, "baseFeePerGas must be specified");
    checkNotNull(blockHash, "blockHash must be specified");
    checkNotNull(transactionsRoot, "transactionsRoot must be specified");
    validateSchema();
  }

  @Override
  public ExecutionPayloadHeader build() {
    validate();
    return new ExecutionPayloadHeaderBellatrix(
        schema,
        SszBytes32.of(parentHash),
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszBytes32.of(stateRoot),
        SszBytes32.of(receiptsRoot),
        SszByteVector.fromBytes(logsBloom),
        SszBytes32.of(prevRandao),
        SszUInt64.of(blockNumber),
        SszUInt64.of(gasLimit),
        SszUInt64.of(gasUsed),
        SszUInt64.of(timestamp),
        schema.getExtraDataSchema().fromBytes(extraData),
        SszUInt256.of(baseFeePerGas),
        SszBytes32.of(blockHash),
        SszBytes32.of(transactionsRoot));
  }
}
