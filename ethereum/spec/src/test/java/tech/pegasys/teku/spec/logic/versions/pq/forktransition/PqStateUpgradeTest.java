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

package tech.pegasys.teku.spec.logic.versions.pq.forktransition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateSchemaEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingPayment;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.BuilderPendingWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip8483;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PqStateUpgradeTest {

  private static final UInt64 FORK_EPOCH = UInt64.valueOf(2);

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(FORK_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion pqVersion = spec.forMilestone(SpecMilestone.PQ);
  private final SpecConfigPq config = SpecConfigPq.required(pqVersion.getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(pqVersion.getSchemaDefinitions());
  private final SchemaDefinitionsEip8483 eip8483Schemas =
      SchemaDefinitionsEip8483.required(
          spec.forMilestone(SpecMilestone.EIP8483).getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
  private final PqStateUpgrade stateUpgrade =
      new PqStateUpgrade(config, schemaDefinitions, accessors, miscHelpers);

  private final Bytes32 registeredKey = Bytes32.repeat((byte) 7);
  private final Bytes32 registeredVrfRoot = Bytes32.repeat((byte) 8);
  private final Bytes32 registeredBuilderKey = Bytes32.repeat((byte) 9);
  private final BLSPublicKey builder0Key = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey builder1Key = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey newDepositKey = dataStructureUtil.randomPublicKey();

  private final BeaconStateEip8483 pre = preState();
  private final BeaconStatePq post = stateUpgrade.upgrade(pre);

  @Test
  void forkAndCarriedFieldsFollowThePreState() {
    assertThat(post.getFork().getPreviousVersion()).isEqualTo(pre.getFork().getCurrentVersion());
    assertThat(post.getFork().getCurrentVersion()).isEqualTo(config.getPqForkVersion());
    assertThat(post.getFork().getEpoch()).isEqualTo(FORK_EPOCH);
    assertThat(post.getSlot()).isEqualTo(pre.getSlot());
    assertThat(post.getBalances()).isEqualTo(pre.getBalances());
    assertThat(post.getRandaoMixes()).isEqualTo(pre.getRandaoMixes());
    assertThat(post.getLatestExecutionPayloadBid()).isEqualTo(pre.getLatestExecutionPayloadBid());
    assertThat(post.getProposerLookahead()).isEqualTo(pre.getProposerLookahead());
    assertThat(post.getPtcWindow()).isEqualTo(pre.getPtcWindow());
    assertThat(post.getValidatorPreregistrations()).isEmpty();
    assertThat(post.getBuilderPreregistrations()).isEmpty();

    final BeaconStatePq roundTripped =
        BeaconStatePq.required(
            schemaDefinitions.getPqBeaconStateSchema().sszDeserialize(post.sszSerialize()));
    assertThat(roundTripped.hashTreeRoot()).isEqualTo(post.hashTreeRoot());
  }

  @Test
  void upgradeFromGloasLeavesEveryValidatorUnregistered() {
    // EIP-8483 scheduled at the same epoch as PQ is overshadowed, so PQ upgrades a Gloas state
    final BeaconStateGloas gloasPre =
        BeaconStateGloas.required(
            new DataStructureUtil(TestSpecFactory.createMinimalGloas())
                .stateBuilderGloas(16, 3, 4)
                .slot(spec.computeStartSlotAtEpoch(FORK_EPOCH))
                .build());

    final BeaconStatePq gloasPost = stateUpgrade.upgrade(gloasPre);

    assertThat(gloasPost.getFork().getPreviousVersion())
        .isEqualTo(gloasPre.getFork().getCurrentVersion());
    assertThat(gloasPost.getFork().getCurrentVersion()).isEqualTo(config.getPqForkVersion());
    assertThat(gloasPost.getValidators().size()).isEqualTo(gloasPre.getValidators().size());
    for (int i = 0; i < gloasPost.getValidators().size(); i++) {
      final Bytes48 blsKey = gloasPre.getValidators().get(i).getPubkeyBytes();
      final ValidatorPq validator = validator(gloasPost, i);
      assertThat(validator.getXmssPublicKey())
          .isEqualTo(miscHelpers.computeLegacyXmssPubkey(blsKey));
      if (!validator.isSlashed()) {
        assertThat(validator.getExitEpoch()).isLessThanOrEqualTo(FORK_EPOCH);
      }
    }
    assertThat(gloasPost.getLatestExecutionPayloadBid())
        .isEqualTo(gloasPre.getLatestExecutionPayloadBid());
  }

  @Test
  void registeredValidatorTakesItsKeysAndKeepsItsEpochs() {
    final ValidatorPq validator = validator(post, 3);
    assertThat(validator.getXmssPublicKey()).isEqualTo(registeredKey);
    assertThat(validator.getVrfRoot()).isEqualTo(registeredVrfRoot);
    assertThat(validator.getExitEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(validator.getWithdrawableEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(validator.getEffectiveBalance())
        .isEqualTo(pre.getValidators().get(3).getEffectiveBalance());
    assertThat(validator.getWithdrawalCredentials())
        .isEqualTo(pre.getValidators().get(3).getWithdrawalCredentials());
  }

  @Test
  void unregisteredValidatorTakesTheLegacyKeysAndExits() {
    final Bytes48 blsKey = pre.getValidators().get(6).getPubkeyBytes();
    final ValidatorPq validator = validator(post, 6);
    assertThat(validator.getXmssPublicKey()).isEqualTo(miscHelpers.computeLegacyXmssPubkey(blsKey));
    assertThat(validator.getVrfRoot()).isEqualTo(miscHelpers.computeLegacyVrfRoot(blsKey));
    assertThat(validator.getExitEpoch()).isEqualTo(FORK_EPOCH);
    assertThat(validator.getWithdrawableEpoch())
        .isEqualTo(FORK_EPOCH.plus(config.getUnregisteredValidatorWithdrawabilityDelay()));
  }

  @Test
  void registrationOfALegacyKeyIsIgnored() {
    final Bytes48 blsKey = pre.getValidators().get(4).getPubkeyBytes();
    final ValidatorPq validator = validator(post, 4);
    assertThat(validator.getVrfRoot()).isEqualTo(miscHelpers.computeLegacyVrfRoot(blsKey));
    assertThat(validator.getExitEpoch()).isEqualTo(FORK_EPOCH);
  }

  @Test
  void slashedUnregisteredValidatorKeepsItsWithdrawableEpoch() {
    final ValidatorPq validator = validator(post, 5);
    assertThat(validator.isSlashed()).isTrue();
    assertThat(validator.getExitEpoch()).isEqualTo(FORK_EPOCH);
    assertThat(validator.getWithdrawableEpoch()).isEqualTo(UInt64.valueOf(100));
  }

  @Test
  void pendingDepositsForExistingValidatorsAreRenamed() {
    final List<PendingDeposit> deposits = post.getPendingDeposits().asList();
    assertThat(deposits).hasSize(2);
    assertThat(deposits.get(0).getPubkeyBytes())
        .isEqualTo(Bytes48.wrap(Bytes.concatenate(PqConstants.XMSS_DEPOSIT_PREFIX, registeredKey)));
    assertThat(deposits.get(0).getAmount()).isEqualTo(pre.getPendingDeposits().get(0).getAmount());
    assertThat(deposits.get(1)).isEqualTo(pre.getPendingDeposits().get(1));
  }

  @Test
  void buildersTakeTheirKeysAndUnregisteredOnesBecomeWithdrawable() {
    final BuilderPq registered = (BuilderPq) post.getBuilders().get(0);
    assertThat(registered.getXmssPublicKey()).isEqualTo(registeredBuilderKey);
    assertThat(registered.getWithdrawableEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(registered.getBalance()).isEqualTo(pre.getBuilders().get(0).getBalance());

    final BuilderPq unregistered = (BuilderPq) post.getBuilders().get(1);
    assertThat(unregistered.getXmssPublicKey())
        .isEqualTo(miscHelpers.computeLegacyXmssPubkey(builder1Key.toBytesCompressed()));
    assertThat(unregistered.getWithdrawableEpoch()).isEqualTo(FORK_EPOCH);
  }

  @Test
  void unregisteredBuildersPaymentsSettleAtQuorumAndAreOtherwiseDropped() {
    final List<BuilderPendingPayment> payments = post.getBuilderPendingPayments().asList();
    // Builder 1 is unregistered: both its payments are cleared
    assertThat(payments.get(0).getWithdrawal().getAmount()).isEqualTo(UInt64.ZERO);
    assertThat(payments.get(1).getWithdrawal().getAmount()).isEqualTo(UInt64.ZERO);
    // Builder 0 registered: its payment is kept
    assertThat(payments.get(2)).isEqualTo(pre.getBuilderPendingPayments().get(2));

    // Only the payment that reached the quorum is settled
    final List<BuilderPendingWithdrawal> withdrawals = post.getBuilderPendingWithdrawals().asList();
    assertThat(withdrawals).containsExactly(pre.getBuilderPendingPayments().get(0).getWithdrawal());
  }

  @Test
  void aggregationCommitteeWindowStartsAtTheForkEpoch() {
    final int slotsPerEpoch = config.getSlotsPerEpoch();
    final List<SszList<SszList<SszUInt64>>> window = post.getAggregationCommitteeWindow().asList();
    for (int i = 0; i < slotsPerEpoch; i++) {
      assertThat(window.get(i)).isEmpty();
    }
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(FORK_EPOCH);
    for (int i = slotsPerEpoch; i < window.size(); i++) {
      final List<int[]> expected =
          accessors.computeAggregationCommittees(post, startSlot.plus(i - slotsPerEpoch)).stream()
              .map(IntList::toIntArray)
              .toList();
      final List<int[]> actual =
          window.get(i).stream()
              .map(
                  committee ->
                      committee.stream().mapToInt(index -> index.get().intValue()).toArray())
              .toList();
      assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
      assertThat(actual).isNotEmpty();
    }
  }

  private static ValidatorPq validator(final BeaconStatePq state, final int index) {
    return (ValidatorPq) state.getValidators().get(index);
  }

  private BeaconStateEip8483 preState() {
    final BeaconStateEip8483 genesis =
        BeaconStateEip8483.required(dataStructureUtil.genesisBeaconState(64));
    final BeaconStateSchemaEip8483 stateSchema =
        BeaconStateSchemaEip8483.required(eip8483Schemas.getBeaconStateSchema());
    final Validator validator3 = genesis.getValidators().get(3);
    final Bytes48 validator4Key = genesis.getValidators().get(4).getPubkeyBytes();
    return BeaconStateEip8483.required(
        genesis.updatedEip8483(
            state -> {
              state.setSlot(miscHelpers.computeStartSlotAtEpoch(FORK_EPOCH));
              final Validator validator5 = state.getValidators().get(5);
              state
                  .getValidators()
                  .set(
                      5,
                      validator5
                          .withSlashed(true)
                          .withExitEpoch(UInt64.valueOf(50))
                          .withWithdrawableEpoch(UInt64.valueOf(100)));
              state.setValidatorPqRegistrations(
                  stateSchema
                      .getValidatorPqRegistrationsSchema()
                      .of(
                          eip8483Schemas
                              .getValidatorPqRegistrationSchema()
                              .create(
                                  UInt64.valueOf(3),
                                  Bytes.concatenate(registeredKey, registeredVrfRoot)),
                          eip8483Schemas
                              .getValidatorPqRegistrationSchema()
                              .create(
                                  UInt64.valueOf(4),
                                  Bytes.concatenate(
                                      miscHelpers.computeLegacyXmssPubkey(validator4Key),
                                      registeredVrfRoot))));
              state.getBuilders().append(builder(builder0Key));
              state.getBuilders().append(builder(builder1Key));
              state.setBuilderPqRegistrations(
                  stateSchema
                      .getBuilderPqRegistrationsSchema()
                      .of(
                          eip8483Schemas
                              .getBuilderPqRegistrationSchema()
                              .create(builder0Key, registeredBuilderKey)));
              state.getBuilderPendingPayments().set(0, payment(1, 5, UInt64.MAX_VALUE));
              state.getBuilderPendingPayments().set(1, payment(1, 6, UInt64.ZERO));
              state.getBuilderPendingPayments().set(2, payment(0, 7, UInt64.MAX_VALUE));
              state.getPendingDeposits().append(pendingDeposit(validator3.getPubkeyBytes()));
              state.getPendingDeposits().append(pendingDeposit(newDepositKey.toBytesCompressed()));
            }));
  }

  private BuilderGloas builder(final BLSPublicKey pubkey) {
    return new BuilderGloas(
        pubkey,
        SpecConfigGloas.PAYLOAD_BUILDER_VERSION,
        dataStructureUtil.randomEth1Address(),
        UInt64.valueOf(10_000_000_000L),
        UInt64.ZERO,
        FAR_FUTURE_EPOCH);
  }

  private BuilderPendingPayment payment(
      final int builderIndex, final long amount, final UInt64 weight) {
    return schemaDefinitions
        .getBuilderPendingPaymentSchema()
        .create(
            weight,
            schemaDefinitions
                .getBuilderPendingWithdrawalSchema()
                .create(
                    dataStructureUtil.randomEth1Address(),
                    UInt64.valueOf(amount),
                    UInt64.valueOf(builderIndex)),
            UInt64.ZERO);
  }

  private PendingDeposit pendingDeposit(final Bytes48 pubkey) {
    return schemaDefinitions
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(pubkey),
            SszBytes32.of(dataStructureUtil.randomBytes32()),
            SszUInt64.of(UInt64.valueOf(1_000_000_000L)),
            new SszSignature(dataStructureUtil.randomSignature()),
            SszUInt64.of(UInt64.ONE));
  }
}
