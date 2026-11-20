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

package tech.pegasys.teku.spec.logic.versions.heze.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EpochProcessorHezeTest {

  private static final UInt64 DEPOSIT_AMOUNT = UInt64.valueOf(32_000_000_000L);

  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final EpochProcessor epochProcessor = spec.getGenesisSpec().getEpochProcessor();
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(spec.getGenesisSchemaDefinitions());

  @Test
  void shouldUseHezeEpochProcessorOnPq() {
    assertThat(epochProcessor).isInstanceOf(EpochProcessorHeze.class);
  }

  @Test
  void applyPendingDeposits_shouldSkipNewValidatorWithBlsCredentials() {
    final BeaconStatePq postState =
        applyDeposits(
            emptyState(),
            epochProcessor,
            deposit(dataStructureUtil.randomBytes32(), blsCredentials()));

    assertThat(postState.getValidators()).isEmpty();
    assertThat(postState.getBalances()).isEmpty();
  }

  @Test
  void applyPendingDeposits_shouldCreateNewValidatorWithEth1Credentials() {
    final Bytes32 key = dataStructureUtil.randomBytes32();

    final BeaconStatePq postState =
        applyDeposits(
            emptyState(),
            epochProcessor,
            deposit(key, dataStructureUtil.randomEth1WithdrawalCredentials()));

    assertThat(postState.getValidators()).hasSize(1);
    assertThat(((ValidatorPq) postState.getValidators().get(0)).getXmssPublicKey()).isEqualTo(key);
  }

  @Test
  void applyPendingDeposits_shouldTopUpExistingValidatorWithBlsCredentials() {
    final Bytes32 key = dataStructureUtil.randomBytes32();
    final BeaconStatePq preState =
        applyDeposits(
            emptyState(),
            epochProcessor,
            deposit(key, dataStructureUtil.randomEth1WithdrawalCredentials()));

    final BeaconStatePq postState =
        applyDeposits(preState, epochProcessor, deposit(key, blsCredentials()));

    assertThat(postState.getValidators()).hasSize(1);
    assertThat(postState.getBalances().getElement(0)).isEqualTo(DEPOSIT_AMOUNT.times(2));
  }

  @Test
  void applyPendingDeposits_pqShouldStillCreateNewValidatorWithBlsCredentials() {
    final EpochProcessor pqEpochProcessor =
        TestSpecFactory.createMinimalPq().getGenesisSpec().getEpochProcessor();

    final BeaconStatePq postState =
        applyDeposits(
            emptyState(),
            pqEpochProcessor,
            deposit(dataStructureUtil.randomBytes32(), blsCredentials()));

    assertThat(postState.getValidators()).hasSize(1);
  }

  private BeaconStatePq applyDeposits(
      final BeaconStatePq preState,
      final EpochProcessor processor,
      final PendingDeposit... deposits) {
    return preState.updatedPq(
        state -> {
          for (final PendingDeposit deposit : deposits) {
            processor.applyPendingDeposits(state, deposit);
          }
        });
  }

  private BeaconStatePq emptyState() {
    return BeaconStatePq.required(schemaDefinitions.getPqBeaconStateSchema().createEmpty());
  }

  private Bytes32 blsCredentials() {
    return dataStructureUtil.randomBlsWithdrawalCredentials();
  }

  /** A deposit of an XMSS key: {@code 0^16 ‖ key}, with the VRF root in the signature field. */
  private PendingDeposit deposit(final Bytes32 key, final Bytes32 withdrawalCredentials) {
    return schemaDefinitions
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(Bytes48.wrap(Bytes.concatenate(PqConstants.XMSS_DEPOSIT_PREFIX, key))),
            SszBytes32.of(withdrawalCredentials),
            SszUInt64.of(DEPOSIT_AMOUNT),
            new SszSignature(
                Bytes.concatenate(dataStructureUtil.randomBytes32(), Bytes.wrap(new byte[64]))),
            SszUInt64.ZERO);
  }
}
