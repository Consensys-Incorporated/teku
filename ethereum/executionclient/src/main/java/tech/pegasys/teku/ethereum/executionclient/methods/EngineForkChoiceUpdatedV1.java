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

package tech.pegasys.teku.ethereum.executionclient.methods;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.response.ResponseUnwrapper;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class EngineForkChoiceUpdatedV1
    extends AbstractEngineJsonRpcMethod<
        tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> {

  private static final Logger LOG = LogManager.getLogger();

  public EngineForkChoiceUpdatedV1(final ExecutionEngineClient executionEngineClient) {
    super(executionEngineClient);
  }

  @Override
  public String getName() {
    return EngineApiMethod.ENGINE_FORK_CHOICE_UPDATED.getName();
  }

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult> execute(
      final JsonRpcRequestParams params) {
    final ForkChoiceState forkChoiceState = params.getRequiredParameter(0, ForkChoiceState.class);
    final Optional<PayloadBuildingAttributes> payloadBuildingAttributes =
        params.getOptionalParameter(1, PayloadBuildingAttributes.class);

    LOG.trace(
        "Calling {}(forkChoiceState={}, payloadAttributes={})",
        getVersionedName(),
        forkChoiceState,
        payloadBuildingAttributes);

    return executionEngineClient
        .forkChoiceUpdatedV1(
            ForkChoiceStateV1.fromInternalForkChoiceState(forkChoiceState),
            PayloadAttributesV1.fromInternalPayloadBuildingAttributes(payloadBuildingAttributes))
        .thenApply(ResponseUnwrapper::unwrapExecutionClientResponseOrThrow)
        .thenApply(ForkChoiceUpdatedResult::asInternalExecutionPayload)
        .thenPeek(
            forkChoiceUpdatedResult ->
                LOG.trace(
                    "Response {}(forkChoiceState={}, payloadAttributes={}) -> {}",
                    getVersionedName(),
                    forkChoiceState,
                    payloadBuildingAttributes,
                    forkChoiceUpdatedResult));
  }
}
