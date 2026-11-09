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

package tech.pegasys.teku.spec.datastructures.state;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_EPOCH;

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.MutableBeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateTestBuilder {
  private final List<Validator> validators = new ArrayList<>();
  private final List<UInt64> balances = new ArrayList<>();

  private final List<PendingPartialWithdrawal> pendingPartialWithdrawals = new ArrayList<>();
  private UInt64 slot;
  private Fork fork;
  private final DataStructureUtil dataStructureUtil;

  private final SpecConfig specConfig;
  private final Spec spec;

  public BeaconStateTestBuilder(final DataStructureUtil dataStructureUtil) {
    this.slot = dataStructureUtil.randomUInt64();
    this.fork = dataStructureUtil.randomFork();
    this.dataStructureUtil = dataStructureUtil;
    this.specConfig = dataStructureUtil.getSpec().getGenesisSpecConfig();
    this.spec = dataStructureUtil.getSpec();
  }

  public BeaconStateTestBuilder slot(final long slot) {
    this.slot = UInt64.valueOf(slot);
    return this;
  }

  public BeaconStateTestBuilder forkVersion(final Bytes4 onlyForkVersion) {
    fork = new Fork(onlyForkVersion, onlyForkVersion, GENESIS_EPOCH);
    return this;
  }

  public BeaconStateTestBuilder validator(final Validator validator) {
    validators.add(validator);
    balances.add(validator.getEffectiveBalance());
    return this;
  }

  public BeaconStateTestBuilder activeValidator(final UInt64 balance) {
    final UInt64 maxEffectiveBalance =
        spec.getSpecConfig(spec.computeEpochAtSlot(slot)).getMaxEffectiveBalance();
    validators.add(
        dataStructureUtil
            .randomValidator()
            .withEffectiveBalance(maxEffectiveBalance.min(balance))
            .withActivationEpoch(UInt64.ZERO)
            .withExitEpoch(FAR_FUTURE_EPOCH));
    balances.add(balance);
    return this;
  }

  public BeaconStateTestBuilder activeEth1Validator(final UInt64 balance) {
    validators.add(
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(dataStructureUtil.randomEth1WithdrawalCredentials())
            .withEffectiveBalance(specConfig.getMaxEffectiveBalance().min(balance))
            .withActivationEpoch(UInt64.ZERO)
            .withExitEpoch(FAR_FUTURE_EPOCH));
    balances.add(balance);
    return this;
  }

  public BeaconStateTestBuilder activeConsolidatingValidator(final UInt64 balance) {
    validators.add(
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(dataStructureUtil.randomCompoundingWithdrawalCredentials())
            .withEffectiveBalance(
                SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra().min(balance))
            .withActivationEpoch(UInt64.ZERO)
            .withExitEpoch(FAR_FUTURE_EPOCH));
    balances.add(balance);
    return this;
  }

  public BeaconStateTestBuilder activeConsolidatingValidatorQueuedForExit(final UInt64 balance) {
    validators.add(
        dataStructureUtil
            .randomValidator()
            .withWithdrawalCredentials(dataStructureUtil.randomCompoundingWithdrawalCredentials())
            .withEffectiveBalance(
                SpecConfigElectra.required(specConfig).getMaxEffectiveBalanceElectra().min(balance))
            .withActivationEpoch(UInt64.ZERO)
            .withExitEpoch(FAR_FUTURE_EPOCH.minus(1)));
    balances.add(balance);
    return this;
  }

  public BeaconStateTestBuilder proposerLookahead(final SszUInt64Vector proposerLookahead) {
    final SpecVersion specVersion = dataStructureUtil.getSpec().atSlot(slot);
    MutableBeaconStateFulu.required(
            (MutableBeaconState)
                specVersion.getSchemaDefinitions().getBeaconStateSchema().createEmpty())
        .setProposerLookahead(proposerLookahead);
    return this;
  }

  public BeaconState build() {
    final SpecVersion specVersion = dataStructureUtil.getSpec().atSlot(slot);
    return specVersion
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .createEmpty()
        .updated(
            state -> {
              state.setSlot(slot);
              state.setFork(fork);
              state.getValidators().appendAll(validators);
              state.getBalances().appendAllElements(balances);
              if (!pendingPartialWithdrawals.isEmpty()) {
                final SszList<PendingPartialWithdrawal> partialWithdrawalSszList =
                    SchemaDefinitionsElectra.required(
                            dataStructureUtil.getSpec().atSlot(slot).getSchemaDefinitions())
                        .getPendingPartialWithdrawalsSchema()
                        .createFromElements(pendingPartialWithdrawals);
                MutableBeaconStateElectra.required(state)
                    .setPendingPartialWithdrawals(partialWithdrawalSszList);
              }
            });
  }

  public BeaconStateTestBuilder pendingPartialWithdrawal(
      final int validatorIndex, final UInt64 partialBalanceAmount) {
    final PendingPartialWithdrawal pendingPartialWithdrawal =
        SchemaDefinitionsElectra.required(
                dataStructureUtil.getSpec().atSlot(slot).getSchemaDefinitions())
            .getPendingPartialWithdrawalSchema()
            .create(
                SszUInt64.of(UInt64.valueOf(validatorIndex)),
                SszUInt64.of(partialBalanceAmount),
                SszUInt64.of(
                    dataStructureUtil
                        .getSpec()
                        .atSlot(slot)
                        .miscHelpers()
                        .computeEpochAtSlot(slot)));

    pendingPartialWithdrawals.add(pendingPartialWithdrawal);
    return this;
  }
}
