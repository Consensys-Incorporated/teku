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

package tech.pegasys.teku.spec.schemas.registry;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

interface SchemaCache {
  static SchemaCache createDefault() {
    return new SchemaCache() {
      private final Map<SpecMilestone, Map<SchemaId<?>, Object>> cache =
          new EnumMap<>(SpecMilestone.class);

      @SuppressWarnings("unchecked")
      @Override
      public <T> T get(final SpecMilestone milestone, final SchemaId<T> schemaId) {
        final Map<?, ?> milestoneSchemaIds = cache.get(milestone);
        if (milestoneSchemaIds == null) {
          return null;
        }
        return (T) milestoneSchemaIds.get(schemaId);
      }

      @Override
      public <T> void put(
          final SpecMilestone milestone, final SchemaId<T> schemaId, final T schema) {
        cache.computeIfAbsent(milestone, __ -> new HashMap<>()).put(schemaId, schema);
      }
    };
  }

  <T> T get(SpecMilestone milestone, SchemaId<T> schemaId);

  <T> void put(SpecMilestone milestone, SchemaId<T> schemaId, T schema);
}
