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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation.PendingAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public class BeaconStateSchemaPhase0
    extends AbstractBeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> {
  public static final int PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX = 15;
  public static final int CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX = 16;

  @VisibleForTesting
  BeaconStateSchemaPhase0(final SpecConfig specConfig) {
    super("BeaconStatePhase0", getUniqueFields(specConfig), specConfig);
  }

  public static BeaconStateSchemaPhase0 create(final SpecConfig specConfig) {
    return new BeaconStateSchemaPhase0(specConfig);
  }

  public static BeaconStateSchemaPhase0 required(final SszSchema<? extends BeaconState> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaPhase0,
        "Expected a BeaconStateSchemaPhase0 but was %s",
        schema.getClass());
    return (BeaconStateSchemaPhase0) schema;
  }

  public static List<SszField> getUniqueFields(final SpecConfig specConfig) {
    final PendingAttestationSchema pendingAttestationSchema =
        new PendingAttestationSchema(specConfig);

    final SszField previousEpochAttestationsField =
        new SszField(
            PREVIOUS_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS,
            () ->
                SszListSchema.create(
                    pendingAttestationSchema,
                    (long) specConfig.getMaxAttestations() * specConfig.getSlotsPerEpoch()));
    final SszField currentEpochAttestationsField =
        new SszField(
            CURRENT_EPOCH_PARTICIPATION_FIELD_INDEX,
            BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS,
            () ->
                SszListSchema.create(
                    pendingAttestationSchema,
                    (long) specConfig.getMaxAttestations() * specConfig.getSlotsPerEpoch()));

    return List.of(previousEpochAttestationsField, currentEpochAttestationsField);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingAttestation, ?> getPreviousEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<PendingAttestation, ?> getCurrentEpochAttestationsSchema() {
    return (SszListSchema<PendingAttestation, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS));
  }

  public PendingAttestationSchema getPendingAttestationSchema() {
    return (PendingAttestationSchema) getCurrentEpochAttestationsSchema().getElementSchema();
  }

  @Override
  public BeaconStatePhase0 createFromBackingNode(final TreeNode node) {
    return new BeaconStatePhase0Impl(this, node);
  }

  @Override
  public MutableBeaconStatePhase0 createBuilder() {
    return new MutableBeaconStatePhase0Impl(createEmptyBeaconStateImpl(), true);
  }

  @Override
  public BeaconStatePhase0 createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStatePhase0Impl createEmptyBeaconStateImpl() {
    return new BeaconStatePhase0Impl(this);
  }
}
