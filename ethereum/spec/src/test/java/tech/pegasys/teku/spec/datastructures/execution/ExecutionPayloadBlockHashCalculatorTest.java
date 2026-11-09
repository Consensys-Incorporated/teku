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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

@TestSpecContext(milestone = {GLOAS})
class ExecutionPayloadBlockHashCalculatorTest {

  private static final UInt64 SLOT = UInt64.valueOf(12);
  private static final UInt64 GAS_LIMIT = UInt64.valueOf(30_000_000);
  private static final UInt64 TIMESTAMP = UInt64.valueOf(1_234_567);
  private static final Bytes EMPTY_BLOCK_ACCESS_LIST = createEmptyBlockAccessList();

  private Spec spec;
  private SchemaDefinitionsGloas schemaDefinitions;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    schemaDefinitions = SchemaDefinitionsGloas.required(spec.atSlot(SLOT).getSchemaDefinitions());
  }

  @TestTemplate
  void computeGloasBlockHashChangesWhenPayloadBodyChanges() {
    final ExecutionRequests executionRequests =
        schemaDefinitions.getExecutionRequestsSchema().createBuilder().build();
    final ExecutionPayload payload = createExecutionPayload(Bytes32.ZERO, Bytes32.ZERO);
    final ExecutionPayloadEnvelope envelope = createEnvelope(payload, executionRequests);

    final Bytes32 originalBlockHash =
        ExecutionPayloadBlockHashCalculator.computeGloasBlockHash(
            envelope, spec.getExecutionRequestsDataCodec(SLOT));

    final ExecutionPayload tamperedPayload =
        createExecutionPayload(Bytes32.ZERO.not(), Bytes32.ZERO);
    final ExecutionPayloadEnvelope tamperedEnvelope =
        createEnvelope(tamperedPayload, executionRequests);

    assertThat(
            ExecutionPayloadBlockHashCalculator.computeGloasBlockHash(
                tamperedEnvelope, spec.getExecutionRequestsDataCodec(SLOT)))
        .isNotEqualTo(originalBlockHash);
  }

  private ExecutionPayloadEnvelope createEnvelope(
      final ExecutionPayload payload, final ExecutionRequests executionRequests) {
    return schemaDefinitions
        .getExecutionPayloadEnvelopeSchema()
        .create(payload, executionRequests, UInt64.ZERO, Bytes32.ZERO, Bytes32.ZERO);
  }

  private ExecutionPayload createExecutionPayload(
      final Bytes32 stateRoot, final Bytes32 blockHash) {
    return schemaDefinitions
        .getExecutionPayloadSchema()
        .createExecutionPayload(
            builder ->
                builder
                    .parentHash(Bytes32.ZERO)
                    .feeRecipient(Bytes20.ZERO)
                    .stateRoot(stateRoot)
                    .receiptsRoot(Bytes32.ZERO)
                    .logsBloom(Bytes.wrap(new byte[256]))
                    .prevRandao(Bytes32.ZERO)
                    .blockNumber(SLOT)
                    .gasLimit(GAS_LIMIT)
                    .gasUsed(UInt64.ZERO)
                    .timestamp(TIMESTAMP)
                    .extraData(Bytes.EMPTY)
                    .baseFeePerGas(UInt256.ONE)
                    .blockHash(blockHash)
                    .transactions(List.of())
                    .withdrawals(List::of)
                    .blobGasUsed(() -> UInt64.ZERO)
                    .excessBlobGas(() -> UInt64.ZERO)
                    .blockAccessList(() -> EMPTY_BLOCK_ACCESS_LIST)
                    .slotNumber(() -> SLOT));
  }

  private static Bytes createEmptyBlockAccessList() {
    final BytesValueRLPOutput rlpOutput = new BytesValueRLPOutput();
    rlpOutput.startList();
    rlpOutput.endList();
    return rlpOutput.encoded();
  }
}
