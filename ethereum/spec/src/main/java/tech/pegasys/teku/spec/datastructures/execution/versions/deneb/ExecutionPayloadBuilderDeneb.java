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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBuilder;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadBuilderCapella;

public class ExecutionPayloadBuilderDeneb extends ExecutionPayloadBuilderCapella {
  private ExecutionPayloadSchemaDeneb schema;

  protected UInt64 blobGasUsed;
  protected UInt64 excessBlobGas;

  public ExecutionPayloadBuilderDeneb schema(final ExecutionPayloadSchemaDeneb schema) {
    this.schema = schema;
    return this;
  }

  @Override
  public ExecutionPayloadBuilder blobGasUsed(final Supplier<UInt64> blobGasUsedSupplier) {
    this.blobGasUsed = blobGasUsedSupplier.get();
    return this;
  }

  @Override
  public ExecutionPayloadBuilderDeneb excessBlobGas(final Supplier<UInt64> excessBlobGasSupplier) {
    this.excessBlobGas = excessBlobGasSupplier.get();
    return this;
  }

  @Override
  protected void validateSchema() {
    checkNotNull(schema, "schema must be specified");
  }

  @Override
  protected void validate() {
    super.validate();
    checkNotNull(blobGasUsed, "blobGasUsed must be specified");
    checkNotNull(excessBlobGas, "excessBlobGas must be specified");
  }

  @Override
  public ExecutionPayload build() {
    validate();
    return new ExecutionPayloadDenebImpl(
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
        transactions.stream()
            .map(schema.getTransactionSchema()::fromBytes)
            .collect(schema.getTransactionsSchema().collector()),
        schema.getWithdrawalsSchema().createFromElements(withdrawals),
        SszUInt64.of(blobGasUsed),
        SszUInt64.of(excessBlobGas));
  }
}
