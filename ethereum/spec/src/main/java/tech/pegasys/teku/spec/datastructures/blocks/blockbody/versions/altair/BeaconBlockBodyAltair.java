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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;

/** A Beacon block body */
public interface BeaconBlockBodyAltair extends BeaconBlockBody {

  static BeaconBlockBodyAltair required(final BeaconBlockBody body) {
    return body.toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected altair block body but got " + body.getClass().getSimpleName()));
  }

  SyncAggregate getSyncAggregate();

  @Override
  default Optional<SyncAggregate> getOptionalSyncAggregate() {
    return Optional.of(getSyncAggregate());
  }

  @Override
  BeaconBlockBodySchemaAltair<?> getSchema();

  @Override
  default Optional<BeaconBlockBodyAltair> toVersionAltair() {
    return Optional.of(this);
  }
}
