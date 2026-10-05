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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistryTest {

  private final SpecConfig specConfig = mock(SpecConfig.class);
  private final SchemaCache schemaCache = spy(SchemaCache.createDefault());

  @SuppressWarnings("unchecked")
  private final SchemaProvider<TestSchema> schemaProvider = mock(SchemaProvider.class);

  @SuppressWarnings("unchecked")
  private final SchemaId<TestSchema> schemaId = mock(SchemaId.class);

  private final SchemaRegistry schemaRegistry =
      new SchemaRegistry(SpecMilestone.ALTAIR, specConfig, schemaCache);

  @Test
  void shouldGetSchemaFromCache() {
    final TestSchema cachedSchema = new TestSchema("test", 2);
    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.ALTAIR, schemaId)).thenReturn(cachedSchema);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertEquals(cachedSchema, result);
    verify(schemaCache).get(SpecMilestone.ALTAIR, schemaId);
    verify(schemaProvider, never()).getSchema(any());
  }

  @Test
  void shouldGetNewInstanceWhenSchemaEqualityCheckIsDisabled() {

    // two different schema instances but equal
    final TestSchema newSchema = new TestSchema("test", 2);
    final TestSchema cachedPhase0Schema = new TestSchema("test1", 2);

    assertThat(newSchema).isNotSameAs(cachedPhase0Schema);
    assertThat(newSchema).isEqualTo(cachedPhase0Schema);

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.PHASE0, schemaId)).thenReturn(cachedPhase0Schema);
    when(schemaProvider.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.PHASE0);
    when(schemaProvider.getSchema(schemaRegistry)).thenReturn(newSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(true);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertThat(result).isSameAs(newSchema);

    verify(schemaCache, never()).get(SpecMilestone.PHASE0, schemaId);
    verify(schemaCache).get(SpecMilestone.ALTAIR, schemaId);
    verify(schemaProvider).getSchema(schemaRegistry);
    verify(schemaCache).put(SpecMilestone.ALTAIR, schemaId, result);
  }

  @Test
  void shouldGetPreviousMilestoneInstanceWhenSchemaAreEqual() {

    // two different schema instances but equal
    final TestSchema newSchema = new TestSchema("test", 2);
    final TestSchema cachedPhase0Schema = new TestSchema("test1", 2);

    assertThat(newSchema).isNotSameAs(cachedPhase0Schema);
    assertThat(newSchema).isEqualTo(cachedPhase0Schema);

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.PHASE0, schemaId)).thenReturn(cachedPhase0Schema);
    when(schemaProvider.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.PHASE0);
    when(schemaProvider.getSchema(schemaRegistry)).thenReturn(newSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(false);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertThat(result).isSameAs(cachedPhase0Schema);

    verify(schemaCache).get(SpecMilestone.PHASE0, schemaId);
    verify(schemaCache).get(SpecMilestone.ALTAIR, schemaId);
    verify(schemaProvider).getSchema(schemaRegistry);
    verify(schemaCache).put(SpecMilestone.ALTAIR, schemaId, result);
  }

  @Test
  void shouldGetNewInstanceWhenSchemaAreNotEqual() {

    // two different schema instances but equal
    final TestSchema newSchema = new TestSchema("test", 3);
    final TestSchema cachedPhase0Schema = new TestSchema("test1", 2);

    assertThat(newSchema).isNotEqualTo(cachedPhase0Schema);

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.PHASE0, schemaId)).thenReturn(cachedPhase0Schema);
    when(schemaProvider.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.PHASE0);
    when(schemaProvider.getSchema(schemaRegistry)).thenReturn(newSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(false);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertThat(result).isSameAs(newSchema);

    verify(schemaCache).get(SpecMilestone.PHASE0, schemaId);
    verify(schemaCache).get(SpecMilestone.ALTAIR, schemaId);
    verify(schemaProvider).getSchema(schemaRegistry);
    verify(schemaCache).put(SpecMilestone.ALTAIR, schemaId, result);
  }

  @Test
  void shouldGetNewInstanceWhenNoPreviousCachedSchemaExists() {
    final TestSchema newSchema = new TestSchema("test", 3);

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.PHASE0, schemaId)).thenReturn(null);
    when(schemaProvider.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.PHASE0);
    when(schemaProvider.getSchema(schemaRegistry)).thenReturn(newSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(false);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertThat(result).isSameAs(newSchema);

    verify(schemaCache).get(SpecMilestone.PHASE0, schemaId);
    verify(schemaCache).get(SpecMilestone.ALTAIR, schemaId);
    verify(schemaProvider).getSchema(schemaRegistry);
    verify(schemaCache).put(SpecMilestone.ALTAIR, schemaId, result);
  }

  @Test
  void shouldGetNewInstanceWhenPhase0() {
    final SchemaRegistry schemaRegistry =
        new SchemaRegistry(SpecMilestone.PHASE0, specConfig, schemaCache);

    // two different schema instances but equal
    final TestSchema newSchema = new TestSchema("test", 3);

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaCache.get(SpecMilestone.PHASE0, schemaId)).thenReturn(null);
    when(schemaProvider.getBaseMilestone(SpecMilestone.PHASE0)).thenReturn(SpecMilestone.PHASE0);
    when(schemaProvider.getSchema(schemaRegistry)).thenReturn(newSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(false);

    schemaRegistry.registerProvider(schemaProvider);
    final TestSchema result = schemaRegistry.get(schemaId);

    assertThat(result).isSameAs(newSchema);

    verify(schemaCache).get(SpecMilestone.PHASE0, schemaId);
    verify(schemaProvider).getSchema(schemaRegistry);
    verify(schemaCache).put(SpecMilestone.PHASE0, schemaId, result);
  }

  @Test
  void shouldThrowExceptionWhenGettingSchemaForUnregisteredProvider() {
    assertThrows(IllegalArgumentException.class, () -> schemaRegistry.get(schemaId));
  }

  @Test
  void shouldReleaseProviderWhenSchemaCreationFails() {
    final TestSchema createdSchema = new TestSchema("test", 2);
    final IllegalStateException creationException =
        new IllegalStateException("schema creation failed");

    when(schemaProvider.getSchemaId()).thenReturn(schemaId);
    when(schemaProvider.getSchema(schemaRegistry))
        .thenThrow(creationException)
        .thenReturn(createdSchema);
    when(schemaProvider.alwaysCreateNewSchema()).thenReturn(true);

    schemaRegistry.registerProvider(schemaProvider);

    assertThatThrownBy(() -> schemaRegistry.get(schemaId)).isSameAs(creationException);
    assertThat(schemaRegistry.get(schemaId)).isSameAs(createdSchema);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldThrowIfDependencyWhenDependencyLoop() {
    final SchemaProvider<String> provider1 = mock(SchemaProvider.class);
    final SchemaProvider<Integer> provider2 = mock(SchemaProvider.class);
    final SchemaProvider<Integer> provider3 = mock(SchemaProvider.class);
    final SchemaId<String> id1 = mock(SchemaId.class);
    final SchemaId<Integer> id2 = mock(SchemaId.class);
    final SchemaId<Integer> id3 = mock(SchemaId.class);

    when(provider1.getSchemaId()).thenReturn(id1);
    when(provider2.getSchemaId()).thenReturn(id2);
    when(provider3.getSchemaId()).thenReturn(id3);

    // create a dependency loop
    when(provider1.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id2);
              return "test";
            });

    when(provider2.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id3);
              return 42;
            });

    when(provider3.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id1);
              return 42;
            });

    schemaRegistry.registerProvider(provider1);
    schemaRegistry.registerProvider(provider2);
    schemaRegistry.registerProvider(provider3);

    assertThatThrownBy(schemaRegistry::primeRegistry)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("loop detected creating schema");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldThrowIfDependencyWhenMutualDependencyLoop() {
    final SchemaProvider<String> provider1 = mock(SchemaProvider.class);
    final SchemaProvider<Integer> provider2 = mock(SchemaProvider.class);
    final SchemaId<String> id1 = mock(SchemaId.class);
    final SchemaId<Integer> id2 = mock(SchemaId.class);

    when(provider1.getSchemaId()).thenReturn(id1);
    when(provider2.getSchemaId()).thenReturn(id2);

    // create a mutual dependency
    when(provider2.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id1);
              return 42;
            });

    when(provider1.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id2);
              return "test";
            });

    schemaRegistry.registerProvider(provider1);
    schemaRegistry.registerProvider(provider2);

    assertThatThrownBy(schemaRegistry::primeRegistry)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("loop detected creating schema");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldResolveNonLoopedDependencies() {
    final SchemaProvider<String> provider1 = mock(SchemaProvider.class);
    final SchemaProvider<Integer> provider2 = mock(SchemaProvider.class);

    final SchemaId<String> id1 = mock(SchemaId.class);
    final SchemaId<Integer> id2 = mock(SchemaId.class);

    when(provider1.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.ALTAIR);
    when(provider2.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.ALTAIR);
    when(provider1.getSchemaId()).thenReturn(id1);
    when(provider2.getSchemaId()).thenReturn(id2);

    // create a mutual dependency
    when(provider1.getSchema(schemaRegistry)).thenReturn("test");
    when(provider2.getSchema(schemaRegistry))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(0, SchemaRegistry.class).get(id1);
              return 42;
            });

    schemaRegistry.registerProvider(provider1);
    schemaRegistry.registerProvider(provider2);

    schemaRegistry.primeRegistry();

    verify(schemaCache).put(SpecMilestone.ALTAIR, id1, "test");
    verify(schemaCache).put(SpecMilestone.ALTAIR, id2, 42);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldPrimeRegistry() {
    final SchemaProvider<String> provider1 = mock(SchemaProvider.class);
    final SchemaProvider<Integer> provider2 = mock(SchemaProvider.class);
    final SchemaId<String> id1 = mock(SchemaId.class);
    final SchemaId<Integer> id2 = mock(SchemaId.class);

    when(provider1.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.ALTAIR);
    when(provider2.getBaseMilestone(SpecMilestone.ALTAIR)).thenReturn(SpecMilestone.ALTAIR);
    when(provider1.getSchemaId()).thenReturn(id1);
    when(provider2.getSchemaId()).thenReturn(id2);

    schemaRegistry.registerProvider(provider1);
    schemaRegistry.registerProvider(provider2);

    schemaRegistry.primeRegistry();

    verify(provider1).getSchema(schemaRegistry);
    verify(provider2).getSchema(schemaRegistry);
  }

  @Test
  void shouldThrowIfPrimeTwice() {
    schemaRegistry.primeRegistry();
    assertThatThrownBy(schemaRegistry::primeRegistry)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Registry already primed");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldThrowIfRegisteringTheSameSchemaIdTwice() {
    final SchemaProvider<String> provider1 = mock(SchemaProvider.class);
    schemaRegistry.registerProvider(provider1);
    assertThatThrownBy(() -> schemaRegistry.registerProvider(provider1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("has been already added via another provider");
  }

  static class TestView extends Container1<TestView, SszByteList> {
    public TestView(final TestSchema schema, final SszByteList field0) {
      super(schema, field0);
    }
  }

  static class TestSchema extends ContainerSchema1<TestView, SszByteList> {
    public TestSchema(final String containerName, final int size) {
      super(containerName, namedSchema("field0", SszByteListSchema.create(size)));
    }

    @Override
    public TestView createFromBackingNode(final TreeNode node) {
      return null;
    }
  }
}
