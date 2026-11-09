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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0.CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0.PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;

public class BeaconStateSchemaAltair
    extends AbstractBeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> {

  public static final int INACTIVITY_SCORES_FIELD_INDEX = 21;
  public static final int CURRENT_SYNC_COMMITTEE_FIELD_INDEX = 22;
  public static final int NEXT_SYNC_COMMITTEE_FIELD_INDEX = 23;

  @VisibleForTesting
  BeaconStateSchemaAltair(final SpecConfig specConfig) {
    super("BeaconStateAltair", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaAltair create(final SpecConfig specConfig) {
    return new BeaconStateSchemaAltair(specConfig);
  }

  public static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final List<SszField> updatedFields =
        List.of(
            new SszField(
                PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX,
                BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION,
                () ->
                    SszListSchema.create(
                        SszPrimitiveSchemas.UINT8_SCHEMA, specConfig.getValidatorRegistryLimit())),
            new SszField(
                CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX,
                BeaconStateFields.CURRENT_EPOCH_PARTICIPATION,
                () ->
                    SszListSchema.create(
                        SszPrimitiveSchemas.UINT8_SCHEMA, specConfig.getValidatorRegistryLimit())));

    final List<SszField> newFields =
        List.of(
            new SszField(
                INACTIVITY_SCORES_FIELD_INDEX,
                BeaconStateFields.INACTIVITY_SCORES,
                SszUInt64ListSchema.create(specConfig.getValidatorRegistryLimit())),
            new SszField(
                CURRENT_SYNC_COMMITTEE_FIELD_INDEX,
                BeaconStateFields.CURRENT_SYNC_COMMITTEE,
                () -> new SyncCommitteeSchema(SpecConfigAltair.required(specConfig))),
            new SszField(
                NEXT_SYNC_COMMITTEE_FIELD_INDEX,
                BeaconStateFields.NEXT_SYNC_COMMITTEE,
                () -> new SyncCommitteeSchema(SpecConfigAltair.required(specConfig))));

    return Stream.concat(
            BeaconStateSchemaPhase0.getUniqueFields(specConfig).stream(), newFields.stream())
        .map(
            field ->
                updatedFields.stream()
                    .filter(updatedField -> updatedField.getIndex() == field.getIndex())
                    .findFirst()
                    .orElse(field))
        .toList();
  }

  public static BeaconStateSchemaAltair required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaAltair,
        "Expected a BeaconStateSchemaAltair but was %s",
        schema.getClass());
    return (BeaconStateSchemaAltair) schema;
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getPreviousEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_PARTICIPATION));
  }

  @SuppressWarnings("unchecked")
  public SszPrimitiveListSchema<Byte, SszByte, ?> getCurrentEpochParticipationSchema() {
    return (SszPrimitiveListSchema<Byte, SszByte, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_PARTICIPATION));
  }

  public SszUInt64ListSchema<?> getInactivityScoresSchema() {
    return (SszUInt64ListSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.INACTIVITY_SCORES));
  }

  public SyncCommitteeSchema getCurrentSyncCommitteeSchema() {
    return (SyncCommitteeSchema)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE));
  }

  @Override
  public BeaconStateAltair createFromBackingNode(final TreeNode node) {
    return new BeaconStateAltairImpl(this, node);
  }

  @Override
  public MutableBeaconStateAltair createBuilder() {
    return new MutableBeaconStateAltairImpl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStateAltair createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateAltairImpl createEmptyBeaconStateImpl() {
    return new BeaconStateAltairImpl(this);
  }
}
