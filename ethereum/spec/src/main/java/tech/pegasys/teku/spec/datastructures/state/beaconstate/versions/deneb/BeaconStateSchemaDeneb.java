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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class BeaconStateSchemaDeneb
    extends AbstractBeaconStateSchema<BeaconStateDeneb, MutableBeaconStateDeneb> {

  @VisibleForTesting
  BeaconStateSchemaDeneb(final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    super("BeaconStateDeneb", getUniqueFields(specConfig, schemaRegistry), specConfig);
  }

  public static List<SszField> getUniqueFields(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return BeaconStateSchemaCapella.getUniqueFields(specConfig, schemaRegistry).stream().toList();
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

  @Override
  public MutableBeaconStateDeneb createBuilder() {
    return new MutableBeaconStateDenebImpl(createEmptyBeaconStateImpl(), true);
  }

  public static BeaconStateSchemaDeneb create(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry) {
    return new BeaconStateSchemaDeneb(specConfig, schemaRegistry);
  }

  public static BeaconStateSchemaDeneb required(final BeaconStateSchema<?, ?> schema) {
    checkArgument(
        schema instanceof BeaconStateSchemaDeneb,
        "Expected a BeaconStateSchemaDeneb but was %s",
        schema.getClass());
    return (BeaconStateSchemaDeneb) schema;
  }

  @Override
  public BeaconStateDeneb createEmpty() {
    return createEmptyBeaconStateImpl();
  }

  private BeaconStateDenebImpl createEmptyBeaconStateImpl() {
    return new BeaconStateDenebImpl(this);
  }

  @Override
  public BeaconStateDeneb createFromBackingNode(final TreeNode node) {
    return new BeaconStateDenebImpl(this, node);
  }
}
