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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.PROPOSER_LOOKAHEAD_SCHEMA;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.stream.Stream;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaFulu
    extends AbstractBeaconStateSchema<BeaconStateFulu, MutableBeaconStateFulu> {
  public static final int PROPOSER_LOOKAHEAD_FIELD_INDEX = 37;

  @VisibleForTesting
  BeaconStateSchemaFulu(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateFulu", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  public static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    final List<SszField> newFields =
        List.of(
            new SszField(
                PROPOSER_LOOKAHEAD_FIELD_INDEX,
                BeaconStateFields.PROPOSER_LOOKAHEAD,
                () -> schemaRegistry.get(PROPOSER_LOOKAHEAD_SCHEMA)));

    return Stream.concat(
            BeaconStateSchemaElectra.getUniqueFields(specConfig, schemaRegistry).stream(),
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

  public SszUInt64VectorSchema<?> getProposerLookaheadSchema() {
    return (SszUInt64VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PROPOSER_LOOKAHEAD));
  }

  @Override
  public MutableBeaconStateFulu createBuilder() {
    return new MutableBeaconStateFuluImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaFulu create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaFulu(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaFulu required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaFulu,
        "Expected a BeaconStateSchemaFulu but was %s",
        schema.getClass());
    return (BeaconStateSchemaFulu) schema;
  }

  @Override
  public BeaconStateFulu createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateFuluImpl createEmptyBeaconStateImpl() {
    return new BeaconStateFuluImpl(this);
  }

  @Override
  public BeaconStateFuluImpl createFromBackingNode(final TreeNode node) {
    return new BeaconStateFuluImpl(this, node);
  }
}
