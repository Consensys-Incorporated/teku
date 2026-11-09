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

package tech.pegasys.teku.beaconrestapi.handlers.v1.beacon;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_CONSENSUS_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class MilestoneDependentTypesUtil {

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getSchemaDefinitionForAllSupportedMilestones(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final Function<SchemaDefinitions, SszSchema<? extends T>> schemaGetter,
          final BiPredicate<T, SpecMilestone> predicate) {
    final SerializableOneOfTypeDefinitionBuilder<T> builder =
        new SerializableOneOfTypeDefinitionBuilder<T>().title(title);
    for (SpecMilestone milestone : schemaDefinitionCache.getSupportedMilestones()) {
      final DeserializableTypeDefinition<? extends T> jsonTypeDefinition =
          schemaGetter
              .apply(schemaDefinitionCache.getSchemaDefinition(milestone))
              .getJsonTypeDefinition();
      builder.withType(value -> predicate.test(value, milestone), jsonTypeDefinition);
    }
    return builder.build();
  }

  public static <T extends SszData>
      SerializableOneOfTypeDefinition<T> getMultipleSchemaDefinitionFromMilestone(
          final SchemaDefinitionCache schemaDefinitionCache,
          final String title,
          final List<ConditionalSchemaGetter<T>> schemaGetterList) {
    final SerializableOneOfTypeDefinitionBuilder<T> builder =
        new SerializableOneOfTypeDefinitionBuilder<T>().title(title);
    for (SpecMilestone milestone : schemaDefinitionCache.getSupportedMilestones()) {
      schemaGetterList.forEach(
          schemaGetter -> {
            if (milestone.isGreaterThanOrEqualTo(schemaGetter.earliestMilestone())
                && schemaGetter
                    .latestMilestone()
                    .map(latest -> !milestone.isGreaterThan(latest))
                    .orElse(true)) {
              final DeserializableTypeDefinition<? extends T> jsonTypeDefinition =
                  schemaGetter
                      .schemaGetter()
                      .apply(schemaDefinitionCache.getSchemaDefinition(milestone))
                      .getJsonTypeDefinition();
              builder.withType(
                  value -> schemaGetter.schemaPredicate().test(value, milestone),
                  jsonTypeDefinition);
            }
          });
    }
    return builder.build();
  }

  public static <T extends SszData>
      DeserializableTypeDefinition<? extends T> headerBasedSelectorWithSlotFallback(
          final Map<String, String> headers,
          final String json,
          final SchemaDefinitionCache schemaDefinitionCache,
          final Function<SchemaDefinitions, SszSchema<? extends T>> getSchema) {
    if (headers.containsKey(HEADER_CONSENSUS_VERSION)) {
      return headerBasedSelector(headers, schemaDefinitionCache, getSchema);
    }
    return slotBasedSelector(json, schemaDefinitionCache, getSchema);
  }

  public static <T extends SszData> DeserializableTypeDefinition<? extends T> headerBasedSelector(
      final Map<String, String> headers,
      final SchemaDefinitionCache schemaDefinitionCache,
      final Function<SchemaDefinitions, SszSchema<? extends T>> getSchema) {
    return headerBasedSelector(
        schemaDefinitionCache,
        Optional.ofNullable(headers.get(HEADER_CONSENSUS_VERSION)),
        (__, schemaDefinitions) -> getSchema.apply(schemaDefinitions).getJsonTypeDefinition());
  }

  public static <Y> Y headerBasedSelector(
      final SchemaDefinitionCache schemaDefinitionCache,
      final Optional<String> headerConsensusVersion,
      final BiFunction<SpecMilestone, SchemaDefinitions, Y> parser) {
    if (headerConsensusVersion.isEmpty()) {
      throw new BadRequestException(
          String.format("Missing required header value for (%s)", HEADER_CONSENSUS_VERSION));
    }
    try {
      final SpecMilestone milestone = SpecMilestone.forName(headerConsensusVersion.get());
      return parser.apply(milestone, schemaDefinitionCache.getSchemaDefinition(milestone));
    } catch (Exception __) {
      throw new BadRequestException(
          String.format(
              "Invalid value for (%s) header: %s",
              HEADER_CONSENSUS_VERSION, headerConsensusVersion.get()));
    }
  }

  private static <T extends SszData> DeserializableTypeDefinition<? extends T> slotBasedSelector(
      final String json,
      final SchemaDefinitionCache schemaDefinitionCache,
      final Function<SchemaDefinitions, SszSchema<? extends T>> getSchema) {
    final Optional<UInt64> slot =
        // SignedBeaconBlock
        getSlot(json, "message", "slot")
            // SignedBlockContents
            .or(() -> getSlot(json, "signed_block", "message", "slot"));
    final SpecMilestone milestone =
        schemaDefinitionCache.milestoneAtSlot(
            slot.orElseThrow(() -> new BadRequestException("Could not locate slot in JSON data")));
    return getSchema
        .apply(schemaDefinitionCache.getSchemaDefinition(milestone))
        .getJsonTypeDefinition();
  }

  private static Optional<UInt64> getSlot(final String json, final String... path) {
    try {
      return JsonUtil.getAttribute(json, CoreTypes.UINT64_TYPE, path);
    } catch (final JsonProcessingException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  public record ConditionalSchemaGetter<T>(
      BiPredicate<T, SpecMilestone> schemaPredicate,
      SpecMilestone earliestMilestone,
      Optional<SpecMilestone> latestMilestone,
      Function<SchemaDefinitions, SszSchema<? extends T>> schemaGetter) {

    public ConditionalSchemaGetter(
        final BiPredicate<T, SpecMilestone> schemaPredicate,
        final SpecMilestone earliestMilestone,
        final Function<SchemaDefinitions, SszSchema<? extends T>> schemaGetter) {
      this(schemaPredicate, earliestMilestone, Optional.empty(), schemaGetter);
    }
  }
}
