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

package tech.pegasys.teku.spec.logic.versions.pq.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.BuilderPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ValidatorPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.VoluntaryExitRequest;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionRequestsProcessorPqTest {

  private static final UInt64 SLOT = UInt64.valueOf(1000);
  private static final Bytes20 ADDRESS =
      Bytes20.fromHexString("0x1111111111111111111111111111111111111111");

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfigPq config =
      SpecConfigPq.required(spec.forMilestone(SpecMilestone.PQ).getConfig());
  private final SchemaDefinitionsPq schemaDefinitions =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions());
  private final PredicatesPq predicates = new PredicatesPq(config);
  private final MiscHelpersPq miscHelpers =
      new MiscHelpersPq(config, predicates, schemaDefinitions);
  private final BeaconStateAccessorsPq accessors =
      new BeaconStateAccessorsPq(config, schemaDefinitions, predicates, miscHelpers);
  private final BeaconStateMutatorsPq mutators =
      new BeaconStateMutatorsPq(config, miscHelpers, accessors, schemaDefinitions);
  private final ExecutionRequestsProcessorPq processor =
      new ExecutionRequestsProcessorPq(
          schemaDefinitions,
          miscHelpers,
          config,
          predicates,
          new ValidatorsUtilGloas(config, miscHelpers, accessors),
          mutators,
          accessors);

  private final Bytes32 validatorKey = dataStructureUtil.randomBytes32();

  @Test
  void depositMatchingAPreregistrationConsumesIt() {
    final Bytes32 key = dataStructureUtil.randomBytes32();
    final Bytes32 credentials = credentials(WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_PREFIX);
    final Bytes32 vrfRoot = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        state()
            .updatedPq(
                mutable ->
                    mutable
                        .getValidatorPreregistrations()
                        .append(
                            new ValidatorPreregistration(
                                key, credentials, vrfRoot, SLOT.plus(100))));

    final BeaconStatePq processed =
        state.updatedPq(
            mutable ->
                processor.processDepositRequests(
                    mutable, List.of(deposit(key, credentials, vrfRoot))));

    assertThat(processed.getValidatorPreregistrations()).isEmpty();
    assertThat(processed.getPendingDeposits()).hasSize(1);
    assertThat(processed.getPendingDeposits().get(0).getPubkeyBytes()).isEqualTo(depositKey(key));
    assertThat(processed.getPendingDeposits().get(0).getSignatureBytes().slice(0, 32))
        .isEqualTo(vrfRoot);
  }

  @Test
  void depositNotMatchingAPreregistrationIsDropped() {
    final Bytes32 key = dataStructureUtil.randomBytes32();
    final Bytes32 credentials = credentials(WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_PREFIX);
    final BeaconStatePq state =
        state()
            .updatedPq(
                mutable ->
                    mutable
                        .getValidatorPreregistrations()
                        .append(
                            new ValidatorPreregistration(
                                key, credentials, Bytes32.ZERO, SLOT.plus(100))));

    final BeaconStatePq processed =
        state.updatedPq(
            mutable ->
                processor.processDepositRequests(
                    mutable,
                    List.of(deposit(key, credentials, dataStructureUtil.randomBytes32()))));

    assertThat(processed.getPendingDeposits()).isEmpty();
    assertThat(processed.getValidatorPreregistrations()).hasSize(1);
  }

  @Test
  void withdrawalRequestsNameXmssKeysAndIgnoreBlsKeys() {
    final BeaconStatePq state = state();
    final BLSPublicKey blsKey = dataStructureUtil.randomPublicKey();

    final BeaconStatePq processed =
        state.updatedPq(
            mutable ->
                processor.processWithdrawalRequests(
                    mutable,
                    List.of(
                        schemaDefinitions
                            .getWithdrawalRequestSchema()
                            .create(ADDRESS, blsKey, UInt64.ZERO),
                        schemaDefinitions
                            .getWithdrawalRequestSchema()
                            .create(
                                ADDRESS,
                                BLSPublicKey.fromBytesCompressed(depositKey(validatorKey)),
                                UInt64.ZERO)),
                    mutators.createValidatorExitContextSupplier(mutable)));

    // The full exit request names the validator's XMSS key
    assertThat(processed.getValidators().get(0).getExitEpoch()).isNotEqualTo(FAR_FUTURE_EPOCH);
  }

  @Test
  void validatorPreregistrationsAreAddedOnceAndReplaceExpiredRecords() {
    final Bytes32 key = dataStructureUtil.randomBytes32();
    final ValidatorPreregistrationRequest request =
        new ValidatorPreregistrationRequest(key, Bytes32.ZERO, Bytes32.ZERO);
    final BeaconStatePq expired =
        state()
            .updatedPq(
                mutable ->
                    mutable
                        .getValidatorPreregistrations()
                        .append(
                            new ValidatorPreregistration(
                                key, Bytes32.ZERO, Bytes32.ZERO, UInt64.valueOf(5))));

    final BeaconStatePq processed =
        expired.updatedPq(
            mutable -> {
              processor.processValidatorPreregistrationRequests(mutable, List.of(request));
              // An active binding makes a second request a no-op
              processor.processValidatorPreregistrationRequests(mutable, List.of(request));
              // An existing validator's key cannot be preregistered
              processor.processValidatorPreregistrationRequests(
                  mutable,
                  List.of(
                      new ValidatorPreregistrationRequest(
                          validatorKey, Bytes32.ZERO, Bytes32.ZERO)));
            });

    assertThat(processed.getValidatorPreregistrations()).hasSize(1);
    assertThat(processed.getValidatorPreregistrations().get(0).getExpirySlot())
        .isEqualTo(SLOT.plus(PqConstants.PREREGISTRATION_EXPIRY_SLOTS));
  }

  @Test
  void builderDepositsCreateXmssBuildersAndLegacyOnesExitAtOnce() {
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final BLSPublicKey legacyKey = dataStructureUtil.randomPublicKey();
    final Bytes32 credentials = credentials(WithdrawalPrefixes.BUILDER_WITHDRAWAL_PREFIX);

    final BeaconStatePq processed =
        state()
            .updatedPq(
                mutable -> {
                  processor.processBuilderPreregistrationRequests(
                      mutable, List.of(new BuilderPreregistrationRequest(builderKey, credentials)));
                  processor.processBuilderDepositRequests(
                      mutable,
                      List.of(
                          builderDeposit(
                              BLSPublicKey.fromBytesCompressed(depositKey(builderKey)),
                              credentials),
                          builderDeposit(legacyKey, credentials)));
                });

    assertThat(processed.getBuilderPreregistrations()).isEmpty();
    assertThat(processed.getBuilders()).hasSize(2);
    final Builder builder = processed.getBuilders().get(0);
    assertThat(((BuilderPq) builder).getXmssPublicKey()).isEqualTo(builderKey);
    assertThat(builder.getWithdrawableEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
    final Builder legacyBuilder = processed.getBuilders().get(1);
    assertThat(((BuilderPq) legacyBuilder).getXmssPublicKey())
        .isEqualTo(miscHelpers.computeLegacyXmssPubkey(legacyKey.toBytesCompressed()));
    assertThat(legacyBuilder.getWithdrawableEpoch()).isNotEqualTo(FAR_FUTURE_EPOCH);
  }

  @Test
  void builderExitRequestsNameXmssKeys() {
    final Bytes32 builderKey = dataStructureUtil.randomBytes32();
    final BeaconStatePq state =
        state()
            .updatedPq(
                mutable -> {
                  mutable
                      .getBuilders()
                      .append(
                          new BuilderPq(
                              builderKey,
                              0,
                              Eth1Address.fromBytes(ADDRESS.getWrappedBytes()),
                              UInt64.valueOf(1_000_000_000L),
                              UInt64.ZERO,
                              FAR_FUTURE_EPOCH));
                });

    final BeaconStatePq processed =
        state.updatedPq(
            mutable ->
                processor.processBuilderExitRequests(
                    mutable,
                    List.of(
                        schemaDefinitions
                            .getBuilderExitRequestSchema()
                            .create(
                                ADDRESS,
                                BLSPublicKey.fromBytesCompressed(depositKey(builderKey))))));

    assertThat(processed.getBuilders().get(0).getWithdrawableEpoch())
        .isNotEqualTo(FAR_FUTURE_EPOCH);
  }

  @Test
  void voluntaryExitRequestsExitActiveValidators() {
    final BeaconStatePq processed =
        state()
            .updatedPq(
                mutable ->
                    processor.processVoluntaryExitRequests(
                        mutable,
                        List.of(
                            new VoluntaryExitRequest(validatorKey),
                            new VoluntaryExitRequest(dataStructureUtil.randomBytes32()))));

    assertThat(processed.getValidators().get(0).getExitEpoch()).isNotEqualTo(FAR_FUTURE_EPOCH);
  }

  @Test
  void voluntaryExitRequestsWaitForTheShardCommitteePeriod() {
    final BeaconStatePq processed =
        state()
            .updatedPq(
                mutable -> {
                  mutable.setSlot(UInt64.valueOf(8));
                  processor.processVoluntaryExitRequests(
                      mutable, List.of(new VoluntaryExitRequest(validatorKey)));
                });

    assertThat(processed.getValidators().get(0).getExitEpoch()).isEqualTo(FAR_FUTURE_EPOCH);
  }

  /** One active validator keyed by {@link #validatorKey}, at slot {@link #SLOT}. */
  private BeaconStatePq state() {
    final ValidatorPq validator =
        new ValidatorPq(
            validatorKey,
            Bytes32.ZERO,
            credentials(WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_PREFIX),
            config.getMinActivationBalance(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);
    return BeaconStatePq.required(schemaDefinitions.getPqBeaconStateSchema().createEmpty())
        .updatedPq(
            mutable -> {
              mutable.setSlot(SLOT);
              mutable.getValidators().append(validator);
              mutable.getBalances().appendElement(config.getMinActivationBalance());
              mutable.setFinalizedCheckpoint(new Checkpoint(UInt64.valueOf(10), Bytes32.ZERO));
              mutable.setLatestExecutionPayloadBid(
                  dataStructureUtil.randomExecutionPayloadBid(SLOT.minus(1), UInt64.ZERO));
            });
  }

  private DepositRequest deposit(
      final Bytes32 key, final Bytes32 credentials, final Bytes32 vrfRoot) {
    return schemaDefinitions
        .getDepositRequestSchema()
        .create(
            BLSPublicKey.fromBytesCompressed(depositKey(key)),
            credentials,
            config.getMinActivationBalance(),
            BLSSignature.fromBytesCompressed(Bytes.concatenate(vrfRoot, Bytes.wrap(new byte[64]))),
            UInt64.ZERO);
  }

  private BuilderDepositRequest builderDeposit(
      final BLSPublicKey pubkey, final Bytes32 credentials) {
    return schemaDefinitions
        .getBuilderDepositRequestSchema()
        .create(
            pubkey,
            credentials,
            UInt64.valueOf(1_000_000_000L),
            BLSSignature.fromBytesCompressed(Bytes.wrap(new byte[96])));
  }

  private static Bytes48 depositKey(final Bytes32 xmssKey) {
    return Bytes48.wrap(Bytes.concatenate(PqConstants.XMSS_DEPOSIT_PREFIX, xmssKey));
  }

  private static Bytes32 credentials(final Bytes prefix) {
    return Bytes32.wrap(
        Bytes.concatenate(prefix, Bytes.wrap(new byte[11]), ADDRESS.getWrappedBytes()));
  }
}
