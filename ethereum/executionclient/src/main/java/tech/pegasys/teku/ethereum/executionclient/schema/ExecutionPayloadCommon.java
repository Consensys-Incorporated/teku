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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes20Serializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt256AsHexSerializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.UInt64AsHexSerializer;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionPayloadCommon {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 parentHash;

  @JsonSerialize(using = Bytes20Serializer.class)
  @JsonDeserialize(using = Bytes20Deserializer.class)
  public final Bytes20 feeRecipient;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 stateRoot;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 receiptsRoot;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public final Bytes logsBloom;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 prevRandao;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 blockNumber;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 gasLimit;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 gasUsed;

  @JsonSerialize(using = UInt64AsHexSerializer.class)
  @JsonDeserialize(using = UInt64AsHexDeserializer.class)
  public final UInt64 timestamp;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = BytesDeserializer.class)
  public final Bytes extraData;

  @JsonSerialize(using = UInt256AsHexSerializer.class)
  @JsonDeserialize(using = UInt256AsHexDeserializer.class)
  public final UInt256 baseFeePerGas;

  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  public final Bytes32 blockHash;

  public ExecutionPayloadCommon(
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
      final @JsonProperty("blockHash") Bytes32 blockHash) {
    checkNotNull(parentHash, "parentHash");
    checkNotNull(feeRecipient, "feeRecipient");
    checkNotNull(stateRoot, "stateRoot");
    checkNotNull(receiptsRoot, "receiptsRoot");
    checkNotNull(logsBloom, "logsBloom");
    checkNotNull(prevRandao, "prevRandao");
    checkNotNull(blockNumber, "blockNumber");
    checkNotNull(gasLimit, "gasLimit");
    checkNotNull(gasUsed, "gasUsed");
    checkNotNull(timestamp, "timestamp");
    checkNotNull(extraData, "extraData");
    checkNotNull(baseFeePerGas, "baseFeePerGas");
    checkNotNull(blockHash, "blockHash");
    this.parentHash = parentHash;
    this.feeRecipient = feeRecipient;
    this.stateRoot = stateRoot;
    this.receiptsRoot = receiptsRoot;
    this.logsBloom = logsBloom;
    this.prevRandao = prevRandao;
    this.blockNumber = blockNumber;
    this.gasLimit = gasLimit;
    this.gasUsed = gasUsed;
    this.timestamp = timestamp;
    this.extraData = extraData;
    this.baseFeePerGas = baseFeePerGas;
    this.blockHash = blockHash;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadCommon that = (ExecutionPayloadCommon) o;
    return Objects.equals(parentHash, that.parentHash)
        && Objects.equals(feeRecipient, that.feeRecipient)
        && Objects.equals(stateRoot, that.stateRoot)
        && Objects.equals(receiptsRoot, that.receiptsRoot)
        && Objects.equals(logsBloom, that.logsBloom)
        && Objects.equals(prevRandao, that.prevRandao)
        && Objects.equals(blockNumber, that.blockNumber)
        && Objects.equals(gasLimit, that.gasLimit)
        && Objects.equals(gasUsed, that.gasUsed)
        && Objects.equals(timestamp, that.timestamp)
        && Objects.equals(extraData, that.extraData)
        && Objects.equals(baseFeePerGas, that.baseFeePerGas)
        && Objects.equals(blockHash, that.blockHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
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
        blockHash);
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
        .toString();
  }
}
