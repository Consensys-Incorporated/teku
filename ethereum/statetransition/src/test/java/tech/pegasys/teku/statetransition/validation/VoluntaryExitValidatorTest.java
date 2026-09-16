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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.REJECT;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidatorTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final Spec mockSpec = mock(Spec.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private RecentChainData recentChainData;
  private ChainUpdater chainUpdater;
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000_000);

  private VoluntaryExitValidator voluntaryExitValidator;

  @BeforeEach
  void beforeEach() {
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();
    voluntaryExitValidator =
        new VoluntaryExitValidator(mockSpec, recentChainData, timeProvider, gossipValidationHelper);
  }

  @Test
  public void shouldAcceptValidVoluntaryExit() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertValidationResult(exit, ACCEPT);
  }

  @Test
  public void shouldAcceptVoluntaryExitThatWasSeenTooLongAgo() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);
    assertValidationResult(exit, ACCEPT);
    assertValidationResult(exit, IGNORE, "Exit is not the first one");
    timeProvider.advanceTimeBy(Duration.ofHours(2));
    assertValidationResult(exit, ACCEPT);
  }

  @ParameterizedTest
  @EnumSource(value = SpecMilestone.class)
  public void shouldAcceptOwnSignedVoluntaryExitNoMocks(final SpecMilestone specMilestone) {
    final Spec spec = TestSpecFactory.create(specMilestone, Eth2Network.MINIMAL);
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();
    // cannot exit before epoch 64
    advanceChainAndUpdateBestBlock(spec.slotsPerEpoch(UInt64.ZERO) * 64L);
    this.voluntaryExitValidator =
        new VoluntaryExitValidator(spec, recentChainData, timeProvider, gossipValidationHelper);

    final UInt64 currentEpoch = spec.getCurrentEpoch(getBestState());
    assertThat(spec.atEpoch(currentEpoch).getMilestone()).isEqualTo(specMilestone);

    final VoluntaryExit voluntaryExit = new VoluntaryExit(currentEpoch, UInt64.ZERO);
    final BLSSignature exitSignature =
        new LocalSigner(spec, VALIDATOR_KEYS.get(0), SyncAsyncRunner.SYNC_RUNNER)
            .signVoluntaryExit(
                voluntaryExit,
                new ForkInfo(
                    getBestState().getForkInfo().getFork(),
                    getBestState().getGenesisValidatorsRoot()))
            .join();
    final SignedVoluntaryExit exit = new SignedVoluntaryExit(voluntaryExit, exitSignature);

    assertValidationResult(exit, ACCEPT);
  }

  /**
   * EIP-7044 pins the voluntary exit signature domain from Capella onwards, so an exit naming a
   * long-past epoch is legitimately valid - provided it is signed under the domain the *state's*
   * fork computes. Validation must therefore be dispatched on the state's fork, not on the epoch
   * carried inside the message.
   */
  @Test
  public void shouldAcceptPastEpochExitSignedUnderStateForkDomain() {
    final Spec spec = setUpChainWithStaggeredForks();
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);

    final SignedVoluntaryExit signedExit =
        new SignedVoluntaryExit(
            exit, signUnderStateForkDomain(spec, state, exit, VALIDATOR_KEYS.get(0)));

    assertValidationResult(signedExit, ACCEPT);
  }

  /**
   * The mirror of the above: an exit naming epoch 0 signed under the domain resolved from that
   * epoch rather than from the state's fork. Block processing rejects this, so gossip and the local
   * API must reject it too, otherwise it lingers in the pool and breaks block production.
   */
  @Test
  public void shouldRejectPastEpochExitSignedUnderMessageEpochDomain() {
    final Spec spec = setUpChainWithStaggeredForks();
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);

    final BLSSignature messageEpochSignature =
        signUnderMessageEpochDomain(spec, state, exit, VALIDATOR_KEYS.get(0));
    assertThat(messageEpochSignature)
        .isNotEqualTo(signUnderStateForkDomain(spec, state, exit, VALIDATOR_KEYS.get(0)));

    assertValidationResult(
        new SignedVoluntaryExit(exit, messageEpochSignature), REJECT, "Signature is invalid");
  }

  /**
   * Round trip: an exit produced by Teku's own signing path for a past epoch must be accepted by
   * Teku's validation. Signing resolves the domain from the fork, so this holds even though the
   * exit names epoch 0.
   */
  @Test
  public void shouldAcceptPastEpochExitProducedByOwnSigner() {
    final Spec spec = setUpChainWithStaggeredForks();
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);

    final BLSSignature signature =
        new LocalSigner(spec, VALIDATOR_KEYS.get(0), SyncAsyncRunner.SYNC_RUNNER)
            .signVoluntaryExit(
                exit, new ForkInfo(state.getFork(), state.getGenesisValidatorsRoot()))
            .getImmediately();

    assertThat(signature)
        .isEqualTo(signUnderStateForkDomain(spec, state, exit, VALIDATOR_KEYS.get(0)));
    assertValidationResult(new SignedVoluntaryExit(exit, signature), ACCEPT);
  }

  /**
   * Capella at epoch 1, Deneb at 2, Electra at 3, Fulu far in the future. The head therefore sits
   * in Electra while epoch 0 resolves to Bellatrix, so the domain the message epoch selects
   * genuinely differs from the one the state's fork selects.
   */
  private Spec setUpChainWithStaggeredForks() {
    final Spec spec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(1000));
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();
    // a validator cannot exit until SHARD_COMMITTEE_PERIOD (64 epochs on minimal) has elapsed
    advanceChainAndUpdateBestBlock(spec.slotsPerEpoch(UInt64.ZERO) * 65L);
    voluntaryExitValidator =
        new VoluntaryExitValidator(spec, recentChainData, timeProvider, gossipValidationHelper);
    return spec;
  }

  /**
   * Reproduces the pre-fix signing behaviour: milestone and fork both taken from the exit epoch.
   */
  private static BLSSignature signUnderMessageEpochDomain(
      final Spec spec,
      final BeaconState state,
      final VoluntaryExit exit,
      final BLSKeyPair keyPair) {
    final SpecVersion specVersion = spec.atEpoch(exit.getEpoch());
    final Bytes32 domain =
        specVersion
            .beaconStateAccessors()
            .getVoluntaryExitDomain(
                exit.getEpoch(),
                spec.getForkSchedule().getFork(exit.getEpoch()),
                state.getGenesisValidatorsRoot());
    return BLS.sign(
        keyPair.getSecretKey(), specVersion.miscHelpers().computeSigningRoot(exit, domain));
  }

  private static BLSSignature signUnderStateForkDomain(
      final Spec spec,
      final BeaconState state,
      final VoluntaryExit exit,
      final BLSKeyPair keyPair) {
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final Bytes32 domain =
        specVersion
            .beaconStateAccessors()
            .getVoluntaryExitDomain(
                exit.getEpoch(), state.getFork(), state.getGenesisValidatorsRoot());
    return BLS.sign(
        keyPair.getSecretKey(), specVersion.miscHelpers().computeSigningRoot(exit, domain));
  }

  @Test
  public void shouldIgnoreExitsAfterTheFirstForValidator() {
    advanceChainAndUpdateBestBlock(6);

    SignedVoluntaryExit exit1 = dataStructureUtil.randomSignedVoluntaryExit();
    SignedVoluntaryExit exit2 = new SignedVoluntaryExit(exit1.getMessage(), exit1.getSignature());
    SignedVoluntaryExit exit3 = new SignedVoluntaryExit(exit2.getMessage(), exit2.getSignature());

    when(mockSpec.validateVoluntaryExit(eq(getBestState()), any())).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(
            eq(getBestState()), any(), eq(BLSSignatureVerifier.SIMPLE)))
        .thenReturn(true);

    assertValidationResult(exit1, ACCEPT);
    assertValidationResult(exit2, IGNORE, "Exit is not the first one");
    assertValidationResult(exit3, IGNORE, "Exit is not the first one");
  }

  @Test
  public void shouldIgnoreExitWhenEpochIsInFuture() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(gossipValidationHelper.isEpochFromFuture(exit.getMessage().getEpoch())).thenReturn(true);

    assertValidationResult(exit, IGNORE, "future epoch");
  }

  @Test
  public void shouldNotIgnoreExitWhenEpochIsNotInFuture() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(gossipValidationHelper.isEpochFromFuture(exit.getMessage().getEpoch())).thenReturn(false);
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);

    assertValidationResult(exit, ACCEPT);
  }

  @Test
  public void shouldRejectInvalidExit() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit))
        .thenReturn(Optional.of(ExitInvalidReason.exitInitiated()));
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(true);

    assertValidationResult(exit, REJECT, "Validator has already initiated exit");
  }

  @Test
  public void shouldRejectExitWithInvalidSignature() {
    advanceChainAndUpdateBestBlock(6);
    SignedVoluntaryExit exit = dataStructureUtil.randomSignedVoluntaryExit();
    when(mockSpec.validateVoluntaryExit(getBestState(), exit)).thenReturn(Optional.empty());
    when(mockSpec.verifyVoluntaryExitSignature(getBestState(), exit, BLSSignatureVerifier.SIMPLE))
        .thenReturn(false);

    assertValidationResult(exit, REJECT, "Signature is invalid");
  }

  private void assertValidationResult(
      final SignedVoluntaryExit exit, final ValidationResultCode expectedResultCode) {
    assertThat(voluntaryExitValidator.validateForGossip(exit))
        .isCompletedWithValueMatching(result -> result.code() == expectedResultCode);
  }

  private void assertValidationResult(
      final SignedVoluntaryExit exit,
      final ValidationResultCode expectedCode,
      final String description) {
    assertThat(voluntaryExitValidator.validateForGossip(exit))
        .isCompletedWithValueMatching(
            result ->
                result.code() == expectedCode
                    && result.getDescription().orElse("").contains(description));
  }

  private void advanceChainAndUpdateBestBlock(final long slot) {
    chainUpdater.updateBestBlock(chainUpdater.advanceChain(slot));
  }

  private BeaconState getBestState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return safeJoin(stateFuture);
  }
}
