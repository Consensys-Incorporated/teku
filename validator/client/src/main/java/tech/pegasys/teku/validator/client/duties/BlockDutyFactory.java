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

package tech.pegasys.teku.validator.client.duties;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.BuilderConfigProvider;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;

public class BlockDutyFactory implements DutyFactory<BlockProductionDuty, Duty> {

  private final ForkProvider forkProvider;
  private final ValidatorApiChannel validatorApiChannel;
  private final BlockContainerSigner blockContainerSigner;
  private final Spec spec;
  private final ValidatorDutyMetrics validatorDutyMetrics;
  private final ExecutionPayloadBidEventsChannel executionPayloadBidEventsChannelPublisher;
  private final BuilderConfigProvider builderConfigProvider;

  public BlockDutyFactory(
      final ForkProvider forkProvider,
      final ValidatorApiChannel validatorApiChannel,
      final BlockContainerSigner blockContainerSigner,
      final Spec spec,
      final ValidatorDutyMetrics validatorDutyMetrics,
      final ExecutionPayloadBidEventsChannel executionPayloadBidEventsChannelPublisher,
      final BuilderConfigProvider builderConfigProvider) {
    this.forkProvider = forkProvider;
    this.validatorApiChannel = validatorApiChannel;
    this.blockContainerSigner = blockContainerSigner;
    this.spec = spec;
    this.validatorDutyMetrics = validatorDutyMetrics;
    this.executionPayloadBidEventsChannelPublisher = executionPayloadBidEventsChannelPublisher;
    this.builderConfigProvider = builderConfigProvider;
  }

  @Override
  public BlockProductionDuty createProductionDuty(final UInt64 slot, final Validator validator) {
    return new BlockProductionDuty(
        validator,
        slot,
        forkProvider,
        validatorApiChannel,
        blockContainerSigner,
        spec,
        validatorDutyMetrics,
        executionPayloadBidEventsChannelPublisher,
        builderConfigProvider);
  }

  @Override
  public Duty createAggregationDuty(final UInt64 slot, final Validator validator) {
    throw new UnsupportedOperationException("Aggregation not supported for blocks");
  }

  @Override
  public String getProductionType() {
    return "block";
  }

  @Override
  public String getAggregationType() {
    // Aggregation is never used but getters should be safe to call so return a placeholder
    return "";
  }
}
