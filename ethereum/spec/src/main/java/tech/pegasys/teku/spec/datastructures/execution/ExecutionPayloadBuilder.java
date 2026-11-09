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

import java.util.List;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;

public interface ExecutionPayloadBuilder {
  ExecutionPayloadBuilder parentHash(Bytes32 parentHash);

  ExecutionPayloadBuilder feeRecipient(Bytes20 feeRecipient);

  ExecutionPayloadBuilder stateRoot(Bytes32 stateRoot);

  ExecutionPayloadBuilder receiptsRoot(Bytes32 receiptsRoot);

  ExecutionPayloadBuilder logsBloom(Bytes logsBloom);

  ExecutionPayloadBuilder prevRandao(Bytes32 prevRandao);

  ExecutionPayloadBuilder blockNumber(UInt64 blockNumber);

  ExecutionPayloadBuilder gasLimit(UInt64 gasLimit);

  ExecutionPayloadBuilder gasUsed(UInt64 gasUsed);

  ExecutionPayloadBuilder timestamp(UInt64 timestamp);

  ExecutionPayloadBuilder extraData(Bytes extraData);

  ExecutionPayloadBuilder baseFeePerGas(UInt256 baseFeePerGas);

  ExecutionPayloadBuilder blockHash(Bytes32 blockHash);

  ExecutionPayloadBuilder transactions(List<Bytes> transactions);

  ExecutionPayloadBuilder withdrawals(Supplier<List<Withdrawal>> withdrawalsSupplier);

  ExecutionPayloadBuilder blobGasUsed(Supplier<UInt64> blobGasUsedSupplier);

  ExecutionPayloadBuilder excessBlobGas(Supplier<UInt64> excessBlobGasSupplier);

  ExecutionPayloadBuilder blockAccessList(Supplier<Bytes> blockAccessListSupplier);

  ExecutionPayloadBuilder slotNumber(Supplier<UInt64> slotNumberSupplier);

  ExecutionPayload build();
}
