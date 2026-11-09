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

package tech.pegasys.teku.spec.datastructures.execution;

import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface ExecutionPayloadHeaderBuilder {
  ExecutionPayloadHeaderBuilder parentHash(Bytes32 parentHash);

  ExecutionPayloadHeaderBuilder feeRecipient(Bytes20 feeRecipient);

  ExecutionPayloadHeaderBuilder stateRoot(Bytes32 stateRoot);

  ExecutionPayloadHeaderBuilder receiptsRoot(Bytes32 receiptsRoot);

  ExecutionPayloadHeaderBuilder logsBloom(Bytes logsBloom);

  ExecutionPayloadHeaderBuilder prevRandao(Bytes32 prevRandao);

  ExecutionPayloadHeaderBuilder blockNumber(UInt64 blockNumber);

  ExecutionPayloadHeaderBuilder gasLimit(UInt64 gasLimit);

  ExecutionPayloadHeaderBuilder gasUsed(UInt64 gasUsed);

  ExecutionPayloadHeaderBuilder timestamp(UInt64 timestamp);

  ExecutionPayloadHeaderBuilder extraData(Bytes extraData);

  ExecutionPayloadHeaderBuilder baseFeePerGas(UInt256 baseFeePerGas);

  ExecutionPayloadHeaderBuilder blockHash(Bytes32 blockHash);

  ExecutionPayloadHeaderBuilder transactionsRoot(Bytes32 transactionRoot);

  ExecutionPayloadHeaderBuilder withdrawalsRoot(Supplier<Bytes32> withdrawalsRootSupplier);

  ExecutionPayloadHeaderBuilder blobGasUsed(Supplier<UInt64> blobGasUsedSupplier);

  ExecutionPayloadHeaderBuilder excessBlobGas(Supplier<UInt64> excessBlobGasSupplier);

  ExecutionPayloadHeaderBuilder blockAccessListRoot(Supplier<Bytes32> blockAccessListRootSupplier);

  ExecutionPayloadHeaderBuilder slotNumber(Supplier<UInt64> slotNumberSupplier);

  ExecutionPayloadHeader build();
}
