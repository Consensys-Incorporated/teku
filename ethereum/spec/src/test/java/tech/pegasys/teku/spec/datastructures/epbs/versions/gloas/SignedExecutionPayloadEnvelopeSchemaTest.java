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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

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
  void getNetworkSszValidator_shouldAcceptValidEnvelope() {
    final SignedExecutionPayloadEnvelope envelope =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(slot.longValue());
    assertThatNoException().isThrownBy(() -> validator().validate(envelope));
  }

  @Test
  void getNetworkSszValidator_shouldRejectTooManyWithdrawals() {
    final int limit = config.getMaxWithdrawalsPerPayload();
    final ExecutionPayload payload =
        dataStructureUtil.randomExecutionPayload(
            slot,
            builder ->
                builder.withdrawals(
                    () ->
                        IntStream.range(0, limit + 1)
                            .mapToObj(__ -> dataStructureUtil.randomWithdrawal())
                            .toList()));
    final SignedExecutionPayloadEnvelope envelope =
        envelope(payload, dataStructureUtil.randomExecutionRequests(slot));

    assertThatThrownBy(() -> validator().validate(envelope))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage(
            "Execution payload envelope has %d withdrawals, max allowed %d", limit + 1, limit);
  }

  @Test
  void getNetworkSszValidator_shouldRejectTooManyWithdrawalRequests() {
    final int limit = config.getMaxWithdrawalRequestsPerPayload();
    final List<WithdrawalRequest> tooManyWithdrawalRequests =
        IntStream.range(0, limit + 1)
            .mapToObj(__ -> dataStructureUtil.randomWithdrawalRequest())
            .toList();
    final ExecutionRequests requests =
        dataStructureUtil
            .randomExecutionRequestsBuilder(slot)
            .withdrawals(tooManyWithdrawalRequests)
            .build();
    final SignedExecutionPayloadEnvelope envelope =
        envelope(dataStructureUtil.randomExecutionPayload(slot), requests);

    assertThatThrownBy(() -> validator().validate(envelope))
        .isInstanceOf(SszDeserializeException.class)
        .hasMessage(
            "Execution payload envelope has %d withdrawal requests, max allowed %d",
            limit + 1, limit);
  }

  private SszNetworkValidator<SignedExecutionPayloadEnvelope> validator() {
    return schema.getNetworkSszValidator().orElseThrow();
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
