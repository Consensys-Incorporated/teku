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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;

/** A Beacon block body */
public interface BlindedBeaconBlockBodyBellatrix extends BeaconBlockBodyAltair {

  static BlindedBeaconBlockBodyBellatrix required(final BeaconBlockBody body) {
    checkArgument(
        body instanceof BlindedBeaconBlockBodyBellatrix,
        "Expected bellatrix blinded block body but got %s",
        body.getClass());
    return (BlindedBeaconBlockBodyBellatrix) body;
  }

  ExecutionPayloadHeader getExecutionPayloadHeader();

  @Override
  default Optional<ExecutionPayloadHeader> getOptionalExecutionPayloadHeader() {
    return Optional.of(getExecutionPayloadHeader());
  }

  @Override
  default Optional<ExecutionPayloadSummary> getOptionalExecutionPayloadSummary() {
    return Optional.of(getExecutionPayloadHeader());
  }

  @Override
  default boolean isBlinded() {
    return true;
  }

  @Override
  BlindedBeaconBlockBodySchemaBellatrix<?> getSchema();

  @Override
  default Optional<BlindedBeaconBlockBodyBellatrix> toBlindedVersionBellatrix() {
    return Optional.of(this);
  }
}
