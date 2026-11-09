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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.ExecutionPayloadDeneb;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface BeaconBlockBodyDeneb extends BeaconBlockBodyCapella {
  static BeaconBlockBodyDeneb required(final BeaconBlockBody body) {
    return body.toVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Deneb block body but got " + body.getClass().getSimpleName()));
  }

  SszList<SszKZGCommitment> getBlobKzgCommitments();

  @Override
  default Optional<SszList<SszKZGCommitment>> getOptionalBlobKzgCommitments() {
    return Optional.of(getBlobKzgCommitments());
  }

  @Override
  BeaconBlockBodySchemaDeneb<?> getSchema();

  @Override
  ExecutionPayloadDeneb getExecutionPayload();

  @Override
  default Optional<BeaconBlockBodyDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}
