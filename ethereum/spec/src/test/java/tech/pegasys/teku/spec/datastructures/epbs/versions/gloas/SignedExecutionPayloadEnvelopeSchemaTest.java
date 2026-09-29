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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * The execution request and withdrawal count limits of {@code
 * validate_execution_payload_envelope_gossip} are enforced by the progressive list schemas.
 */
class SignedExecutionPayloadEnvelopeSchemaTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.ONE;
  private final SpecConfigGloas config =
      SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
  private final SignedExecutionPayloadEnvelopeSchema schema =
      schemaDefinitions.getSignedExecutionPayloadEnvelopeSchema();

  @Test
  void sszDeserialize_shouldRoundTripValidEnvelope() {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(slot.longValue());
    assertThat(schema.sszDeserialize(envelope.sszSerialize())).isEqualTo(envelope);
  }

  @Test
  void sszDeserialize_shouldRejectTooManyWithdrawals() {
    final int limit = config.getMaxWithdrawalsPerPayload();
    final ExecutionPayload payload =
        dataStructureUtil.withOversizedProgressiveListField(
            dataStructureUtil.randomExecutionPayload(slot),
            ExecutionPayloadFields.WITHDRAWALS,
            dataStructureUtil::randomWithdrawal,
            limit + 1);
    final SignedExecutionPayloadEnvelope envelope =
        envelope(payload, dataStructureUtil.randomExecutionRequests(slot));

    assertThatThrownBy(() -> schema.sszDeserialize(envelope.sszSerialize()))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("List length %d exceeds max length %d", limit + 1, limit);
  }

  @Test
  void sszDeserialize_shouldRejectTooManyWithdrawalRequests() {
    final int limit = config.getMaxWithdrawalRequestsPerPayload();
    final ExecutionRequests requests =
        dataStructureUtil.withOversizedProgressiveListField(
            dataStructureUtil.randomExecutionRequests(slot),
            ExecutionRequestsFields.WITHDRAWALS,
            dataStructureUtil::randomWithdrawalRequest,
            limit + 1);
    final SignedExecutionPayloadEnvelope envelope =
        envelope(dataStructureUtil.randomExecutionPayload(slot), requests);

    assertThatThrownBy(() -> schema.sszDeserialize(envelope.sszSerialize()))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage("List length %d exceeds max length %d", limit + 1, limit);
  }

  private SignedExecutionPayloadEnvelope envelope(
      final ExecutionPayload payload, final ExecutionRequests requests) {
    return schema.create(
        schemaDefinitions
            .getExecutionPayloadEnvelopeSchema()
            .create(
                payload,
                requests,
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32()),
        dataStructureUtil.randomSignature());
  }
}
