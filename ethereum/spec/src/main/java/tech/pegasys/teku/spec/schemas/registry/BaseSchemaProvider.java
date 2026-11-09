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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProvider<T> implements SchemaProvider<T> {
  private final TreeMap<SpecMilestone, SchemaProviderCreator<T>> milestoneToSchemaCreator =
      new TreeMap<>();
  private final SchemaId<T> schemaId;
  private final boolean alwaysCreateNewSchema;

  private BaseSchemaProvider(
      final SchemaId<T> schemaId,
      final List<SchemaProviderCreator<T>> schemaProviderCreators,
      final SpecMilestone untilMilestone,
      final boolean alwaysCreateNewSchema) {
    this.schemaId = schemaId;
    this.alwaysCreateNewSchema = alwaysCreateNewSchema;
    final List<SchemaProviderCreator<T>> creatorsList = new ArrayList<>(schemaProviderCreators);

    SchemaProviderCreator<T> lastCreator = null;

    for (final SpecMilestone milestone : SpecMilestone.getMilestonesUpTo(untilMilestone)) {
      if (!creatorsList.isEmpty() && creatorsList.getFirst().baseMilestone == milestone) {
        lastCreator = creatorsList.removeFirst();
      }

      if (lastCreator != null) {
        milestoneToSchemaCreator.put(milestone, lastCreator);
      }
    }
  }

  @Override
  public SpecMilestone getBaseMilestone(final SpecMilestone milestone) {
    return getSchemaCreator(milestone).baseMilestone;
  }

  @Override
  public boolean alwaysCreateNewSchema() {
    return alwaysCreateNewSchema;
  }

  @Override
  public T getSchema(final SchemaRegistry registry) {
    final SpecMilestone milestone = registry.getMilestone();
    return createSchema(registry, milestone, registry.getSpecConfig());
  }

  @Override
  public SchemaId<T> getSchemaId() {
    return schemaId;
  }

  protected T createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return getSchemaCreator(effectiveMilestone)
        .creator
        .create(registry, specConfig, schemaId.getSchemaName(registry.getMilestone()));
  }

  private SchemaProviderCreator<T> getSchemaCreator(final SpecMilestone milestone) {
    final SchemaProviderCreator<T> maybeSchemaCreator = milestoneToSchemaCreator.get(milestone);
    if (maybeSchemaCreator == null) {
      throw new IllegalArgumentException(
          "It is not supposed to create a specific version for " + milestone);
    }
    return maybeSchemaCreator;
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return milestoneToSchemaCreator.keySet();
  }

  protected record SchemaProviderCreator<T>(SpecMilestone baseMilestone, SchemaCreator<T> creator) {

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("baseMilestone", baseMilestone).toString();
    }
  }

  /**
   * Creates a builder for a schema provider.<br>
   * Example usage:
   *
   * <pre>{@code
   * providerBuilder(EXAMPLE_SCHEMA)
   *    .withCreator(ALTAIR, (registry, config) -> ExampleSchema1.create(registry, config))
   *    .withCreator(ELECTRA, (registry, config) -> ExampleSchema2.create(registry, config))
   *    .build();
   *
   * }</pre>
   *
   * this will create a schema provider that will generate: <br>
   * - a new ExampleSchema1 for each milestone from ALTAIR to CAPELLA <br>
   * - a new ExampleSchema2 for each milestone from ELECTRA to last known milestone <br>
   *
   * <p>By default, the schema provider will check for schema equality when a creator is used
   * multiple times. In the previous example, if ExampleSchema1.create generates schemas that are
   * <b>equals</b> in both ALTAIR and BELLATRIX context, the ALTAIR instance will be used for
   * BELLATRIX too.<br>
   * Since the equality check does not consider names, semantically equivalent schemas with
   * different fields or container names will be considered equal.<br>
   *
   * <p>If the equality check is relevant, this behavior can be avoided in two ways:<br>
   * - specifying a new creator like: <br>
   *
   * <pre>{@code
   * variableProviderBuilder(EXAMPLE_SCHEMA)
   *    .withCreator(ALTAIR, (registry, config) -> ExampleSchema1.create(registry, config))
   *    .withCreator(BELLATRIX, (registry, config) -> ExampleSchema1.create(registry, config))
   *    .withCreator(ELECTRA, (registry, config) -> ExampleSchema2.create(registry, config))
   *    .build();
   *
   * }</pre>
   *
   * - using {@link Builder#alwaysCreateNewSchema()}
   */
  static <T> Builder<T> providerBuilder(final SchemaId<T> schemaId) {
    return new Builder<>(schemaId);
  }

  static class Builder<T> {
    private final SchemaId<T> schemaId;
    final List<SchemaProviderCreator<T>> schemaProviderCreators = new ArrayList<>();
    private SpecMilestone untilMilestone = SpecMilestone.getHighestMilestone();
    private boolean alwaysCreateNewSchema = false;

    private Builder(final SchemaId<T> schemaId) {
      this.schemaId = schemaId;
    }

    public Builder<T> withCreator(
        final SpecMilestone milestone, final SchemaCreator<T> creationSchema) {
      checkArgument(
          schemaProviderCreators.isEmpty()
              || milestone.isGreaterThan(schemaProviderCreators.getLast().baseMilestone),
          "Creator's milestones must added in strict ascending order for %s",
          schemaId);

      schemaProviderCreators.add(new SchemaProviderCreator<>(milestone, creationSchema));
      return this;
    }

    /**
     * This can be used when a schema is deprecated and should not be used for newer milestones.
     *
     * @param untilMilestone the last milestone for which the schema will be created
     */
    public Builder<T> until(final SpecMilestone untilMilestone) {
      this.untilMilestone = untilMilestone;
      return this;
    }

    /**
     * Forces schema provider to create a new schema on each milestone, disabling schema equality
     * check with previous milestone. Refer to {@link BaseSchemaProvider} for more information.
     */
    public Builder<T> alwaysCreateNewSchema() {
      this.alwaysCreateNewSchema = true;
      return this;
    }

    public BaseSchemaProvider<T> build() {
      checkArgument(
          !schemaProviderCreators.isEmpty(), "There should be at least 1 creator for %s", schemaId);

      checkArgument(
          untilMilestone.isGreaterThanOrEqualTo(schemaProviderCreators.getLast().baseMilestone),
          "until must be greater or equal than last creator milestone in %s",
          schemaId);
      return new BaseSchemaProvider<>(
          schemaId, schemaProviderCreators, untilMilestone, alwaysCreateNewSchema);
    }
  }

  @FunctionalInterface
  public interface SchemaCreator<T> {
    T create(SchemaRegistry registry, SpecConfig specConfig, String schemaName);
  }
}
