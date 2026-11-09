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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_CONSOLIDATIONS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_DEPOSITS_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PENDING_PARTIAL_WITHDRAWALS_SCHEMA;

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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaElectra
    extends AbstractBeaconStateSchema<BeaconStateElectra, MutableBeaconStateElectra> {
  public static final int DEPOSIT_REQUESTS_START_INDEX_FIELD_INDEX = 28;
  public static final int DEPOSIT_BALANCE_TO_CONSUME_FIELD_INDEX = 29;
  public static final int EXIT_BALANCE_TO_CONSUME_FIELD_INDEX = 30;
  public static final int EARLIEST_EXIT_EPOCH_FIELD_INDEX = 31;
  public static final int CONSOLIDATION_BALANCE_TO_CONSUME_FIELD_INDEX = 32;
  public static final int EARLIEST_CONSOLIDATION_EPOCH_FIELD_INDEX = 33;
  public static final int PENDING_DEPOSITS_FIELD_INDEX = 34;
  public static final int PENDING_PARTIAL_WITHDRAWALS_FIELD_INDEX = 35;
  public static final int PENDING_CONSOLIDATIONS_FIELD_INDEX = 36;

  @VisibleForTesting
  BeaconStateSchemaElectra(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateElectra", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  public static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                DEPOSIT_REQUESTS_START_INDEX_FIELD_INDEX,
                BeaconStateFields.DEPOSIT_REQUESTS_START_INDEX,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                DEPOSIT_BALANCE_TO_CONSUME_FIELD_INDEX,
                BeaconStateFields.DEPOSIT_BALANCE_TO_CONSUME,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                EXIT_BALANCE_TO_CONSUME_FIELD_INDEX,
                BeaconStateFields.EXIT_BALANCE_TO_CONSUME,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                EARLIEST_EXIT_EPOCH_FIELD_INDEX,
                BeaconStateFields.EARLIEST_EXIT_EPOCH,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                CONSOLIDATION_BALANCE_TO_CONSUME_FIELD_INDEX,
                BeaconStateFields.CONSOLIDATION_BALANCE_TO_CONSUME,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                EARLIEST_CONSOLIDATION_EPOCH_FIELD_INDEX,
                BeaconStateFields.EARLIEST_CONSOLIDATION_EPOCH,
                () -> SszPrimitiveSchemas.UINT64_SCHEMA),
            new SszField(
                PENDING_DEPOSITS_FIELD_INDEX,
                BeaconStateFields.PENDING_DEPOSITS,
                () -> schemaRegistry.get(PENDING_DEPOSITS_SCHEMA)),
            new SszField(
                PENDING_PARTIAL_WITHDRAWALS_FIELD_INDEX,
                BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS,
                () -> schemaRegistry.get(PENDING_PARTIAL_WITHDRAWALS_SCHEMA)),
            new SszField(
                PENDING_CONSOLIDATIONS_FIELD_INDEX,
                BeaconStateFields.PENDING_CONSOLIDATIONS,
                () -> schemaRegistry.get(PENDING_CONSOLIDATIONS_SCHEMA)));

    return Stream.concat(
            BeaconStateSchemaDeneb.getUniqueFields(specConfig, schemaRegistry).stream(),
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
  public SszListSchema<PendingDeposit, ?> getPendingDepositsSchema() {
    return (SszListSchema<PendingDeposit, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_DEPOSITS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingPartialWithdrawal, ?> getPendingPartialWithdrawalsSchema() {
    return (SszListSchema<PendingPartialWithdrawal, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_PARTIAL_WITHDRAWALS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingConsolidation, ?> getPendingConsolidationsSchema() {
    return (SszListSchema<PendingConsolidation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PENDING_CONSOLIDATIONS));
  }

  @Override
  public MutableBeaconStateElectra createBuilder() {
    return new MutableBeaconStateElectraImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaElectra create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaElectra(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaElectra required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaElectra,
        "Expected a BeaconStateSchemaElectra but was %s",
        schema.getClass());
    return (BeaconStateSchemaElectra) schema;
  }

  @Override
  public BeaconStateElectra createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateElectraImpl createEmptyBeaconStateImpl() {
    return new BeaconStateElectraImpl(this);
  }

  @Override
  public BeaconStateElectraImpl createFromBackingNode(final TreeNode node) {
    return new BeaconStateElectraImpl(this, node);
  }
}
