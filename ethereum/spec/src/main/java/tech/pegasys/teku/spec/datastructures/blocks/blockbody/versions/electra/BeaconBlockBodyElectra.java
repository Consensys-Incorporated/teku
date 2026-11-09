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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;

public interface BeaconBlockBodyElectra extends BeaconBlockBodyDeneb {
  static BeaconBlockBodyElectra required(final BeaconBlockBody body) {
    return body.toVersionElectra()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Electra block body but got " + body.getClass().getSimpleName()));
  }

  @Override
  BeaconBlockBodySchemaElectra<?> getSchema();

  ExecutionRequests getExecutionRequests();

  @Override
  default Optional<ExecutionRequests> getOptionalExecutionRequests() {
    return Optional.of(getExecutionRequests());
  }

  @Override
  default Optional<BeaconBlockBodyElectra> toVersionElectra() {
    return Optional.of(this);
  }
}
