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

package tech.pegasys.teku.spec.datastructures.state.beaconstate;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszPrimitiveVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee.SyncCommitteeSchema;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;

public interface BeaconStateSchema<T extends BeaconState, TMutable extends MutableBeaconState>
    extends SszContainerSchema<T> {
  TMutable createBuilder();

  T createEmpty();

  default SszBytes32VectorSchema<?> getBlockRootsSchema() {
    return (SszBytes32VectorSchema<?>) getChildSchema(getFieldIndex(BeaconStateFields.BLOCK_ROOTS));
  }

  default SszBytes32VectorSchema<?> getStateRootsSchema() {
    return (SszBytes32VectorSchema<?>) getChildSchema(getFieldIndex(BeaconStateFields.STATE_ROOTS));
  }

  @SuppressWarnings("unchecked")
  default SszPrimitiveListSchema<Bytes32, SszBytes32, ?> getHistoricalRootsSchema() {
    return (SszPrimitiveListSchema<Bytes32, SszBytes32, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<Eth1Data, ?> getEth1DataVotesSchema() {
    return (SszListSchema<Eth1Data, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES));
  }

  @SuppressWarnings("unchecked")
  default SszListSchema<Validator, ?> getValidatorsSchema() {
    return (SszListSchema<Validator, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.VALIDATORS));
  }

  default SszUInt64ListSchema<?> getBalancesSchema() {
    return (SszUInt64ListSchema<?>) getChildSchema(getFieldIndex(BeaconStateFields.BALANCES));
  }

  default SszBytes32VectorSchema<?> getRandaoMixesSchema() {
    return (SszBytes32VectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.RANDAO_MIXES));
  }

  @SuppressWarnings("unchecked")
  default SszPrimitiveVectorSchema<UInt64, SszUInt64, ?> getSlashingsSchema() {
    return (SszPrimitiveVectorSchema<UInt64, SszUInt64, ?>)
        getChildSchema(getFieldIndex(BeaconStateFields.SLASHINGS));
  }

  default SszBitvectorSchema<?> getJustificationBitsSchema() {
    return (SszBitvectorSchema<?>)
        getChildSchema(getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS));
  }

  default SyncCommitteeSchema getCurrentSyncCommitteeSchemaOrThrow() {
    return (SyncCommitteeSchema) getSchemaOrThrow(BeaconStateFields.CURRENT_SYNC_COMMITTEE);
  }

  default SyncCommitteeSchema getNextSyncCommitteeSchemaOrThrow() {
    return (SyncCommitteeSchema) getSchemaOrThrow(BeaconStateFields.NEXT_SYNC_COMMITTEE);
  }

  private SszSchema<?> getSchemaOrThrow(final SszFieldName field) {
    final int fieldIndex = getFieldIndex(field);
    checkArgument(
        fieldIndex >= 0, "Expected a %s field in schema %s but was not found", field, getClass());
    return getChildSchema(fieldIndex);
  }
}
