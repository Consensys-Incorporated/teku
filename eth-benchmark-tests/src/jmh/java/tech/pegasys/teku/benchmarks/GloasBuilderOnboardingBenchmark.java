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

package tech.pegasys.teku.benchmarks;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit.PendingDepositSchema;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * Measures the Fulu to Gloas state upgrade when the pending deposit queue holds many validly signed
 * deposits for new validators, followed by a few builder deposits for new pubkeys. Builder
 * onboarding only needs to know whether a builder deposit's pubkey is a pending validator, so the
 * validator deposits' signatures should not dominate the upgrade time.
 *
 * <p>Run with: ./gradlew :eth-benchmark-tests:jmh --args="GloasBuilderOnboardingBenchmark"
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
public class GloasBuilderOnboardingBenchmark {

  private static final UInt64 GLOAS_EPOCH = UInt64.valueOf(2);
  private static final UInt64 DEPOSIT_AMOUNT = UInt64.valueOf(32_000_000_000L);

  @Param({"1000", "10000"})
  int validatorDepositCount;

  @Param({"100"})
  int builderDepositCount;

  private StateUpgrade<?> stateUpgrade;
  private BeaconState preState;

  @Setup(Level.Trial)
  public void init() {
    final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_EPOCH);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SpecVersion gloasSpecVersion = spec.atEpoch(GLOAS_EPOCH);
    stateUpgrade = gloasSpecVersion.getStateUpgrade().orElseThrow();
    final MiscHelpers miscHelpers = gloasSpecVersion.miscHelpers();
    final PendingDepositSchema pendingDepositSchema =
        SchemaDefinitionsElectra.required(gloasSpecVersion.getSchemaDefinitions())
            .getPendingDepositSchema();

    final List<PendingDeposit> pendingDeposits = new ArrayList<>();
    for (int i = 0; i < validatorDepositCount; i++) {
      pendingDeposits.add(
          signedPendingDeposit(
              miscHelpers,
              pendingDepositSchema,
              dataStructureUtil.randomKeyPair(),
              dataStructureUtil.randomEth1WithdrawalCredentials()));
    }
    for (int i = 0; i < builderDepositCount; i++) {
      final Bytes32 builderCredentials =
          Bytes32.wrap(
              Bytes.concatenate(
                  Bytes.of(WithdrawalPrefixes.BUILDER_WITHDRAWAL_BYTE),
                  dataStructureUtil.randomBytes32().slice(1)));
      pendingDeposits.add(
          signedPendingDeposit(
              miscHelpers,
              pendingDepositSchema,
              dataStructureUtil.randomKeyPair(),
              builderCredentials));
    }

    final BeaconState baseState =
        dataStructureUtil
            .stateBuilder(SpecMilestone.FULU, 64, 0)
            .setSlotToStartOfEpoch(GLOAS_EPOCH)
            .build()
            .updated(GloasBuilderOnboardingBenchmark::activateAllValidators);
    preState =
        baseState.updated(
            state ->
                ((MutableBeaconStateElectra) state)
                    .setPendingDeposits(
                        baseState
                            .toVersionElectra()
                            .orElseThrow()
                            .getPendingDeposits()
                            .getSchema()
                            .createFromElements(pendingDeposits)));
  }

  @Benchmark
  public BeaconState upgradeToGloas() {
    return stateUpgrade.upgrade(preState);
  }

  private static void activateAllValidators(final MutableBeaconState state) {
    final SszMutableList<Validator> validators = state.getValidators();
    for (int i = 0; i < validators.size(); i++) {
      validators.update(
          i,
          validator ->
              validator
                  .withActivationEligibilityEpoch(UInt64.ZERO)
                  .withActivationEpoch(UInt64.ZERO)
                  .withExitEpoch(FAR_FUTURE_EPOCH)
                  .withWithdrawableEpoch(FAR_FUTURE_EPOCH));
    }
  }

  private static PendingDeposit signedPendingDeposit(
      final MiscHelpers miscHelpers,
      final PendingDepositSchema pendingDepositSchema,
      final BLSKeyPair keyPair,
      final Bytes32 withdrawalCredentials) {
    final BLSPublicKey pubkey = keyPair.getPublicKey();
    return pendingDepositSchema.create(
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(DEPOSIT_AMOUNT),
        new SszSignature(
            BLS.sign(
                keyPair.getSecretKey(),
                miscHelpers.computeDepositSigningRoot(
                    pubkey, withdrawalCredentials, DEPOSIT_AMOUNT))),
        SszUInt64.of(UInt64.ZERO));
  }
}
