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

import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class ExecutionPayloadContext {
  private final Bytes8 payloadId;
  private final ForkChoiceState forkChoiceState;
  private final PayloadBuildingAttributes payloadBuildingAttributes;

  public ExecutionPayloadContext(
      final Bytes8 payloadId,
      final ForkChoiceState forkChoiceState,
      final PayloadBuildingAttributes payloadBuildingAttributes) {
    this.payloadId = payloadId;
    this.forkChoiceState = forkChoiceState;
    this.payloadBuildingAttributes = payloadBuildingAttributes;
  }

  public Bytes8 getPayloadId() {
    return payloadId;
  }

  public ForkChoiceState getForkChoiceState() {
    return forkChoiceState;
  }

  public PayloadBuildingAttributes getPayloadBuildingAttributes() {
    return payloadBuildingAttributes;
  }

  public Bytes32 getParentHash() {
    return forkChoiceState.headExecutionBlockHash();
  }

  public boolean isValidatorRegistrationPresent() {
    return payloadBuildingAttributes.validatorRegistration().isPresent();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ExecutionPayloadContext that = (ExecutionPayloadContext) o;
    return Objects.equals(payloadId, that.payloadId)
        && Objects.equals(forkChoiceState, that.forkChoiceState)
        && Objects.equals(payloadBuildingAttributes, that.payloadBuildingAttributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(payloadId, forkChoiceState, payloadBuildingAttributes);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("payloadId", payloadId)
        .add("forkChoiceState", forkChoiceState)
        .add("payloadBuildingAttributes", payloadBuildingAttributes)
        .toString();
  }
}
