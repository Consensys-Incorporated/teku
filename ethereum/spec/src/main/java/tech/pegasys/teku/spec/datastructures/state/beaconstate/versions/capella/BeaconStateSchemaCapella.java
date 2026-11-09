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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_SUMMARIES_SCHEMA;

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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaCapella
    extends AbstractBeaconStateSchema<BeaconStateCapella, MutableBeaconStateCapella> {
  public static final int NEXT_WITHDRAWAL_INDEX_FIELD_INDEX = 25;
  public static final int NEXT_WITHDRAWAL_VALIDATOR_INDEX_FIELD_INDEX = 26;
  public static final int HISTORICAL_SUMMARIES_FIELD_INDEX = 27;

  @VisibleForTesting
  BeaconStateSchemaCapella(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateCapella", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  public static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                NEXT_WITHDRAWAL_INDEX_FIELD_INDEX,
                BeaconStateFields.NEXT_WITHDRAWAL_INDEX,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                NEXT_WITHDRAWAL_VALIDATOR_INDEX_FIELD_INDEX,
                BeaconStateFields.NEXT_WITHDRAWAL_VALIDATOR_INDEX,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                HISTORICAL_SUMMARIES_FIELD_INDEX,
                BeaconStateFields.HISTORICAL_SUMMARIES,
                () -> schemaRegistry.get(HISTORICAL_SUMMARIES_SCHEMA)));

    return Stream.concat(
            BeaconStateSchemaBellatrix.getUniqueFields(specConfig, schemaRegistry).stream(),
            newFields.stream())
        .toList();
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

  @SuppressWarnings("unchecked")
  public SszListSchema<HistoricalSummary, ?> getHistoricalSummariesSchema() {
    return (SszListSchema<HistoricalSummary, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.HISTORICAL_SUMMARIES));
  }

  @Override
  public MutableBeaconStateCapella createBuilder() {
    return new MutableBeaconStateCapellaImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaCapella create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaCapella(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaCapella required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaCapella,
        "Expected a BeaconStateSchemaCapella but was %s",
        schema.getClass());
    return (BeaconStateSchemaCapella) schema;
  }

  @Override
  public BeaconStateCapella createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateCapellaImpl createEmptyBeaconStateImpl() {
    return new BeaconStateCapellaImpl(this);
  }

  @Override
  public BeaconStateCapella createFromBackingNode(final TreeNode node) {
    return new BeaconStateCapellaImpl(this, node);
  }
}
