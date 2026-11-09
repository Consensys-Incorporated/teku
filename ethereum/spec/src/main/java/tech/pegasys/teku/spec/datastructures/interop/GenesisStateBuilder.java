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

package tech.pegasys.teku.spec.datastructures.interop;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class GenesisStateBuilder {

  private Spec spec;
  private boolean signDeposits = true;
  private UInt64 genesisTime = UInt64.ZERO;
  private Optional<ExecutionPayloadHeader> executionPayloadHeader = Optional.empty();
  private final List<Deposit> genesisDeposits = new ArrayList<>();

  public BeaconState build() {
    checkNotNull(spec, "Must provide a spec");

    // If our Genesis is post-Bellatrix, we must have a non-default Execution Payload Header (but we
    // should not override if one has been specified)
    if (executionPayloadHeader.isEmpty()
        && spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      executionPayloadHeader = Optional.of(mockExecutionPayloadHeader());
    }

    final Bytes32 eth1BlockHash =
        executionPayloadHeader
            .map(ExecutionPayloadSummary::getBlockHash)
            .orElseGet(this::generateMockGenesisBlockHash);

    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(
            eth1BlockHash, genesisTime, genesisDeposits, executionPayloadHeader);
    return initialState.updated(state -> state.setGenesisTime(genesisTime));
  }

  public GenesisStateBuilder spec(final Spec spec) {
    this.spec = spec;
    return this;
  }

  public GenesisStateBuilder signDeposits(final boolean signDeposits) {
    this.signDeposits = signDeposits;
    return this;
  }

  public GenesisStateBuilder genesisTime(final long genesisTime) {
    return genesisTime(UInt64.valueOf(genesisTime));
  }

  public GenesisStateBuilder genesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
    return this;
  }

  public GenesisStateBuilder addValidator(final BLSKeyPair keyPair) {
    return addValidator(keyPair, spec.getGenesisSpecConfig().getMaxEffectiveBalance());
  }

  public GenesisStateBuilder addValidator(final BLSKeyPair keyPair, final UInt64 depositAmount) {
    return addValidator(
        new DepositGenerator(spec, signDeposits)
            .createDepositData(keyPair, depositAmount, keyPair.getPublicKey()));
  }

  public GenesisStateBuilder addValidators(final List<BLSKeyPair> validatorKeys) {
    validatorKeys.forEach(this::addValidator);
    return this;
  }

  public GenesisStateBuilder addValidator(
      final BLSKeyPair keyPair, final Bytes20 withdrawalAddress) {
    return addValidator(
        new DepositGenerator(spec, signDeposits)
            .createDepositData(
                keyPair, spec.getGenesisSpecConfig().getMaxEffectiveBalance(), withdrawalAddress));
  }

  public GenesisStateBuilder addValidator(
      final BLSKeyPair keyPair, final Bytes32 withdrawalCredentials) {
    return addValidator(
        new DepositGenerator(spec, signDeposits)
            .createDepositData(
                keyPair,
                spec.getGenesisSpecConfig().getMaxEffectiveBalance(),
                withdrawalCredentials));
  }

  public GenesisStateBuilder addValidator(final DepositData depositData) {
    genesisDeposits.add(new Deposit(depositData));
    return this;
  }

  public GenesisStateBuilder addMockValidators(final int validatorCount) {
    return addValidators(
        new MockStartValidatorKeyPairFactory().generateKeyPairs(0, validatorCount));
  }

  public GenesisStateBuilder executionPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader) {
    this.executionPayloadHeader = Optional.of(executionPayloadHeader);
    return this;
  }

  public GenesisStateBuilder executionPayloadHeader(
      final Optional<ExecutionPayloadHeader> executionPayloadHeader) {
    this.executionPayloadHeader = executionPayloadHeader;
    return this;
  }

  private Bytes32 generateMockGenesisBlockHash() {
    return Bytes32.repeat((byte) 0x42);
  }

  private ExecutionPayloadHeader mockExecutionPayloadHeader() {
    return SchemaDefinitionsBellatrix.required(spec.getGenesisSchemaDefinitions())
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            b -> {
              b.blockHash(generateMockGenesisBlockHash());
              b.parentHash(Bytes32.ZERO);
              b.feeRecipient(Bytes20.ZERO);
              b.stateRoot(Bytes32.ZERO);
              b.receiptsRoot(Bytes32.ZERO);
              b.logsBloom(Bytes.repeat((byte) 0x00, 256));
              b.prevRandao(Bytes32.ZERO);
              b.blockNumber(UInt64.ZERO);
              b.gasLimit(UInt64.ZERO);
              b.gasUsed(UInt64.ZERO);
              b.timestamp(UInt64.ZERO);
              b.extraData(Bytes.repeat((byte) 0x00, 20));
              b.baseFeePerGas(UInt256.ZERO);
              b.transactionsRoot(Bytes32.ZERO);
              // Capella
              b.withdrawalsRoot(() -> Bytes32.ZERO);
              // Deneb
              b.blobGasUsed(() -> UInt64.ZERO);
              b.excessBlobGas(() -> UInt64.ZERO);
              // Gloas
              b.blockAccessListRoot(() -> Bytes32.ZERO);
              b.slotNumber(() -> UInt64.ZERO);
            });
  }
}
