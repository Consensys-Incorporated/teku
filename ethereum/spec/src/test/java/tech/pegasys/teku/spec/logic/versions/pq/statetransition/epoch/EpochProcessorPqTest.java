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

package tech.pegasys.teku.spec.logic.versions.pq.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class EpochProcessorPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecVersion specLogic = spec.forMilestone(SpecMilestone.PQ);
  private final SpecConfigPq config = SpecConfigPq.required(specLogic.getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(specLogic.getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
  private final BeaconStateMutatorsPq mutators =
      new BeaconStateMutatorsPq(config, miscHelpers, accessors, schemaDefinitions);
  private final EpochProcessorPq epochProcessor =
      new EpochProcessorPq(
          config,
          predicates,
          miscHelpers,
          accessors,
          mutators,
          specLogic.getValidatorsUtil(),
          specLogic.getBeaconStateUtil(),
          specLogic.getValidatorStatusFactory(),
          schemaDefinitions,
          StubTimeProvider.withTimeInSeconds(0));

  @Test
  void aggregationCommitteeWindowShiftsByAnEpochAndFillsTheLast() throws IOException {
    final BeaconStatePq genesis = fixtureGenesis();
    final int slotsPerEpoch = config.getSlotsPerEpoch();

    final BeaconStatePq processed =
        genesis.updatedPq(epochProcessor::processAggregationCommitteeWindow);

    final int windowLength = genesis.getAggregationCommitteeWindow().size();
    for (int i = 0; i < windowLength - slotsPerEpoch; i++) {
      assertThat(processed.getAggregationCommitteeWindow().get(i))
          .isEqualTo(genesis.getAggregationCommitteeWindow().get(i + slotsPerEpoch));
    }
    // The last epoch is current + MIN_SEED_LOOKAHEAD + 1 = 2, computed from the state
    for (int i = 0; i < slotsPerEpoch; i++) {
      final UInt64 slot = UInt64.valueOf(2L * slotsPerEpoch + i);
      final List<int[]> expected =
          accessors.computeAggregationCommittees(genesis, slot).stream()
              .map(committee -> committee.toIntArray())
              .toList();
      final List<int[]> actual =
          processed.getAggregationCommitteeWindow().get(windowLength - slotsPerEpoch + i).stream()
              .map(
                  committee ->
                      committee.stream().mapToInt(index -> index.get().intValue()).toArray())
              .toList();
      assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }
  }

  @Test
  void preregistrationExpirySweepsOnlyExpiredRecords() {
    final BeaconStatePq state =
        state(UInt64.valueOf(100))
            .updatedPq(
                mutable -> {
                  mutable
                      .getValidatorPreregistrations()
                      .append(validatorPreregistration(UInt64.valueOf(100)));
                  mutable
                      .getValidatorPreregistrations()
                      .append(validatorPreregistration(UInt64.valueOf(101)));
                  mutable
                      .getBuilderPreregistrations()
                      .append(
                          new BuilderPreregistration(
                              dataStructureUtil.randomBytes32(), Bytes32.ZERO, UInt64.valueOf(50)));
                });

    final BeaconStatePq processed = state.updatedPq(epochProcessor::processPreregistrationExpiry);

    assertThat(processed.getValidatorPreregistrations()).hasSize(1);
    assertThat(processed.getValidatorPreregistrations().get(0).getExpirySlot())
        .isEqualTo(UInt64.valueOf(101));
    assertThat(processed.getBuilderPreregistrations()).isEmpty();
  }

  @Test
  void legacyDepositCreatesAnExitedValidatorWithoutConsumingChurn() {
    final Bytes48 legacyKey = dataStructureUtil.randomPublicKey().toBytesCompressed();
    // Far more than the churn: a legacy deposit is applied regardless
    final UInt64 amount = UInt64.valueOf(2048_000_000_000L);
    final BeaconStatePq state =
        state(UInt64.ZERO)
            .updatedPq(
                mutable -> mutable.getPendingDeposits().append(pendingDeposit(legacyKey, amount)));

    final BeaconStatePq processed = state.updatedPq(epochProcessor::processPendingDeposits);

    assertThat(processed.getPendingDeposits()).isEmpty();
    assertThat(processed.getValidators()).hasSize(1);
    final ValidatorPq validator = (ValidatorPq) processed.getValidators().get(0);
    assertThat(validator.getXmssPublicKey())
        .isEqualTo(miscHelpers.computeLegacyXmssPubkey(legacyKey));
    assertThat(validator.getVrfRoot()).isEqualTo(miscHelpers.computeLegacyVrfRoot(legacyKey));
    final UInt64 epoch = accessors.getCurrentEpoch(processed);
    assertThat(validator.getExitEpoch()).isEqualTo(epoch);
    assertThat(validator.getWithdrawableEpoch())
        .isEqualTo(epoch.plus(config.getMinValidatorWithdrawabilityDelay()));
    assertThat(processed.getBalances().get(0).get()).isEqualTo(amount);
    assertThat(processed.getDepositBalanceToConsume()).isEqualTo(UInt64.ZERO);
  }

  @Test
  void xmssDepositsCreateValidatorsWithTheirVrfRootAndTopUpExistingOnes() {
    final Bytes32 key = dataStructureUtil.randomBytes32();
    final Bytes32 vrfRoot = dataStructureUtil.randomBytes32();
    final Bytes48 depositKey =
        Bytes48.wrap(Bytes.concatenate(PqConstants.XMSS_DEPOSIT_PREFIX, key));
    final PendingDeposit deposit =
        pendingDeposit(
            depositKey,
            config.getMinActivationBalance(),
            Bytes.concatenate(vrfRoot, Bytes.wrap(new byte[64])));

    final BeaconStatePq processed =
        state(UInt64.ZERO)
            .updatedPq(
                mutable -> {
                  epochProcessor.applyPendingDeposits(mutable, deposit);
                  epochProcessor.applyPendingDeposits(mutable, deposit);
                });

    assertThat(processed.getValidators()).hasSize(1);
    final ValidatorPq validator = (ValidatorPq) processed.getValidators().get(0);
    assertThat(validator.getXmssPublicKey()).isEqualTo(key);
    assertThat(validator.getVrfRoot()).isEqualTo(vrfRoot);
    assertThat(validator.getExitEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
    assertThat(validator.getEffectiveBalance()).isEqualTo(config.getMinActivationBalance());
    assertThat(processed.getBalances().get(0).get())
        .isEqualTo(config.getMinActivationBalance().times(2));
    assertThat(processed.getInactivityScores()).hasSize(1);
  }

  @Test
  void hasNoSyncCommitteeUpdates() {
    final BeaconStatePq state = state(UInt64.ZERO);
    assertThat(state.updatedPq(epochProcessor::processSyncCommitteeUpdates)).isEqualTo(state);
  }

  private BeaconStatePq fixtureGenesis() throws IOException {
    return BeaconStatePq.required(
        schemaDefinitions
            .getPqBeaconStateSchema()
            .sszDeserialize(
                Bytes.wrap(
                    Resources.toByteArray(
                        Resources.getResource(
                            "tech/pegasys/teku/spec/datastructures/state/beaconstate/versions/pq/"
                                + "pq_genesis_minimal.ssz")))));
  }

  private BeaconStatePq state(final UInt64 latestBidSlot) {
    return BeaconStatePq.required(schemaDefinitions.getPqBeaconStateSchema().createEmpty())
        .updatedPq(
            mutable -> {
              mutable.setSlot(UInt64.valueOf(64));
              mutable.setLatestExecutionPayloadBid(
                  dataStructureUtil.randomExecutionPayloadBid(latestBidSlot, UInt64.ZERO));
            });
  }

  private ValidatorPreregistration validatorPreregistration(final UInt64 expirySlot) {
    return new ValidatorPreregistration(
        dataStructureUtil.randomBytes32(), Bytes32.ZERO, Bytes32.ZERO, expirySlot);
  }

  private PendingDeposit pendingDeposit(final Bytes48 pubkey, final UInt64 amount) {
    return pendingDeposit(pubkey, amount, Bytes.wrap(new byte[96]));
  }

  private PendingDeposit pendingDeposit(
      final Bytes48 pubkey, final UInt64 amount, final Bytes signature) {
    return schemaDefinitions
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(pubkey),
            SszBytes32.of(Bytes32.ZERO),
            SszUInt64.of(amount),
            new SszSignature(signature),
            SszUInt64.of(UInt64.ZERO));
  }
}
