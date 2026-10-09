/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EpochProcessorHezeTest {

  private static final UInt64 DEPOSIT_AMOUNT = UInt64.valueOf(1_000_000_000L);

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final EpochProcessor epochProcessor = spec.getGenesisSpec().getEpochProcessor();

  @Test
  void shouldUseHezeEpochProcessor() {
    assertThat(epochProcessor).isInstanceOf(EpochProcessorHeze.class);
  }

  @Test
  void applyPendingDeposits_shouldSkipNewValidatorWithBlsCredentialsAndValidSignature() {
    final BeaconState preState = activeState();
    final PendingDeposit deposit =
        signedDeposit(dataStructureUtil.randomBlsWithdrawalCredentials());

    final BeaconState postState = applyDeposits(preState, deposit);

    assertThat(postState.getValidators().size()).isEqualTo(preState.getValidators().size());
    assertThat(postState.getBalances()).isEqualTo(preState.getBalances());
  }

  @Test
  void applyPendingDeposits_shouldSkipNewValidatorWithBlsCredentialsAndInvalidSignature() {
    final BeaconState preState = activeState();
    final PendingDeposit deposit =
        pendingDeposit(
            dataStructureUtil.randomPublicKey(),
            dataStructureUtil.randomBlsWithdrawalCredentials(),
            dataStructureUtil.randomSignature());

    final BeaconState postState = applyDeposits(preState, deposit);

    assertThat(postState.getValidators().size()).isEqualTo(preState.getValidators().size());
    assertThat(postState.getBalances()).isEqualTo(preState.getBalances());
  }

  @Test
  void applyPendingDeposits_shouldCreateNewValidatorWithEth1Credentials() {
    final BeaconState preState = activeState();
    final PendingDeposit deposit =
        signedDeposit(dataStructureUtil.randomEth1WithdrawalCredentials());

    final BeaconState postState = applyDeposits(preState, deposit);

    assertThat(postState.getValidators().size()).isEqualTo(preState.getValidators().size() + 1);
    assertThat(postState.getValidators().get(preState.getValidators().size()).getPublicKey())
        .isEqualTo(deposit.getPublicKey());
  }

  @Test
  void applyPendingDeposits_shouldTopUpExistingValidatorWithBlsCredentials() {
    final BeaconState preState = activeState();
    final int validatorIndex = 1;
    final UInt64 balanceBefore = preState.getBalances().getElement(validatorIndex);
    // Top-ups don't verify the signature, so an invalid one must still be applied
    final PendingDeposit deposit =
        pendingDeposit(
            preState.getValidators().get(validatorIndex).getPublicKey(),
            dataStructureUtil.randomBlsWithdrawalCredentials(),
            dataStructureUtil.randomSignature());

    final BeaconState postState = applyDeposits(preState, deposit);

    assertThat(postState.getValidators().size()).isEqualTo(preState.getValidators().size());
    assertThat(postState.getBalances().getElement(validatorIndex))
        .isEqualTo(balanceBefore.plus(DEPOSIT_AMOUNT));
  }

  @Test
  void applyPendingDeposits_shouldCreateValidatorFromEth1DepositAfterSkippedBlsDeposit() {
    final BeaconState preState = activeState();
    final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
    final PendingDeposit blsDeposit =
        signedDeposit(keyPair, dataStructureUtil.randomBlsWithdrawalCredentials());
    final Bytes32 eth1Credentials = dataStructureUtil.randomEth1WithdrawalCredentials();
    final PendingDeposit eth1Deposit = signedDeposit(keyPair, eth1Credentials);

    final BeaconState postState = applyDeposits(preState, blsDeposit, eth1Deposit);

    final int newValidatorIndex = preState.getValidators().size();
    assertThat(postState.getValidators().size()).isEqualTo(newValidatorIndex + 1);
    assertThat(postState.getValidators().get(newValidatorIndex).getPublicKey())
        .isEqualTo(keyPair.getPublicKey());
    assertThat(postState.getValidators().get(newValidatorIndex).getWithdrawalCredentials())
        .isEqualTo(eth1Credentials);
    assertThat(postState.getBalances().getElement(newValidatorIndex)).isEqualTo(DEPOSIT_AMOUNT);
  }

  @Test
  void applyPendingDeposits_gloasShouldStillCreateNewValidatorWithBlsCredentials() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasDataStructureUtil = new DataStructureUtil(gloasSpec);
    final BeaconState preState = activeState(gloasDataStructureUtil);
    final DepositData depositData =
        new DepositGenerator(gloasSpec)
            .createDepositData(
                gloasDataStructureUtil.randomKeyPair(),
                DEPOSIT_AMOUNT,
                gloasDataStructureUtil.randomBlsWithdrawalCredentials());
    final PendingDeposit deposit = toPendingDeposit(depositData);

    final EpochProcessor gloasEpochProcessor = gloasSpec.getGenesisSpec().getEpochProcessor();
    final BeaconState postState =
        preState.updated(state -> gloasEpochProcessor.applyPendingDeposits(state, deposit));

    assertThat(postState.getValidators().size()).isEqualTo(preState.getValidators().size() + 1);
  }

  private BeaconState applyDeposits(final BeaconState preState, final PendingDeposit... deposits) {
    return preState.updated(
        state -> {
          for (final PendingDeposit deposit : deposits) {
            epochProcessor.applyPendingDeposits(state, deposit);
          }
        });
  }

  private PendingDeposit signedDeposit(final Bytes32 withdrawalCredentials) {
    return signedDeposit(dataStructureUtil.randomKeyPair(), withdrawalCredentials);
  }

  private PendingDeposit signedDeposit(
      final BLSKeyPair keyPair, final Bytes32 withdrawalCredentials) {
    return toPendingDeposit(
        new DepositGenerator(spec)
            .createDepositData(keyPair, DEPOSIT_AMOUNT, withdrawalCredentials));
  }

  private PendingDeposit toPendingDeposit(final DepositData depositData) {
    return pendingDeposit(
        depositData.getPubkey(),
        depositData.getWithdrawalCredentials(),
        depositData.getAmount(),
        depositData.getSignature());
  }

  private PendingDeposit pendingDeposit(
      final BLSPublicKey publicKey,
      final Bytes32 withdrawalCredentials,
      final BLSSignature signature) {
    return pendingDeposit(publicKey, withdrawalCredentials, DEPOSIT_AMOUNT, signature);
  }

  private PendingDeposit pendingDeposit(
      final BLSPublicKey publicKey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(publicKey),
            SszBytes32.of(withdrawalCredentials),
            SszUInt64.of(amount),
            new SszSignature(signature),
            SszUInt64.ZERO);
  }

  private BeaconState activeState() {
    return activeState(dataStructureUtil);
  }

  private static BeaconState activeState(final DataStructureUtil dataStructureUtil) {
    final BeaconStateTestBuilder builder = new BeaconStateTestBuilder(dataStructureUtil).slot(0);
    IntStream.range(0, 4).forEach(__ -> builder.activeValidator(UInt64.THIRTY_TWO_ETH));
    return builder.build();
  }
}
