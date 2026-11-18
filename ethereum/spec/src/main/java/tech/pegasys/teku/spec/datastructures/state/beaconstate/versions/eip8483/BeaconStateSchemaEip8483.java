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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BUILDER_PQ_REGISTRATIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.VALIDATOR_PQ_REGISTRATIONS_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
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
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.BuilderPqRegistration;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

/**
 * The EIP-8483 state is the Gloas state with two additional progressive lists. It extends the Gloas
 * schema so that all Gloas logic keeps working against it.
 */
public class BeaconStateSchemaEip8483 extends BeaconStateSchemaGloas {

  public static final int VALIDATOR_PQ_REGISTRATIONS_FIELD_INDEX = PTC_WINDOW_INDEX + 1;
  public static final int BUILDER_PQ_REGISTRATIONS_FIELD_INDEX = PTC_WINDOW_INDEX + 2;

  private static final boolean[] ACTIVE_FIELDS =
      ProgressiveSchemaUtils.allActive(BUILDER_PQ_REGISTRATIONS_FIELD_INDEX + 1);

  @VisibleForTesting
  BeaconStateSchemaEip8483(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super(
        "BeaconStateEip8483",
        ACTIVE_FIELDS,
        getUniqueFields(specConfig, schemaRegistry),
        specConfig);
  }

  public static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                VALIDATOR_PQ_REGISTRATIONS_FIELD_INDEX,
                BeaconStateFields.VALIDATOR_PQ_REGISTRATIONS,
                () -> schemaRegistry.get(VALIDATOR_PQ_REGISTRATIONS_SCHEMA)),
            new SszField(
                BUILDER_PQ_REGISTRATIONS_FIELD_INDEX,
                BeaconStateFields.BUILDER_PQ_REGISTRATIONS,
                () -> schemaRegistry.get(BUILDER_PQ_REGISTRATIONS_SCHEMA)));
    return Stream.concat(
            BeaconStateSchemaGloas.getUniqueFields(specConfig, schemaRegistry).stream(),
            newFields.stream())
        .toList();
  }

  public static BeaconStateSchemaEip8483 create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaEip8483(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaEip8483 required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaEip8483,
        "Expected a BeaconStateSchemaEip8483 but was %s",
        schema.getClass());
    return (BeaconStateSchemaEip8483) schema;
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<ValidatorPqRegistration, ?> getValidatorPqRegistrationsSchema() {
    return (SszListSchema<ValidatorPqRegistration, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.VALIDATOR_PQ_REGISTRATIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<BuilderPqRegistration, ?> getBuilderPqRegistrationsSchema() {
    return (SszListSchema<BuilderPqRegistration, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.BUILDER_PQ_REGISTRATIONS));
  }

  @Override
  public MutableBeaconStateGloas createBuilder() {
    return new MutableBeaconStateEip8483Impl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateGloas createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  @Override
  public BeaconStateGloas createEmptyWithTransitionCachesFrom(final BeaconState sourceState) {
    return new BeaconStateEip8483Impl(
        this,
        BeaconStateCache.getTransitionCaches(sourceState).copy(),
        SlotCaches.createNewEmpty());
  }

  private BeaconStateEip8483Impl createEmptyBeaconStateImpl() {
    return new BeaconStateEip8483Impl(this);
  }

  @Override
  public BeaconStateGloas createFromBackingNode(final TreeNode node) {
    return new BeaconStateEip8483Impl(this, node);
  }
}
