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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.analysis.ValidatorStats;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;

public interface BeaconState extends SszContainer, ValidatorStats {

  BeaconStateSchema<? extends BeaconState, ? extends MutableBeaconState> getBeaconStateSchema();

  default UInt64 getGenesisTime() {
    final int fieldIndex = BeaconStateInvariants.GENESIS_TIME_FIELD.getIndex();
    return ((SszUInt64) get(fieldIndex)).get();
  }

  default Bytes32 getGenesisValidatorsRoot() {
    final int fieldIndex = BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD.getIndex();
    return ((SszBytes32) get(fieldIndex)).get();
  }

  default UInt64 getSlot() {
    final int fieldIndex = BeaconStateInvariants.SLOT_FIELD.getIndex();
    return ((SszUInt64) get(fieldIndex)).get();
  }

  default Fork getFork() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FORK);
    return getAny(fieldIndex);
  }

  default ForkInfo getForkInfo() {
    return new ForkInfo(getFork(), getGenesisValidatorsRoot());
  }

  // History
  default BeaconBlockHeader getLatestBlockHeader() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_BLOCK_HEADER);
    return getAny(fieldIndex);
  }

  default SszBytes32Vector getBlockRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS);
    return getAny(fieldIndex);
  }

  default SszBytes32Vector getStateRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS);
    return getAny(fieldIndex);
  }

  default SszPrimitiveList<Bytes32, SszBytes32> getHistoricalRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS);
    return getAny(fieldIndex);
  }

  // Eth1
  default Eth1Data getEth1Data() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA);
    return getAny(fieldIndex);
  }

  default SszList<Eth1Data> getEth1DataVotes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES);
    return getAny(fieldIndex);
  }

  default UInt64 getEth1DepositIndex() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX);
    return ((SszUInt64) get(fieldIndex)).get();
  }

  // Registry
  default SszList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS);
    return getAny(fieldIndex);
  }

  default SszUInt64List getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES);
    return getAny(fieldIndex);
  }

  default SszBytes32Vector getRandaoMixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES);
    return getAny(fieldIndex);
  }

  // Slashings
  default SszPrimitiveVector<UInt64, SszUInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS);
    return getAny(fieldIndex);
  }

  // Finality
  default SszBitvector getJustificationBits() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS);
    return getAny(fieldIndex);
  }

  default Checkpoint getPreviousJustifiedCheckpoint() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT);
    return getAny(fieldIndex);
  }

  default Checkpoint getCurrentJustifiedCheckpoint() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT);
    return getAny(fieldIndex);
  }

  default Checkpoint getFinalizedCheckpoint() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FINALIZED_CHECKPOINT);
    return getAny(fieldIndex);
  }

  @Override
  default SszMutableContainer createWritableCopy() {
    throw new UnsupportedOperationException("Use BeaconState.updated() to modify");
  }

  <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) throws E1, E2, E3;

  interface Mutator<
      TState extends MutableBeaconState,
      E1 extends Exception,
      E2 extends Exception,
      E3 extends Exception> {

    void mutate(TState state) throws E1, E2, E3;
  }

  default Optional<BeaconStatePhase0> toVersionPhase0() {
    return Optional.empty();
  }

  default Optional<BeaconStateAltair> toVersionAltair() {
    return Optional.empty();
  }

  default Optional<BeaconStateBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<BeaconStateCapella> toVersionCapella() {
    return Optional.empty();
  }

  default Optional<BeaconStateDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  default Optional<BeaconStateElectra> toVersionElectra() {
    return Optional.empty();
  }

  default Optional<BeaconStateFulu> toVersionFulu() {
    return Optional.empty();
  }

  default Optional<BeaconStateGloas> toVersionGloas() {
    return Optional.empty();
  }
}
