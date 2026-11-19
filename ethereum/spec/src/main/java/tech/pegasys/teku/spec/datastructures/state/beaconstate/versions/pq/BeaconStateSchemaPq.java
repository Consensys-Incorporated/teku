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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * The PQ state: the Gloas state without sync committees (gaps at 22 and 23) and without EIP-8483's
 * PQ registrations (gaps at 46 and 47), with XMSS-keyed validators and builders, and with
 * preregistrations and the aggregation-committee window at 48 to 50.
 */
public class BeaconStateSchemaPq extends BeaconStateSchemaGloas {

  public static final int CURRENT_SYNC_COMMITTEE_FIELD_INDEX = 22;
  public static final int NEXT_SYNC_COMMITTEE_FIELD_INDEX = 23;
  public static final int VALIDATOR_PQ_REGISTRATIONS_FIELD_INDEX = PTC_WINDOW_INDEX + 1;
  public static final int BUILDER_PQ_REGISTRATIONS_FIELD_INDEX = PTC_WINDOW_INDEX + 2;
  public static final int VALIDATOR_PREREGISTRATIONS_FIELD_INDEX = 48;
  public static final int BUILDER_PREREGISTRATIONS_FIELD_INDEX = 49;
  public static final int AGGREGATION_COMMITTEE_WINDOW_FIELD_INDEX = 50;

  private static final int VALIDATORS_FIELD_INDEX = 11;

  private static final Set<Integer> REMOVED_FIELD_INDICES =
      Set.of(
          CURRENT_SYNC_COMMITTEE_FIELD_INDEX,
          NEXT_SYNC_COMMITTEE_FIELD_INDEX,
          VALIDATOR_PQ_REGISTRATIONS_FIELD_INDEX,
          BUILDER_PQ_REGISTRATIONS_FIELD_INDEX);

  private static final boolean[] ACTIVE_FIELDS = activeFields();

  @VisibleForTesting
  BeaconStateSchemaPq(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStatePq", ACTIVE_FIELDS, getAllFields(specConfig, schemaRegistry));
  }

  private static boolean[] activeFields() {
    final boolean[] activeFields = new boolean[AGGREGATION_COMMITTEE_WINDOW_FIELD_INDEX + 1];
    for (int i = 0; i < activeFields.length; i++) {
      activeFields[i] = !REMOVED_FIELD_INDICES.contains(i);
    }
    return activeFields;
  }

  public static List<SszField> getAllFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                VALIDATOR_PREREGISTRATIONS_FIELD_INDEX,
                BeaconStateFields.VALIDATOR_PREREGISTRATIONS,
                () -> SszProgressiveListSchema.create(ValidatorPreregistration.SSZ_SCHEMA)),
            new SszField(
                BUILDER_PREREGISTRATIONS_FIELD_INDEX,
                BeaconStateFields.BUILDER_PREREGISTRATIONS,
                () -> SszProgressiveListSchema.create(BuilderPreregistration.SSZ_SCHEMA)),
            new SszField(
                AGGREGATION_COMMITTEE_WINDOW_FIELD_INDEX,
                BeaconStateFields.AGGREGATION_COMMITTEE_WINDOW,
                () -> createAggregationCommitteeWindowSchema(specConfig)));
    return Stream.of(
            BeaconStateFields.getCommonFields(specConfig).stream(),
            BeaconStateSchemaGloas.getUniqueFields(specConfig, schemaRegistry).stream(),
            newFields.stream())
        .flatMap(fields -> fields)
        .filter(field -> !REMOVED_FIELD_INDICES.contains(field.getIndex()))
        .map(field -> replaceKeyedRegistryField(field, specConfig))
        .sorted(Comparator.comparingInt(SszField::getIndex))
        .toList();
  }

  // Validators and builders are keyed by XMSS public keys in PQ
  private static SszField replaceKeyedRegistryField(
      final SszField field, final SpecConfig specConfig) {
    if (field.getIndex() == VALIDATORS_FIELD_INDEX) {
      return new SszField(
          VALIDATORS_FIELD_INDEX,
          BeaconStateFields.VALIDATORS,
          () ->
              BeaconStateFields.listSchemaForState(
                  specConfig,
                  SszListSchema.create(
                      ValidatorPq.SSZ_SCHEMA, specConfig.getValidatorRegistryLimit())));
    }
    if (field.getIndex() == BUILDERS_FIELD_INDEX) {
      return new SszField(
          BUILDERS_FIELD_INDEX,
          BeaconStateFields.BUILDERS,
          () -> SszProgressiveListSchema.create(BuilderPq.SSZ_SCHEMA));
    }
    return field;
  }

  /**
   * The aggregation committees of the previous, current and lookahead epochs: per slot, the
   * aggregators of each beacon committee.
   */
  private static SszVectorSchema<SszList<SszList<SszUInt64>>, ?>
      createAggregationCommitteeWindowSchema(final SpecConfig specConfig) {
    final SszListSchema<SszUInt64, ? extends SszList<SszUInt64>> aggregationCommitteeSchema =
        SszPrimitiveListSchema.createProgressive(SszPrimitiveSchemas.UINT64_SCHEMA);
    final SszProgressiveListSchema<SszList<SszUInt64>> aggregationCommitteesSchema =
        SszProgressiveListSchema.create(asListSchema(aggregationCommitteeSchema));
    return SszVectorSchema.create(
        aggregationCommitteesSchema,
        (long) (specConfig.getMinSeedLookahead() + 2) * specConfig.getSlotsPerEpoch());
  }

  @SuppressWarnings("unchecked")
  private static SszListSchema<SszUInt64, SszList<SszUInt64>> asListSchema(
      final SszListSchema<SszUInt64, ? extends SszList<SszUInt64>> schema) {
    return (SszListSchema<SszUInt64, SszList<SszUInt64>>) schema;
  }

  public static BeaconStateSchemaPq create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaPq(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaPq required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaPq,
        "Expected a BeaconStateSchemaPq but was %s",
        schema.getClass());
    return (BeaconStateSchemaPq) schema;
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<ValidatorPreregistration, ?> getValidatorPreregistrationsSchema() {
    return (SszListSchema<ValidatorPreregistration, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.VALIDATOR_PREREGISTRATIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderPreregistration, ?> getBuilderPreregistrationsSchema() {
    return (SszListSchema<BuilderPreregistration, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BUILDER_PREREGISTRATIONS));
  }

  @SuppressWarnings("unchecked")
  public SszVectorSchema<SszList<SszList<SszUInt64>>, ?> getAggregationCommitteeWindowSchema() {
    return (SszVectorSchema<SszList<SszList<SszUInt64>>, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.AGGREGATION_COMMITTEE_WINDOW));
  }

  @Override
  public MutableBeaconStateGloas createBuilder() {
    return new MutableBeaconStatePqImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateGloas createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  @Override
  public BeaconStateGloas createEmptyWithTransitionCachesFrom(final BeaconState sourceState) {
    return new BeaconStatePqImpl(
        this,
        BeaconStateCache.getTransitionCaches(sourceState).copy(),
        SlotCaches.createNewEmpty());
  }

  private BeaconStatePqImpl createEmptyBeaconStateImpl() {
    return new BeaconStatePqImpl(this);
  }

  @Override
  public BeaconStateGloas createFromBackingNode(final TreeNode node) {
    return new BeaconStatePqImpl(this, node);
  }
}
