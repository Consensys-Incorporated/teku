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

package tech.pegasys.teku.spec.logic.versions.gloas.forktransition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.WithdrawalPrefixes;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.ValidatorIndexCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class GloasStateUpgradeTest {

  private static final UInt64 GLOAS_EPOCH = UInt64.valueOf(2);
  private static final UInt64 DEPOSIT_AMOUNT = UInt64.valueOf(32_000_000_000L);
  private static final BLSKeyPair NEW_KEY_PAIR_A = BLSTestUtil.randomKeyPair(5736);
  private static final BLSKeyPair NEW_KEY_PAIR_B = BLSTestUtil.randomKeyPair(5737);
  private static final BLSKeyPair EXISTING_VALIDATOR_KEY_PAIR = BLSTestUtil.randomKeyPair(5738);

  private final Spec spec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_EPOCH);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCarryValidatorIndexCacheAcrossUpgrade() {
    final BeaconStateFulu preState =
        BeaconStateFulu.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.FULU, 64, 0)
                .setSlotToStartOfEpoch(GLOAS_EPOCH)
                .build()
                .updated(this::activateAllValidators));
    final ValidatorIndexCache preStateValidatorIndexCache =
        BeaconStateCache.getTransitionCaches(preState).getValidatorIndexCache();
    preStateValidatorIndexCache.getValidatorIndex(
        preState, preState.getValidators().get(0).getPublicKey());

    final GloasStateUpgrade stateUpgrade = createStateUpgrade();
    final BeaconStateGloas postState = BeaconStateGloas.required(stateUpgrade.upgrade(preState));

    assertThat(BeaconStateCache.getTransitionCaches(postState).getValidatorIndexCache())
        .isSameAs(preStateValidatorIndexCache);
  }

  @Test
  void shouldRematerializeEpochParticipationWithUInt8Schemas() {
    final BeaconStateFulu preState =
        BeaconStateFulu.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.FULU, 64, 0)
                .setSlotToStartOfEpoch(GLOAS_EPOCH)
                .build()
                .updated(this::activateAllValidators)
                .updated(
                    state -> {
                      final MutableBeaconStateAltair altairState = (MutableBeaconStateAltair) state;
                      altairState.getPreviousEpochParticipation().set(0, SszByte.asUInt8(1));
                      altairState.getCurrentEpochParticipation().set(0, SszByte.asUInt8(2));
                    }));

    final BeaconStateGloas postState =
        BeaconStateGloas.required(createStateUpgrade().upgrade(preState));

    assertThat(postState.getPreviousEpochParticipation()).isInstanceOf(SszByteList.class);
    assertThat(postState.getCurrentEpochParticipation()).isInstanceOf(SszByteList.class);
    assertThat(postState.getPreviousEpochParticipation().get(0).getSchema())
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
    assertThat(postState.getCurrentEpochParticipation().get(0).getSchema())
        .isEqualTo(SszPrimitiveSchemas.UINT8_SCHEMA);
    assertThatSszData(postState.getPreviousEpochParticipation())
        .isEqualByGettersTo(
            postState
                .getPreviousEpochParticipation()
                .getSchema()
                .sszDeserialize(postState.getPreviousEpochParticipation().sszSerialize()));
    assertThatSszData(postState.getCurrentEpochParticipation())
        .isEqualByGettersTo(
            postState
                .getCurrentEpochParticipation()
                .getSchema()
                .sszDeserialize(postState.getCurrentEpochParticipation().sszSerialize()));
  }

  @Test
  void shouldCarryBoundedHistoricalSummariesAcrossUpgrade() {
    final BeaconStateFulu initialState =
        BeaconStateFulu.required(
            dataStructureUtil
                .stateBuilder(SpecMilestone.FULU, 64, 1)
                .setSlotToStartOfEpoch(GLOAS_EPOCH)
                .build()
                .updated(this::activateAllValidators));
    final BeaconStateFulu preState =
        BeaconStateFulu.required(
            initialState.updated(
                state ->
                    ((MutableBeaconStateCapella) state)
                        .setHistoricalSummaries(
                            initialState
                                .getHistoricalSummaries()
                                .getSchema()
                                .createFromElements(
                                    List.of(dataStructureUtil.randomHistoricalSummary())))));

    final BeaconStateGloas postState =
        BeaconStateGloas.required(createStateUpgrade().upgrade(preState));

    assertThat(postState.getHistoricalSummaries().size()).isEqualTo(1);
    assertThat(postState.getHistoricalSummaries().sszSerialize())
        .isEqualTo(preState.getHistoricalSummaries().sszSerialize());
    assertThat(postState.getHistoricalSummaries().getSchema().getMaxLength())
        .isEqualTo(preState.getHistoricalSummaries().getSchema().getMaxLength());
  }

  @Test
  void shouldDropRepeatedBuilderDepositsWithoutRescanningQueuedInvalidEth1Deposits() {
    final BLSPublicKey repeatedPubkey = dataStructureUtil.randomPublicKey();
    final int samePubkeyEth1DepositCount = 32;
    final int repeatedBuilderDepositCount = 64;
    final List<PendingDeposit> pendingDeposits = new ArrayList<>();

    // Retained invalid ETH1 deposits for the same pubkey are the queue prefix the old
    // implementation rescanned for every repeated builder deposit below.
    final List<PendingDeposit> samePubkeyEth1Deposits = new ArrayList<>();
    for (int i = 0; i < samePubkeyEth1DepositCount; i++) {
      samePubkeyEth1Deposits.add(eth1PendingDepositWithInvalidSignature(repeatedPubkey));
    }
    pendingDeposits.addAll(samePubkeyEth1Deposits);

    // Repeated invalid builder deposits for the same pubkey are dropped, but should not trigger a
    // full rescan and re-verification of the retained ETH1 deposits each time.
    for (int i = 0; i < repeatedBuilderDepositCount; i++) {
      pendingDeposits.add(builderPendingDepositWithInvalidSignature(repeatedPubkey));
    }

    final BeaconStateFulu preState = createPreStateWithPendingDeposits(pendingDeposits);

    final CountingSignatureMiscHelpers miscHelpers = createCountingSignatureMiscHelpers(false);
    final BeaconStateGloas postState =
        BeaconStateGloas.required(createStateUpgrade(miscHelpers).upgrade(preState));

    assertThat(postState.getBuilders().asList()).isEmpty();
    assertThat(postState.getPendingDeposits().asList()).isEqualTo(samePubkeyEth1Deposits);
    assertThat(miscHelpers.getSignatureVerificationCount())
        .isLessThanOrEqualTo(samePubkeyEth1DepositCount + repeatedBuilderDepositCount);
  }

  @Test
  void shouldNotVerifyValidatorDepositsForPubkeysWithoutBuilderDeposits() {
    final int validatorDepositCount = 16;
    final List<PendingDeposit> validatorDeposits = new ArrayList<>();
    for (int i = 0; i < validatorDepositCount; i++) {
      validatorDeposits.add(
          signedPendingDeposit(
              dataStructureUtil.randomKeyPair(),
              dataStructureUtil.randomEth1WithdrawalCredentials()));
    }
    final List<PendingDeposit> pendingDeposits = new ArrayList<>(validatorDeposits);
    final BLSKeyPair builderKeyPair = dataStructureUtil.randomKeyPair();
    pendingDeposits.add(signedPendingDeposit(builderKeyPair, randomBuilderWithdrawalCredentials()));

    final CountingSignatureMiscHelpers miscHelpers = createCountingSignatureMiscHelpers(true);
    final BeaconStateGloas postState =
        BeaconStateGloas.required(
            createStateUpgrade(miscHelpers)
                .upgrade(createPreStateWithPendingDeposits(pendingDeposits)));

    assertThat(postState.getBuilders().asList())
        .extracting(Builder::getPublicKey)
        .containsExactly(builderKeyPair.getPublicKey());
    assertThat(postState.getPendingDeposits().asList()).isEqualTo(validatorDeposits);
    // Only the builder deposit's own signature is checked
    assertThat(miscHelpers.getSignatureVerificationCount()).isEqualTo(1);
  }

  @Test
  void shouldKeepBuilderDepositsQueuedForPendingValidatorVerifyingEachDepositOnce() {
    final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
    final List<PendingDeposit> pendingDeposits = new ArrayList<>();
    pendingDeposits.add(
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), dataStructureUtil.randomEth1WithdrawalCredentials()));
    pendingDeposits.add(
        signedPendingDeposit(keyPair, dataStructureUtil.randomEth1WithdrawalCredentials()));
    for (int i = 0; i < 3; i++) {
      pendingDeposits.add(
          pendingDepositWithEmptySignature(
              keyPair.getPublicKey(), randomBuilderWithdrawalCredentials()));
    }

    final CountingSignatureMiscHelpers miscHelpers = createCountingSignatureMiscHelpers(true);
    final BeaconStateGloas postState =
        BeaconStateGloas.required(
            createStateUpgrade(miscHelpers)
                .upgrade(createPreStateWithPendingDeposits(pendingDeposits)));

    assertThat(postState.getBuilders().asList()).isEmpty();
    // The pending validator claims the pubkey, so its invalid builder deposits stay queued
    assertThat(postState.getPendingDeposits().asList()).isEqualTo(pendingDeposits);
    // Each builder deposit's own signature, plus both validator deposits once, on the first
    // builder deposit
    assertThat(miscHelpers.getSignatureVerificationCount()).isEqualTo(5);
  }

  @Test
  void shouldOnlyConsiderValidatorDepositsQueuedBeforeBuilderDeposit() {
    final BLSKeyPair keyPair = dataStructureUtil.randomKeyPair();
    final PendingDeposit invalidValidatorDeposit =
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), dataStructureUtil.randomEth1WithdrawalCredentials());
    final PendingDeposit droppedBuilderDeposit =
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), randomBuilderWithdrawalCredentials());
    final PendingDeposit validValidatorDeposit =
        signedPendingDeposit(keyPair, dataStructureUtil.randomEth1WithdrawalCredentials());
    final PendingDeposit retainedBuilderDeposit1 =
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), randomBuilderWithdrawalCredentials());
    final PendingDeposit retainedBuilderDeposit2 =
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), randomBuilderWithdrawalCredentials());
    final PendingDeposit laterValidatorDeposit =
        pendingDepositWithEmptySignature(
            keyPair.getPublicKey(), dataStructureUtil.randomEth1WithdrawalCredentials());

    final CountingSignatureMiscHelpers miscHelpers = createCountingSignatureMiscHelpers(true);
    final BeaconStateGloas postState =
        BeaconStateGloas.required(
            createStateUpgrade(miscHelpers)
                .upgrade(
                    createPreStateWithPendingDeposits(
                        List.of(
                            invalidValidatorDeposit,
                            droppedBuilderDeposit,
                            validValidatorDeposit,
                            retainedBuilderDeposit1,
                            retainedBuilderDeposit2,
                            laterValidatorDeposit))));

    assertThat(postState.getBuilders().asList()).isEmpty();
    assertThat(postState.getPendingDeposits().asList())
        .containsExactly(
            invalidValidatorDeposit,
            validValidatorDeposit,
            retainedBuilderDeposit1,
            retainedBuilderDeposit2,
            laterValidatorDeposit);
    // Each builder deposit's own signature, the invalid validator deposit on the first builder
    // deposit, then only the valid validator deposit on the next; the invalid one is not
    // re-verified, and the later validator deposit for the already pending pubkey is never verified
    assertThat(miscHelpers.getSignatureVerificationCount()).isEqualTo(5);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource({"singleNewKeyDepositSequences", "randomMixedDepositSequences"})
  void shouldOnboardBuildersAsSpecifiedForDepositSequence(final List<DepositCase> depositCases) {
    // Give an existing validator a known key so its deposits can carry valid signatures
    final BeaconStateFulu baseState =
        BeaconStateFulu.required(
            createPreState()
                .updated(
                    state ->
                        state
                            .getValidators()
                            .update(
                                0,
                                validator ->
                                    new Validator(
                                        EXISTING_VALIDATOR_KEY_PAIR.getPublicKey(),
                                        validator.getWithdrawalCredentials(),
                                        validator.getEffectiveBalance(),
                                        validator.isSlashed(),
                                        validator.getActivationEligibilityEpoch(),
                                        validator.getActivationEpoch(),
                                        validator.getExitEpoch(),
                                        validator.getWithdrawableEpoch()))));
    final List<OnboardingDeposit> deposits = new ArrayList<>();
    for (int position = 0; position < depositCases.size(); position++) {
      final DepositCase depositCase = depositCases.get(position);
      deposits.add(
          new OnboardingDeposit(depositCase, createOnboardingDeposit(depositCase, position)));
    }

    final CountingSignatureMiscHelpers miscHelpers = createCountingSignatureMiscHelpers(true);
    final BeaconStateGloas postState =
        BeaconStateGloas.required(
            createStateUpgrade(miscHelpers)
                .upgrade(
                    withPendingDeposits(
                        baseState, deposits.stream().map(OnboardingDeposit::deposit).toList())));

    final List<PendingDeposit> expectedPendingDeposits = new ArrayList<>();
    final List<Builder> expectedBuilders = new ArrayList<>();
    applySpecOnboarding(deposits, expectedPendingDeposits, expectedBuilders);
    assertThat(postState.getPendingDeposits().asList())
        .containsExactlyElementsOf(expectedPendingDeposits);
    assertThat(postState.getBuilders().asList()).containsExactlyElementsOf(expectedBuilders);
    // Each deposit signature is verified at most once
    assertThat(miscHelpers.getSignatureVerificationCount()).isLessThanOrEqualTo(deposits.size());
  }

  /** Every sequence of up to 4 validator and builder deposits for one new pubkey. */
  static Stream<Arguments> singleNewKeyDepositSequences() {
    final List<DepositCase> kinds = new ArrayList<>();
    for (final boolean builderCredentials : List.of(false, true)) {
      for (final boolean validSignature : List.of(true, false)) {
        kinds.add(new DepositCase(DepositOwner.NEW_KEY_A, builderCredentials, validSignature));
      }
    }
    List<List<DepositCase>> sequences = List.of(List.of());
    final List<List<DepositCase>> allSequences = new ArrayList<>();
    for (int length = 1; length <= 4; length++) {
      final List<List<DepositCase>> extended = new ArrayList<>();
      for (final List<DepositCase> sequence : sequences) {
        for (final DepositCase kind : kinds) {
          final List<DepositCase> next = new ArrayList<>(sequence);
          next.add(kind);
          extended.add(next);
        }
      }
      allSequences.addAll(extended);
      sequences = extended;
    }
    return allSequences.stream().map(Arguments::of);
  }

  /**
   * Interleaved deposits for two new pubkeys and an existing validator, so routing for one pubkey
   * is checked not to leak into another.
   */
  static Stream<Arguments> randomMixedDepositSequences() {
    final Random random = new Random(5736);
    final DepositOwner[] owners = DepositOwner.values();
    final List<List<DepositCase>> sequences = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      final int length = 1 + random.nextInt(10);
      final List<DepositCase> sequence = new ArrayList<>();
      for (int position = 0; position < length; position++) {
        final DepositOwner owner = owners[random.nextInt(owners.length)];
        sequence.add(new DepositCase(owner, random.nextBoolean(), random.nextBoolean()));
      }
      sequences.add(sequence);
    }
    return sequences.stream().map(Arguments::of);
  }

  /**
   * A literal port of onboard_builders_from_pending_deposits, using the signature validity each
   * deposit was built with rather than verifying it.
   */
  private void applySpecOnboarding(
      final List<OnboardingDeposit> deposits,
      final List<PendingDeposit> pendingDeposits,
      final List<Builder> builders) {
    final List<OnboardingDeposit> retained = new ArrayList<>();
    for (final OnboardingDeposit onboardingDeposit : deposits) {
      final DepositCase depositCase = onboardingDeposit.depositCase();
      final PendingDeposit deposit = onboardingDeposit.deposit();
      final BLSPublicKey pubkey = deposit.getPublicKey();
      if (!depositCase.builderCredentials()) {
        retained.add(onboardingDeposit);
        continue;
      }
      final OptionalInt builderIndex =
          IntStream.range(0, builders.size())
              .filter(i -> builders.get(i).getPublicKey().equals(pubkey))
              .findFirst();
      if (builderIndex.isPresent()) {
        final Builder builder = builders.get(builderIndex.getAsInt());
        builders.set(
            builderIndex.getAsInt(),
            builder.copyWithNewBalance(builder.getBalance().plus(deposit.getAmount())));
        continue;
      }
      if (!depositCase.validSignature()) {
        final boolean isPendingValidator =
            retained.stream()
                .anyMatch(
                    queued ->
                        queued.deposit().getPublicKey().equals(pubkey)
                            && queued.depositCase().validSignature());
        if (depositCase.owner() == DepositOwner.EXISTING_VALIDATOR || isPendingValidator) {
          retained.add(onboardingDeposit);
        }
        continue;
      }
      builders.add(
          new Builder(
              pubkey,
              PAYLOAD_BUILDER_VERSION,
              Eth1Address.fromBytes(deposit.getWithdrawalCredentials().slice(12)),
              deposit.getAmount(),
              spec.computeEpochAtSlot(deposit.getSlot()),
              FAR_FUTURE_EPOCH));
    }
    retained.forEach(onboardingDeposit -> pendingDeposits.add(onboardingDeposit.deposit()));
  }

  /**
   * Gives each position a distinct amount, slot epoch and execution address so top-ups and the
   * creating deposit are distinguishable, and varies the non-builder prefix and the builder
   * credential padding since only the first byte decides routing.
   */
  private PendingDeposit createOnboardingDeposit(
      final DepositCase depositCase, final int position) {
    final Bytes prefix =
        depositCase.builderCredentials()
            ? WithdrawalPrefixes.BUILDER_WITHDRAWAL_PREFIX
            : Bytes.of(position % 3);
    final Bytes padding =
        filledBytes(11, depositCase.builderCredentials() && position % 2 == 1 ? 0x11 : 0);
    final Bytes32 withdrawalCredentials =
        Bytes32.wrap(Bytes.concatenate(prefix, padding, filledBytes(20, position + 1)));
    final UInt64 amount = UInt64.valueOf((position + 1) * 1_000_000_000L);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(position));

    final BLSKeyPair signingKeyPair =
        switch (depositCase.owner()) {
          case NEW_KEY_A -> NEW_KEY_PAIR_A;
          case NEW_KEY_B -> NEW_KEY_PAIR_B;
          case EXISTING_VALIDATOR -> EXISTING_VALIDATOR_KEY_PAIR;
        };
    final BLSPublicKey pubkey = signingKeyPair.getPublicKey();
    // An invalid signature is a well-formed signature over the wrong amount
    final UInt64 signedAmount = depositCase.validSignature() ? amount : amount.plus(1);
    final MiscHelpersGloas miscHelpers =
        MiscHelpersGloas.required(spec.atEpoch(GLOAS_EPOCH).miscHelpers());
    final BLSSignature signature =
        BLS.sign(
            signingKeyPair.getSecretKey(),
            miscHelpers.computeDepositSigningRoot(pubkey, withdrawalCredentials, signedAmount));
    return pendingDeposit(pubkey, withdrawalCredentials, amount, signature, slot);
  }

  private static Bytes filledBytes(final int size, final int value) {
    final byte[] bytes = new byte[size];
    Arrays.fill(bytes, (byte) value);
    return Bytes.wrap(bytes);
  }

  private enum DepositOwner {
    NEW_KEY_A,
    NEW_KEY_B,
    EXISTING_VALIDATOR
  }

  private record DepositCase(
      DepositOwner owner, boolean builderCredentials, boolean validSignature) {
    @Override
    public String toString() {
      return owner
          + "/"
          + (builderCredentials ? "builder" : "validator")
          + (validSignature ? "/valid" : "/invalid");
    }
  }

  private record OnboardingDeposit(DepositCase depositCase, PendingDeposit deposit) {}

  private BeaconStateFulu createPreStateWithPendingDeposits(
      final List<PendingDeposit> pendingDeposits) {
    return withPendingDeposits(createPreState(), pendingDeposits);
  }

  private BeaconStateFulu createPreState() {
    return BeaconStateFulu.required(
        dataStructureUtil
            .stateBuilder(SpecMilestone.FULU, 64, 0)
            .setSlotToStartOfEpoch(GLOAS_EPOCH)
            .build()
            .updated(this::activateAllValidators));
  }

  private BeaconStateFulu withPendingDeposits(
      final BeaconStateFulu baseState, final List<PendingDeposit> pendingDeposits) {
    final SszList<PendingDeposit> pendingDepositsList =
        baseState.getPendingDeposits().getSchema().createFromElements(pendingDeposits);
    return BeaconStateFulu.required(
        baseState.updated(
            state -> ((MutableBeaconStateElectra) state).setPendingDeposits(pendingDepositsList)));
  }

  private PendingDeposit eth1PendingDepositWithInvalidSignature(final BLSPublicKey pubkey) {
    return pendingDepositWithInvalidSignature(
        pubkey, dataStructureUtil.randomEth1WithdrawalCredentials());
  }

  private PendingDeposit builderPendingDepositWithInvalidSignature(final BLSPublicKey pubkey) {
    return pendingDepositWithInvalidSignature(pubkey, randomBuilderWithdrawalCredentials());
  }

  private Bytes32 randomBuilderWithdrawalCredentials() {
    return Bytes32.wrap(
        Bytes.concatenate(
            Bytes.of(WithdrawalPrefixes.BUILDER_WITHDRAWAL_BYTE),
            dataStructureUtil.randomBytes32().slice(1)));
  }

  private PendingDeposit pendingDepositWithInvalidSignature(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials) {
    return pendingDeposit(
        pubkey,
        withdrawalCredentials,
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomSignature());
  }

  private PendingDeposit pendingDepositWithEmptySignature(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials) {
    return pendingDeposit(pubkey, withdrawalCredentials, DEPOSIT_AMOUNT, BLSSignature.empty());
  }

  private PendingDeposit signedPendingDeposit(
      final BLSKeyPair keyPair, final Bytes32 withdrawalCredentials) {
    final BLSPublicKey pubkey = keyPair.getPublicKey();
    final MiscHelpersGloas miscHelpers =
        MiscHelpersGloas.required(spec.atEpoch(GLOAS_EPOCH).miscHelpers());
    final BLSSignature signature =
        BLS.sign(
            keyPair.getSecretKey(),
            miscHelpers.computeDepositSigningRoot(pubkey, withdrawalCredentials, DEPOSIT_AMOUNT));
    return pendingDeposit(pubkey, withdrawalCredentials, DEPOSIT_AMOUNT, signature);
  }

  private PendingDeposit pendingDeposit(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    return pendingDeposit(pubkey, withdrawalCredentials, amount, signature, UInt64.ZERO);
  }

  private PendingDeposit pendingDeposit(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final UInt64 slot) {
    return SchemaDefinitionsElectra.required(spec.atEpoch(GLOAS_EPOCH).getSchemaDefinitions())
        .getPendingDepositSchema()
        .create(
            new SszPublicKey(pubkey),
            SszBytes32.of(withdrawalCredentials),
            SszUInt64.of(amount),
            new SszSignature(signature),
            SszUInt64.of(slot));
  }

  private GloasStateUpgrade createStateUpgrade() {
    final SpecVersion gloasSpecVersion = spec.atEpoch(GLOAS_EPOCH);
    return createStateUpgrade(MiscHelpersGloas.required(gloasSpecVersion.miscHelpers()));
  }

  private GloasStateUpgrade createStateUpgrade(final MiscHelpersGloas miscHelpers) {
    final SpecVersion gloasSpecVersion = spec.atEpoch(GLOAS_EPOCH);
    return new GloasStateUpgrade(
        SpecConfigGloas.required(gloasSpecVersion.getConfig()),
        SchemaDefinitionsGloas.required(gloasSpecVersion.getSchemaDefinitions()),
        BeaconStateAccessorsGloas.required(gloasSpecVersion.beaconStateAccessors()),
        PredicatesGloas.required(gloasSpecVersion.predicates()),
        BeaconStateMutatorsGloas.required(gloasSpecVersion.beaconStateMutators()),
        miscHelpers,
        gloasSpecVersion.getValidatorsUtil());
  }

  private CountingSignatureMiscHelpers createCountingSignatureMiscHelpers(
      final boolean verifySignatures) {
    final SpecVersion gloasSpecVersion = spec.atEpoch(GLOAS_EPOCH);
    return new CountingSignatureMiscHelpers(
        SpecConfigGloas.required(gloasSpecVersion.getConfig()),
        PredicatesGloas.required(gloasSpecVersion.predicates()),
        SchemaDefinitionsGloas.required(gloasSpecVersion.getSchemaDefinitions()),
        verifySignatures);
  }

  private void activateAllValidators(final MutableBeaconState state) {
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

  /**
   * Counts deposit signature verifications, either rejecting every signature or delegating to the
   * real check.
   */
  private static class CountingSignatureMiscHelpers extends MiscHelpersGloas {
    private final boolean verifySignatures;
    private int signatureVerificationCount = 0;

    private CountingSignatureMiscHelpers(
        final SpecConfigGloas specConfig,
        final PredicatesGloas predicates,
        final SchemaDefinitionsGloas schemaDefinitions,
        final boolean verifySignatures) {
      super(specConfig, predicates, schemaDefinitions);
      this.verifySignatures = verifySignatures;
    }

    @Override
    public boolean isValidDepositSignature(
        final BLSPublicKey pubkey,
        final Bytes32 withdrawalCredentials,
        final UInt64 amount,
        final BLSSignature signature) {
      signatureVerificationCount++;
      return verifySignatures
          && super.isValidDepositSignature(pubkey, withdrawalCredentials, amount, signature);
    }

    private int getSignatureVerificationCount() {
      return signatureVerificationCount;
    }
  }
}
