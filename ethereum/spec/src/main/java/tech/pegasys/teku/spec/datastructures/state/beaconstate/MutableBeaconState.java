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
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefContainer;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64List;
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
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.MutableBeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;

public interface MutableBeaconState extends BeaconState, SszMutableRefContainer {

  // Versioning

  default void setGenesisTime(final UInt64 genesisTime) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_TIME);
    set(fieldIndex, SszUInt64.of(genesisTime));
  }

  default void setGenesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.GENESIS_VALIDATORS_ROOT);
    set(fieldIndex, SszBytes32.of(genesisValidatorsRoot));
  }

  default void setSlot(final UInt64 slot) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLOT);
    set(fieldIndex, SszUInt64.of(slot));
  }

  default void setFork(final Fork fork) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FORK);
    set(fieldIndex, fork);
  }

  // History
  default void setLatestBlockHeader(final BeaconBlockHeader latestBlockHeader) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.LATEST_BLOCK_HEADER);
    set(fieldIndex, latestBlockHeader);
  }

  @Override
  default SszMutableBytes32Vector getBlockRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS);
    return getAnyByRef(fieldIndex);
  }

  default void setBlockRoots(final SszBytes32Vector blockRoots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BLOCK_ROOTS);
    set(fieldIndex, blockRoots);
  }

  @Override
  default SszMutableBytes32Vector getStateRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS);
    return getAnyByRef(fieldIndex);
  }

  default void setStateRoots(final SszBytes32Vector stateRoots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.STATE_ROOTS);
    set(fieldIndex, stateRoots);
  }

  @Override
  default SszMutablePrimitiveList<Bytes32, SszBytes32> getHistoricalRoots() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS);
    return getAnyByRef(fieldIndex);
  }

  default void setHistoricalRoots(final SszPrimitiveList<Bytes32, SszBytes32> historicalRoots) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.HISTORICAL_ROOTS);
    set(fieldIndex, historicalRoots);
  }

  // Eth1
  default void setEth1Data(final Eth1Data eth1Data) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA);
    set(fieldIndex, eth1Data);
  }

  @Override
  default SszMutableList<Eth1Data> getEth1DataVotes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES);
    return getAnyByRef(fieldIndex);
  }

  default void setEth1DataVotes(final SszList<Eth1Data> eth1DataList) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DATA_VOTES);
    set(fieldIndex, eth1DataList);
  }

  default void setEth1DepositIndex(final UInt64 eth1DepositIndex) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.ETH1_DEPOSIT_INDEX);
    set(fieldIndex, SszUInt64.of(eth1DepositIndex));
  }

  // Registry
  @Override
  default SszMutableList<Validator> getValidators() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS);
    return getAnyByRef(fieldIndex);
  }

  default void setValidators(final SszList<Validator> validators) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.VALIDATORS);
    set(fieldIndex, validators);
  }

  @Override
  default SszMutableUInt64List getBalances() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES);
    return getAnyByRef(fieldIndex);
  }

  default void setBalances(final SszUInt64List balances) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.BALANCES);
    set(fieldIndex, balances);
  }

  @Override
  default SszMutableBytes32Vector getRandaoMixes() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES);
    return getAnyByRef(fieldIndex);
  }

  default void setRandaoMixes(final SszBytes32Vector randaoMixes) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.RANDAO_MIXES);
    set(fieldIndex, randaoMixes);
  }

  // Slashings
  @Override
  default SszMutablePrimitiveVector<UInt64, SszUInt64> getSlashings() {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS);
    return getAnyByRef(fieldIndex);
  }

  default void setSlashings(final SszPrimitiveVector<UInt64, SszUInt64> slashings) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.SLASHINGS);
    set(fieldIndex, slashings);
  }

  // Finality
  default void setJustificationBits(final SszBitvector justificationBits) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.JUSTIFICATION_BITS);
    set(fieldIndex, justificationBits);
  }

  default void setPreviousJustifiedCheckpoint(final Checkpoint previousJustifiedCheckpoint) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_JUSTIFIED_CHECKPOINT);
    set(fieldIndex, previousJustifiedCheckpoint);
  }

  default void setCurrentJustifiedCheckpoint(final Checkpoint currentJustifiedCheckpoint) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_JUSTIFIED_CHECKPOINT);
    set(fieldIndex, currentJustifiedCheckpoint);
  }

  default void setFinalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.FINALIZED_CHECKPOINT);
    set(fieldIndex, finalizedCheckpoint);
  }

  @Override
  BeaconState commitChanges();

  default Optional<MutableBeaconStatePhase0> toMutableVersionPhase0() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateAltair> toMutableVersionAltair() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateBellatrix> toMutableVersionBellatrix() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateCapella> toMutableVersionCapella() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateDeneb> toMutableVersionDeneb() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateElectra> toMutableVersionElectra() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateFulu> toMutableVersionFulu() {
    return Optional.empty();
  }

  default Optional<MutableBeaconStateGloas> toMutableVersionGloas() {
    return Optional.empty();
  }
}
