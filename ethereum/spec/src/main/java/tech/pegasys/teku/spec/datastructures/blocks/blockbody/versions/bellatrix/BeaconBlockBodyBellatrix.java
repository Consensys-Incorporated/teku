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

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;

/** A Beacon block body */
public interface BeaconBlockBodyBellatrix extends BeaconBlockBodyAltair {

  static BeaconBlockBodyBellatrix required(final BeaconBlockBody body) {
    return body.toVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected bellatrix block body but got " + body.getClass().getSimpleName()));
  }

  ExecutionPayload getExecutionPayload();

  @Override
  default Optional<ExecutionPayload> getOptionalExecutionPayload() {
    return Optional.of(getExecutionPayload());
  }

  @Override
  default Optional<ExecutionPayloadSummary> getOptionalExecutionPayloadSummary() {
    return Optional.of(getExecutionPayload());
  }

  @Override
  BeaconBlockBodySchemaBellatrix<?> getSchema();

  @Override
  default Optional<BeaconBlockBodyBellatrix> toVersionBellatrix() {
    return Optional.of(this);
  }
}
