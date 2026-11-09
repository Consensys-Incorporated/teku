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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;

public interface BlindedBeaconBlockBodyCapella extends BlindedBeaconBlockBodyBellatrix {
  static BlindedBeaconBlockBodyCapella required(final BeaconBlockBody body) {
    return body.toBlindedVersionCapella()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a capella blinded block body but got: "
                        + body.getClass().getSimpleName()));
  }

  SszList<SignedBlsToExecutionChange> getBlsToExecutionChanges();

  @Override
  default Optional<SszList<SignedBlsToExecutionChange>> getOptionalBlsToExecutionChanges() {
    return Optional.of(getBlsToExecutionChanges());
  }

  @Override
  default Optional<BlindedBeaconBlockBodyCapella> toBlindedVersionCapella() {
    return Optional.of(this);
  }

  @Override
  BlindedBeaconBlockBodySchemaCapella<?> getSchema();
}
