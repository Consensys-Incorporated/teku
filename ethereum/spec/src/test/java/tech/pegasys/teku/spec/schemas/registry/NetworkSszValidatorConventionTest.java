/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_BEACON_BLOCK_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszOptionalSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

/**
 * Network validators are consulted only on the schema a gossip or RPC message is decoded with, so
 * declaring one on a nested schema is dead code. This test pins the set of schemas that declare one
 * to the wire-level schemas that actually need it.
 */
@TestSpecContext(allMilestones = true)
class NetworkSszValidatorConventionTest {

  @TestTemplate
  void onlyWireLevelSchemasDeclareNetworkValidators(final SpecContext specContext) {
    final SchemaRegistry registry =
        specContext.getSpec().getGenesisSchemaDefinitions().getSchemaRegistry();

    final Set<SszSchema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<SszSchema<?>> withValidator = Collections.newSetFromMap(new IdentityHashMap<>());
    for (final SchemaId<?> schemaId : allSchemaIds()) {
      registeredSchema(registry, schemaId)
          .ifPresent(schema -> walk(schema, visited, withValidator));
    }

    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS)) {
      assertThat(withValidator)
          .containsExactlyInAnyOrder(
              registry.get(SIGNED_BEACON_BLOCK_SCHEMA),
              registry.get(SIGNED_EXECUTION_PAYLOAD_ENVELOPE_SCHEMA));
    } else {
      assertThat(withValidator).isEmpty();
    }
  }

  private static void walk(
      final SszSchema<?> schema,
      final Set<SszSchema<?>> visited,
      final Set<SszSchema<?>> withValidator) {
    if (!visited.add(schema)) {
      return;
    }
    if (schema.getNetworkSszValidator().isPresent()) {
      withValidator.add(schema);
    }
    switch (schema) {
      case SszContainerSchema<?> container -> {
        for (int i = 0; i < container.getFieldsCount(); i++) {
          walk(container.getChildSchema(i), visited, withValidator);
        }
      }
      case SszCollectionSchema<?, ?> collection ->
          walk(collection.getElementSchema(), visited, withValidator);
      case SszOptionalSchema<?, ?> optional ->
          walk(optional.getChildSchema(), visited, withValidator);
      default -> {}
    }
  }

  private static Optional<SszSchema<?>> registeredSchema(
      final SchemaRegistry registry, final SchemaId<?> schemaId) {
    try {
      final Object schema = registry.get(schemaId);
      return schema instanceof SszSchema<?> sszSchema ? Optional.of(sszSchema) : Optional.empty();
    } catch (final IllegalArgumentException notRegisteredForMilestone) {
      return Optional.empty();
    }
  }

  private static List<SchemaId<?>> allSchemaIds() {
    return Arrays.stream(SchemaTypes.class.getDeclaredFields())
        .filter(field -> Modifier.isStatic(field.getModifiers()))
        .filter(field -> SchemaId.class.isAssignableFrom(field.getType()))
        .map(NetworkSszValidatorConventionTest::readSchemaId)
        .toList();
  }

  private static SchemaId<?> readSchemaId(final Field field) {
    try {
      return (SchemaId<?>) field.get(null);
    } catch (final IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
