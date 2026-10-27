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

package tech.pegasys.teku.spec.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadBlockHashCalculator;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionLayerChannelStubTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ExecutionLayerChannelStub executionLayer =
      new ExecutionLayerChannelStub(spec, SYSTEM_TIME_PROVIDER, List.of("blobs=0"), false);

  @Test
  void engineGetPayload_shouldReturnGloasPayloadWithBlockHashComputedFromEnvelopeContent() {
    final UInt64 slot = UInt64.ONE;
    final ForkChoiceState forkChoiceState = dataStructureUtil.randomForkChoiceState(false);
    final PayloadBuildingAttributes payloadBuildingAttributes =
        dataStructureUtil.randomPayloadBuildingAttributes(false);
    final Bytes8 payloadId =
        safeJoin(
                executionLayer.engineForkChoiceUpdated(
                    forkChoiceState, Optional.of(payloadBuildingAttributes)))
            .getPayloadId()
            .orElseThrow();
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);

    final GetPayloadResponse getPayloadResponse =
        safeJoin(
            executionLayer.engineGetPayload(
                new ExecutionPayloadContext(payloadId, forkChoiceState, payloadBuildingAttributes),
                state));

    // the envelope a proposer builds from this response
    final ExecutionPayloadEnvelope envelope =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
            .getExecutionPayloadEnvelopeSchema()
            .create(
                getPayloadResponse.getExecutionPayload(),
                getPayloadResponse.getExecutionRequests().orElseThrow(),
                dataStructureUtil.randomBuilderIndex(),
                dataStructureUtil.randomBytes32(),
                payloadBuildingAttributes.parentBeaconBlock().blockRoot());
    assertThat(
            ExecutionPayloadBlockHashCalculator.computeGloasBlockHash(
                envelope, spec.getExecutionRequestsDataCodec(slot)))
        .isEqualTo(getPayloadResponse.getExecutionPayload().getBlockHash());
  }
}
