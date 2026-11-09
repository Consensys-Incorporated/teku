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

package tech.pegasys.teku.validator.api.required;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class ValidatorRequiredApiTypes {

  public static final DeserializableTypeDefinition<SyncingStatus> SYNCING_STATUS =
      DeserializableTypeDefinition.object(SyncingStatus.class, SyncingStatus.Builder.class)
          .name("SyncingStatus")
          .initializer(SyncingStatus.Builder::builder)
          .finisher(SyncingStatus.Builder::build)
          .withField(
              "head_slot",
              CoreTypes.UINT64_TYPE,
              SyncingStatus::getHeadSlot,
              SyncingStatus.Builder::headSlot)
          .withField(
              "sync_distance",
              CoreTypes.UINT64_TYPE,
              SyncingStatus::getSyncDistance,
              SyncingStatus.Builder::syncDistance)
          .withField(
              "is_syncing",
              CoreTypes.BOOLEAN_TYPE,
              SyncingStatus::isSyncing,
              SyncingStatus.Builder::isSyncing)
          .withOptionalField(
              "is_optimistic",
              CoreTypes.BOOLEAN_TYPE,
              SyncingStatus::getIsOptimistic,
              SyncingStatus.Builder::isOptimistic)
          .withOptionalField(
              "el_offline",
              CoreTypes.BOOLEAN_TYPE,
              SyncingStatus::isElOffline,
              SyncingStatus.Builder::elOffline)
          .build();

  public static final DeserializableTypeDefinition<SyncingStatus> SYNCING_STATUS_RESPONSE =
      DeserializableTypeDefinition.object(SyncingStatus.class, SyncingStatus.Builder.class)
          .name("SyncingStatusResponse")
          .initializer(SyncingStatus.Builder::builder)
          .finisher(SyncingStatus.Builder::build)
          .withField(
              "data",
              SYNCING_STATUS,
              Function.identity(),
              (builder, syncingStatus) ->
                  builder
                      .headSlot(syncingStatus.getHeadSlot())
                      .syncDistance(syncingStatus.getSyncDistance())
                      .isSyncing(syncingStatus.isSyncing())
                      .isOptimistic(syncingStatus.getIsOptimistic())
                      .elOffline(syncingStatus.isElOffline()))
          .build();
}
