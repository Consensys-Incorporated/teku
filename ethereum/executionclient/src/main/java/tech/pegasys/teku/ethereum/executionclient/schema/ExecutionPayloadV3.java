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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;

public class ExecutionPayloadV3 extends ExecutionPayloadV2 {
  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 blobGasUsed;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 excessBlobGas;

  @JsonCreator
  public ExecutionPayloadV3(
      final @JsonProperty("parentHash") Bytes32 parentHash,
      final @JsonProperty("feeRecipient") Bytes20 feeRecipient,
      final @JsonProperty("stateRoot") Bytes32 stateRoot,
      final @JsonProperty("receiptsRoot") Bytes32 receiptsRoot,
      final @JsonProperty("logsBloom") Bytes logsBloom,
      final @JsonProperty("prevRandao") Bytes32 prevRandao,
      final @JsonProperty("blockNumber") UInt64 blockNumber,
      final @JsonProperty("gasLimit") UInt64 gasLimit,
      final @JsonProperty("gasUsed") UInt64 gasUsed,
      final @JsonProperty("timestamp") UInt64 timestamp,
      final @JsonProperty("extraData") Bytes extraData,
      final @JsonProperty("baseFeePerGas") UInt256 baseFeePerGas,
      final @JsonProperty("blockHash") Bytes32 blockHash,
      final @JsonProperty("transactions") List<Bytes> transactions,
      final @JsonProperty("withdrawals") List<WithdrawalV1> withdrawals,
      final @JsonProperty("blobGasUsed") UInt64 blobGasUsed,
      final @JsonProperty("excessBlobGas") UInt64 excessBlobGas) {
    super(
        parentHash,
        feeRecipient,
        stateRoot,
        receiptsRoot,
        logsBloom,
        prevRandao,
        blockNumber,
        gasLimit,
        gasUsed,
        timestamp,
        extraData,
        baseFeePerGas,
        blockHash,
        transactions,
        withdrawals);
    checkNotNull(blobGasUsed, "blobGasUsed");
    checkNotNull(excessBlobGas, "excessBlobGas");
    this.blobGasUsed = blobGasUsed;
    this.excessBlobGas = excessBlobGas;
  }

  @Override
  protected ExecutionPayloadBuilder applyToBuilder(
      final ExecutionPayloadSchema<?> executionPayloadSchema,
      final ExecutionPayloadBuilder builder) {
    return super.applyToBuilder(executionPayloadSchema, builder)
        .blobGasUsed(() -> blobGasUsed)
        .excessBlobGas(() -> excessBlobGas);
  }

  public static ExecutionPayloadV3 fromInternalExecutionPayload(
      final ExecutionPayload executionPayload) {
    return new ExecutionPayloadV3(
        executionPayload.getParentHash(),
        executionPayload.getFeeRecipient(),
        executionPayload.getStateRoot(),
        executionPayload.getReceiptsRoot(),
        executionPayload.getLogsBloom(),
        executionPayload.getPrevRandao(),
        executionPayload.getBlockNumber(),
        executionPayload.getGasLimit(),
        executionPayload.getGasUsed(),
        executionPayload.getTimestamp(),
        executionPayload.getExtraData(),
        executionPayload.getBaseFeePerGas(),
        executionPayload.getBlockHash(),
        getTransactions(executionPayload),
        getWithdrawals(ExecutionPayloadCapella.required(executionPayload).getWithdrawals()),
        ExecutionPayloadDeneb.required(executionPayload).getBlobGasUsed(),
        ExecutionPayloadDeneb.required(executionPayload).getExcessBlobGas());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ExecutionPayloadV3 that = (ExecutionPayloadV3) o;
    return Objects.equals(blobGasUsed, that.blobGasUsed)
        && Objects.equals(excessBlobGas, that.excessBlobGas);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), blobGasUsed, excessBlobGas);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("parentHash", parentHash)
        .add("feeRecipient", feeRecipient)
        .add("stateRoot", stateRoot)
        .add("receiptsRoot", receiptsRoot)
        .add("logsBloom", logsBloom)
        .add("prevRandao", prevRandao)
        .add("blockNumber", blockNumber)
        .add("gasLimit", gasLimit)
        .add("gasUsed", gasUsed)
        .add("timestamp", timestamp)
        .add("extraData", extraData)
        .add("baseFeePerGas", baseFeePerGas)
        .add("blockHash", blockHash)
        .add("transactions", transactions)
        .add("withdrawals", withdrawals)
        .add("blobGasUsed", blobGasUsed)
        .add("excessBlobGas", excessBlobGas)
        .toString();
  }
}
