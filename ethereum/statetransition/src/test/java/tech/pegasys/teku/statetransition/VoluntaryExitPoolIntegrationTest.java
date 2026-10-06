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

package tech.pegasys.teku.statetransition;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.SyncAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.signatures.LocalSigner;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.statetransition.validation.VoluntaryExitValidator;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * End-to-end cover for the reported failure mode: an exit accepted by the local API path but
 * rejected by block processing lingers in the pool forever, because the pool only evicts entries
 * whose block-inclusion validation fails - and that shares its logic with gossip validation. Every
 * subsequent block proposal then fails its state transition.
 *
 * <p>Uses the real {@link VoluntaryExitValidator} and a real {@link MappedOperationPool} rather
 * than mocks, so it exercises the validator/pool/block-selection chain the way production does.
 */
class VoluntaryExitPoolIntegrationTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 25);

  // Capella at epoch 1, Deneb at 2, Electra at 3, Fulu far in the future. The head sits in Electra
  // while epoch 0 resolves to Bellatrix, so the two dispatch keys select different milestones.
  private final Spec spec =
      TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
          UInt64.ONE, UInt64.valueOf(2), UInt64.valueOf(3), UInt64.valueOf(1000));
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1_000_000_000);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private RecentChainData recentChainData;
  private OperationPool<SignedVoluntaryExit> pool;

  @BeforeEach
  void setUp() {
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
    final ChainUpdater chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();
    // a validator cannot exit until SHARD_COMMITTEE_PERIOD (64 epochs on minimal) has elapsed
    chainUpdater.updateBestBlock(chainUpdater.advanceChain(spec.slotsPerEpoch(UInt64.ZERO) * 65L));

    final Function<UInt64, SszListSchema<SignedVoluntaryExit, ?>> schemaSupplier =
        slot ->
            spec.atSlot(slot)
                .getSchemaDefinitions()
                .getBeaconBlockBodySchema()
                .getVoluntaryExitsSchema();
    pool =
        new MappedOperationPool<>(
            "VoluntaryExitPool",
            metricsSystem,
            schemaSupplier,
            new VoluntaryExitValidator(
                spec,
                recentChainData,
                timeProvider,
                new GossipValidationHelper(spec, recentChainData, metricsSystem)),
            asyncRunner,
            timeProvider);
  }

  @Test
  void shouldNotAdmitPastEpochExitSignedUnderMessageEpochDomain() {
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);
    final SignedVoluntaryExit signedExit =
        new SignedVoluntaryExit(
            exit, signUnderMessageEpochDomain(state, exit, VALIDATOR_KEYS.get(0)));

    final InternalValidationResult result = safeJoin(pool.addLocal(signedExit));

    assertThat(result.code()).isEqualTo(ValidationResultCode.REJECT);
    assertThat(pool.size()).isZero();
    // and it can never be selected for a block, no matter how often we propose
    assertThat(pool.getItemsForBlock(state, getMaxVoluntaryExits(state))).isEmpty();
    assertThat(pool.getItemsForBlock(state, getMaxVoluntaryExits(state))).isEmpty();
    assertThat(pool.size()).isZero();
  }

  @Test
  void shouldAdmitAndIncludePastEpochExitSignedUnderStateForkDomain() {
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);
    final SignedVoluntaryExit signedExit =
        new SignedVoluntaryExit(exit, signUnderStateForkDomain(state, exit, VALIDATOR_KEYS.get(0)));

    final InternalValidationResult result = safeJoin(pool.addLocal(signedExit));

    // EIP-7044 makes a correctly signed past-epoch exit legitimately valid - the fix must not
    // reject these wholesale.
    assertThat(result.code()).isEqualTo(ValidationResultCode.ACCEPT);
    assertThat(pool.size()).isEqualTo(1);
    assertThat(pool.getItemsForBlock(state, getMaxVoluntaryExits(state)))
        .containsExactly(signedExit);
    assertThat(pool.size()).isEqualTo(1);
  }

  /**
   * Round trip through Teku's own signing path: an exit it generates for a past epoch must be
   * admitted to its own pool and selectable for a block.
   */
  @Test
  void shouldAdmitPastEpochExitProducedByOwnSigner() {
    final BeaconState state = getBestState();
    final VoluntaryExit exit = new VoluntaryExit(UInt64.ZERO, UInt64.ZERO);
    final SignedVoluntaryExit signedExit =
        new SignedVoluntaryExit(
            exit,
            new LocalSigner(spec, VALIDATOR_KEYS.get(0), SyncAsyncRunner.SYNC_RUNNER)
                .signVoluntaryExit(
                    exit, new ForkInfo(state.getFork(), state.getGenesisValidatorsRoot()))
                .getImmediately());

    assertThat(safeJoin(pool.addLocal(signedExit)).code()).isEqualTo(ValidationResultCode.ACCEPT);
    assertThat(pool.getItemsForBlock(state, getMaxVoluntaryExits(state)))
        .containsExactly(signedExit);
  }

  private int getMaxVoluntaryExits(final BeaconState state) {
    return spec.atSlot(state.getSlot()).getConfig().getMaxVoluntaryExits();
  }

  /**
   * Reproduces the pre-fix signing behaviour: milestone and fork both taken from the exit epoch.
   */
  private BLSSignature signUnderMessageEpochDomain(
      final BeaconState state, final VoluntaryExit exit, final BLSKeyPair keyPair) {
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

  private BLSSignature signUnderStateForkDomain(
      final BeaconState state, final VoluntaryExit exit, final BLSKeyPair keyPair) {
    final SpecVersion specVersion = spec.atSlot(state.getSlot());
    final Bytes32 domain =
        specVersion
            .beaconStateAccessors()
            .getVoluntaryExitDomain(
                exit.getEpoch(), state.getFork(), state.getGenesisValidatorsRoot());
    return BLS.sign(
        keyPair.getSecretKey(), specVersion.miscHelpers().computeSigningRoot(exit, domain));
  }

  private BeaconState getBestState() {
    final SafeFuture<BeaconState> stateFuture = recentChainData.getBestState().orElseThrow();
    assertThat(stateFuture).isCompleted();
    return safeJoin(stateFuture);
  }
}
