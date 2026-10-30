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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.BUILDER_CONFIG_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.BUILDER_ENTRY_SCHEMA;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderEntry;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.BidForBlock;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.LocalBid;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;

public class ExecutionPayloadBidSelectorTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionPayloadBidCircuitBreaker circuitBreaker =
      mock(ExecutionPayloadBidCircuitBreaker.class);
  private final BeaconState state = mock(BeaconState.class);

  private final ExecutionPayloadBidSelector selector =
      new ExecutionPayloadBidSelector(true, circuitBreaker);

  @ParameterizedTest
  @CsvSource({
    "100, 90, 80, 120, false",
    "100, 90, 90, 100, true",
    "90, 100, 100, 90, false",
    "100, 100, 90, 100, true",
    "1000, 0, 1, 1, false",
    "1, 1, 1000, 0, true",
    "0, 1, 1000, 0, true",
    "1000, 0, 10, 0, true",
    "10, 0, 1000, 0, false",
    "0, 18446744073709551615, 1000, 100, true",
    "1000, 100, 0, 18446744073709551615, false",
    "5, 18446744073709551615, 10, 18446744073709551615, false",
    "10, 18446744073709551615, 5, 18446744073709551615, true",
    "0, 18446744073709551615, 1000, 0, true",
    "1000, 0, 0, 18446744073709551615, false",
    "18446744073709551615, 100, 18446744073709551614, 101, false"
  })
  void ranksRemoteBidsByPreferenceThenBoostedScoreThenRawValue(
      final String valueA,
      final String factorA,
      final String valueB,
      final String factorB,
      final boolean aWins) {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid bidA =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(valueA));
    final SignedExecutionPayloadBid bidB =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(valueB));
    final BuilderConfig builderConfig =
        BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(factorA));
    final RemoteBid p2pBid = toRemoteBid(bidA);
    final RemoteBid builderBid =
        toBuilderApiBid(bidB, createBuilderEntry(UInt64.ZERO, UInt64.valueOf(factorB)));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    final Optional<RemoteBid> bestRemote =
        selector.selectBestRemoteBid(
            Set.of(p2pBid), List.of(builderBid), parentRoot, parentBlockHash, state, builderConfig);

    assertThat(bestRemote).contains(aWins ? p2pBid : builderBid);
    // Without a local payload, preserve the configured preference between remote bids.
    assertThat(
            selector.selectBestBidForBlock(Optional.empty(), bestRemote, builderConfig, slot).bid())
        .isEqualTo(aWins ? bidA : bidB);

    final RemoteBid builderBidA =
        toBuilderApiBid(bidA, createBuilderEntry(UInt64.ZERO, UInt64.valueOf(factorA)));
    for (final List<RemoteBid> bids :
        List.of(List.of(builderBidA, builderBid), List.of(builderBid, builderBidA))) {
      assertThat(
              selector.selectBestRemoteBid(
                  Set.of(), bids, parentRoot, parentBlockHash, state, builderConfig))
          .contains(aWins ? builderBidA : builderBid);
    }
  }

  @Test
  void selectsLocalBidWhenNoRemoteBidIsEligible() {
    final UInt64 slot = UInt64.valueOf(10);
    final LocalBid localBid = new LocalBid(randomLocalSelfBuiltBid(slot), UInt256.ONE, false);

    assertThat(
            selector
                .selectBestBidForBlock(
                    Optional.of(localBid), Optional.empty(), BuilderConfig.NO_OP, slot)
                .bid())
        .isEqualTo(localBid.bid());
  }

  @Test
  void failsWhenNeitherLocalNorRemoteBidIsAvailable() {
    assertThatThrownBy(
            () ->
                selector.selectBestBidForBlock(
                    Optional.empty(), Optional.empty(), BuilderConfig.NO_OP, UInt64.valueOf(10)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No remote or self-built bid is available for block at slot 10.");
  }

  @Test
  void comparesWinningBuilderBidWithLocalUsingTheSameEntryFactor() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final RemoteBid p2pBid =
        toRemoteBid(createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100)));
    final RemoteBid builderBid =
        toBuilderApiBid(
            createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(80)),
            createBuilderEntry(UInt64.ZERO, UInt64.valueOf(120)));
    final BuilderConfig builderConfig = BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90));
    final LocalBid localBid =
        new LocalBid(randomLocalSelfBuiltBid(slot), UInt256.valueOf(90_000_000_000L), false);
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    final Optional<RemoteBid> bestRemote =
        selector.selectBestRemoteBid(
            Set.of(p2pBid), List.of(builderBid), parentRoot, parentBlockHash, state, builderConfig);

    assertThat(bestRemote).contains(builderBid);
    assertThat(
            selector
                .selectBestBidForBlock(Optional.of(localBid), bestRemote, builderConfig, slot)
                .bid())
        .isEqualTo(builderBid.bid());
  }

  @Test
  void selectsNextEligibleBidWhenHighestRankedBuilderIsDisallowed() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final RemoteBid allowedBid =
        toRemoteBid(createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100)));
    final RemoteBid disallowedBid =
        toBuilderApiBid(
            createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(200)),
            createBuilderEntry(UInt64.ZERO, BUILDER_BOOST_FACTOR_PREFER_BUILDER));
    when(circuitBreaker.isBuilderAllowed(allowedBid.builderIndex(), state)).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(allowedBid),
                List.of(disallowedBid),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .contains(allowedBid);
  }

  @Test
  void selectsBuilderBidUsingItsOwnMinimum() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final RemoteBid belowMinimum =
        toBuilderApiBid(
            createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(75)),
            createBuilderEntry(UInt64.valueOf(100), BUILDER_BOOST_FACTOR_PREFER_BUILDER));
    final RemoteBid eligibleBid =
        toBuilderApiBid(
            createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(60)),
            createBuilderEntry(UInt64.valueOf(50), BUILDER_BOOST_FACTOR_MAX_PROFIT));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(),
                List.of(belowMinimum, eligibleBid),
                parentRoot,
                parentBlockHash,
                state,
                BUILDER_CONFIG_SCHEMA.create(
                    UInt64.valueOf(70), BUILDER_BOOST_FACTOR_MAX_PROFIT, List.of())))
        .contains(eligibleBid);
  }

  @Test
  void selectBestRemoteBidReturnsEmptyWhenBidsAreEmpty() {
    assertThat(
            selector.selectBestRemoteBid(
                Set.of(),
                List.of(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                state,
                BuilderConfig.NO_OP))
        .isEmpty();
  }

  @Test
  void selectBestRemoteBidFiltersP2PBidsOnParentRootAndParentBlockHash() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid matching =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final SignedExecutionPayloadBid wrongRoot =
        createBid(slot, dataStructureUtil.randomBytes32(), parentBlockHash, UInt64.valueOf(200));
    final SignedExecutionPayloadBid wrongHash =
        createBid(slot, parentRoot, dataStructureUtil.randomBytes32(), UInt64.valueOf(300));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(wrongRoot), toRemoteBid(wrongHash), toRemoteBid(matching)),
                List.of(),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .contains(toRemoteBid(matching));
  }

  @Test
  void selectBestRemoteBidReturnsHighestValueMatching() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid lowerBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final SignedExecutionPayloadBid higherBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(500));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(lowerBid), toRemoteBid(higherBid)),
                List.of(),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .contains(toRemoteBid(higherBid));
  }

  @Test
  void selectBestRemoteBidFiltersBuilderNotAllowed() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid p2pBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final SignedExecutionPayloadBid builderBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(42));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(false);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(p2pBid)),
                List.of(toRemoteBid(builderBid)),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .isEmpty();
  }

  @Test
  void selectBestRemoteBidFiltersP2PBidsBelowMinBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final UInt64 minBid = UInt64.valueOf(100);
    final BuilderConfig builderConfig =
        BUILDER_CONFIG_SCHEMA.create(minBid, UInt64.valueOf(100), List.of());
    final SignedExecutionPayloadBid belowMinBid =
        createBid(slot, parentRoot, parentBlockHash, minBid.minus(1));
    final SignedExecutionPayloadBid atMinBid = createBid(slot, parentRoot, parentBlockHash, minBid);
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(belowMinBid)),
                List.of(),
                parentRoot,
                parentBlockHash,
                state,
                builderConfig))
        .isEmpty();
    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(atMinBid)),
                List.of(),
                parentRoot,
                parentBlockHash,
                state,
                builderConfig))
        .contains(toRemoteBid(atMinBid));
  }

  @Test
  void selectBestRemoteBidPrefersBuilderBidOnEqualValue() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final UInt64 value = UInt64.valueOf(100);
    final SignedExecutionPayloadBid p2pBid = createBid(slot, parentRoot, parentBlockHash, value);
    final SignedExecutionPayloadBid builderBid =
        createBid(slot, parentRoot, parentBlockHash, value);
    // the entry's boost factor matches the top-level one, so the boosted values tie
    final BuilderEntry builderEntry =
        createBuilderEntry(UInt64.ZERO, BUILDER_BOOST_FACTOR_MAX_PROFIT);
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(p2pBid)),
                List.of(toBuilderApiBid(builderBid, builderEntry)),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .contains(toBuilderApiBid(builderBid, builderEntry));
  }

  @Test
  void selectBestRemoteBidSelectsHigherValueBuilderBidOverP2P() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid p2pBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final SignedExecutionPayloadBid builderBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(200));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(p2pBid)),
                List.of(toRemoteBid(builderBid)),
                parentRoot,
                parentBlockHash,
                state,
                BuilderConfig.NO_OP))
        .contains(toRemoteBid(builderBid));
  }

  @Test
  void requestedFactorOverridesConfiguredFactorAndSelectsLocal() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final BidForBlock selectedBid =
        selectBestBidForBlock(
            remoteBid,
            UInt256.valueOf(80_000_000_000L),
            false,
            BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(80)));

    assertThat(selectedBid.bid().getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void configuredFactorSelectsRemoteBelowThreshold() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final BidForBlock selectedBid =
        selectBestBidForBlock(
            remoteBid,
            UInt256.valueOf(89_000_000_000L),
            false,
            BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90)));

    assertThat(selectedBid.bid()).isEqualTo(remoteBid);
  }

  @Test
  void configuredFactorSelectsLocalAtThreshold() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final BidForBlock selectedBid =
        selectBestBidForBlock(
            remoteBid,
            UInt256.valueOf(90_000_000_000L),
            false,
            BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90)));

    assertThat(selectedBid.bid().getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void preferExecutionFactorSelectsViableLocalBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.MAX_VALUE);

    final BidForBlock selectedBid =
        selectBestBidForBlock(
            remoteBid,
            UInt256.ZERO,
            false,
            BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_EXECUTION));

    assertThat(selectedBid.bid().getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void preferBuilderFactorSelectsRemoteBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.ONE);

    final BidForBlock selectedBid =
        selectBestBidForBlock(
            remoteBid,
            UInt256.MAX_VALUE,
            false,
            BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_BUILDER));

    assertThat(selectedBid.bid()).isEqualTo(remoteBid);
  }

  @Test
  void comparesLocalValueAtWeiPrecision() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.ONE);

    final BidForBlock belowThreshold =
        selectBestBidForBlock(
            remoteBid,
            UInt256.valueOf(899_999_999),
            false,
            BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90)));
    final BidForBlock atThreshold =
        selectBestBidForBlock(
            remoteBid,
            UInt256.valueOf(900_000_000),
            false,
            BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90)));

    assertThat(belowThreshold.bid()).isEqualTo(remoteBid);
    assertThat(atThreshold.bid().getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void enabledShouldOverrideBuilderSelectsLowValueLocalBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final BidForBlock selectedBid =
        selectBestBidForBlock(remoteBid, UInt256.ONE, true, BuilderConfig.NO_OP);

    assertThat(selectedBid.bid().getSignature()).isEqualTo(BLSSignature.infinity());
  }

  @Test
  void disabledShouldOverrideBuilderIgnoresOverrideFlag() {
    final ExecutionPayloadBidSelector selectorWithOverrideDisabled =
        new ExecutionPayloadBidSelector(false, circuitBreaker);
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final LocalBid localBid = new LocalBid(randomLocalSelfBuiltBid(slot), UInt256.ONE, true);
    final BidForBlock selectedBid =
        selectorWithOverrideDisabled.selectBestBidForBlock(
            Optional.of(localBid), Optional.of(toRemoteBid(remoteBid)), BuilderConfig.NO_OP, slot);

    assertThat(selectedBid.bid()).isEqualTo(remoteBid);
  }

  @Test
  void selectsRemoteBidWhenLocalBidIsUnavailable() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));

    final BidForBlock selectedBid =
        selector.selectBestBidForBlock(
            Optional.empty(),
            Optional.of(toRemoteBid(remoteBid)),
            BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_EXECUTION),
            slot);

    assertThat(selectedBid.bid()).isEqualTo(remoteBid);
  }

  @Test
  void logsComparisonFactorAndSource() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100_000_000));

    try (final LogCaptor logCaptor = LogCaptor.forClass(ExecutionPayloadBidSelector.class)) {
      selectBestBidForBlock(
          remoteBid,
          UInt256.valueOf(80_000_000_000_000_000L),
          false,
          BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(80)));
      selectBestBidForBlock(
          remoteBid,
          UInt256.valueOf(89_000_000_000_000_000L),
          false,
          BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(90)));

      assertThat(logCaptor.getInfoLogs())
          .anyMatch(
              log ->
                  log.contains(
                      "Local execution payload (0.080000 ETH) is chosen over remote bid (0.100000 ETH)"))
          .anyMatch(log -> log.contains("builder boost factor: 80%, source: VC."))
          .anyMatch(
              log ->
                  log.contains(
                      "Remote bid (0.100000 ETH) is chosen over local execution payload (0.089000 ETH)"))
          .anyMatch(log -> log.contains("builder boost factor: 90%, source: VC."));
    }
  }

  @Test
  void logsBuilderBoostFactorsWithExpectedFormatting() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100_000_000));

    try (final LogCaptor logCaptor = LogCaptor.forClass(ExecutionPayloadBidSelector.class)) {
      selectBestBidForBlock(
          remoteBid,
          UInt256.valueOf(100_000_000_000_000_000L),
          false,
          BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_MAX_PROFIT));
      selectBestBidForBlock(
          remoteBid,
          UInt256.ONE,
          false,
          BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_EXECUTION));
      selectBestBidForBlock(
          remoteBid,
          UInt256.ONE,
          false,
          BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_BUILDER));
      selectBestBidForBlock(
          remoteBid,
          UInt256.valueOf(80_000_000_000_000_000L),
          false,
          BuilderConfig.withBuilderBoostFactor(UInt64.valueOf(80)));

      assertThat(logCaptor.getInfoLogs())
          .contains(
              "Local execution payload (0.100000 ETH) is chosen over remote bid (0.100000 ETH) for block at slot 10 - builder boost factor: MAX_PROFIT, source: VC.",
              "Local execution payload (0.000000 ETH) is chosen over remote bid (0.100000 ETH) for block at slot 10 - builder boost factor: PREFER_EXECUTION, source: VC.",
              "Remote bid (0.100000 ETH) is chosen over local execution payload (0.000000 ETH) for block at slot 10 - builder boost factor: PREFER_BUILDER, source: VC.",
              "Local execution payload (0.080000 ETH) is chosen over remote bid (0.100000 ETH) for block at slot 10 - builder boost factor: 80%, source: VC.");
    }
  }

  @Test
  void selectBestRemoteBidFiltersBuilderApiBidsBelowEntryMinBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    // top-level minBid is 50 but the builder entry's minBid is 100
    final BuilderConfig builderConfig =
        BUILDER_CONFIG_SCHEMA.create(UInt64.valueOf(50), UInt64.valueOf(100), List.of());
    final BuilderEntry builderEntry = createBuilderEntry(UInt64.valueOf(100), UInt64.valueOf(100));
    // bid value (75) is above top-level min but below entry-level min
    final SignedExecutionPayloadBid belowEntryMinBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(75));
    final SignedExecutionPayloadBid atEntryMinBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(),
                List.of(toBuilderApiBid(belowEntryMinBid, builderEntry)),
                parentRoot,
                parentBlockHash,
                state,
                builderConfig))
        .isEmpty();
    assertThat(
            selector.selectBestRemoteBid(
                Set.of(),
                List.of(toBuilderApiBid(atEntryMinBid, builderEntry)),
                parentRoot,
                parentBlockHash,
                state,
                builderConfig))
        .contains(toBuilderApiBid(atEntryMinBid, builderEntry));
  }

  @Test
  void builderBoostFactorFromBuilderEntryOverridesTopLevelConfig() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid remoteBid =
        createBid(
            slot, dataStructureUtil.randomBytes32(), dataStructureUtil.randomBytes32(), UInt64.ONE);
    // top-level config would prefer execution, but entry-level should prefer builder
    final BuilderEntry builderEntry =
        createBuilderEntry(UInt64.ZERO, BUILDER_BOOST_FACTOR_PREFER_BUILDER);
    final BuilderConfig builderConfig =
        BuilderConfig.withBuilderBoostFactor(BUILDER_BOOST_FACTOR_PREFER_EXECUTION);

    final LocalBid localBid = new LocalBid(randomLocalSelfBuiltBid(slot), UInt256.MAX_VALUE, false);
    final BidForBlock selectedBid =
        selector.selectBestBidForBlock(
            Optional.of(localBid),
            Optional.of(toBuilderApiBid(remoteBid, builderEntry)),
            builderConfig,
            slot);

    assertThat(selectedBid.bid()).isEqualTo(remoteBid);
  }

  private BidForBlock selectBestBidForBlock(
      final SignedExecutionPayloadBid remoteBid,
      final UInt256 localValue,
      final boolean shouldOverrideBuilder,
      final BuilderConfig builderConfig) {
    final UInt64 slot = remoteBid.getMessage().getSlot();
    final LocalBid localBid =
        new LocalBid(randomLocalSelfBuiltBid(slot), localValue, shouldOverrideBuilder);
    return selector.selectBestBidForBlock(
        Optional.of(localBid), Optional.of(toRemoteBid(remoteBid)), builderConfig, slot);
  }

  private RemoteBid toRemoteBid(final SignedExecutionPayloadBid bid) {
    return new RemoteBid(bid, bid.getMessage().getValue(), Optional.empty());
  }

  private RemoteBid toBuilderApiBid(
      final SignedExecutionPayloadBid bid, final BuilderEntry builderEntry) {
    return new RemoteBid(bid, bid.getMessage().getValue(), Optional.of(builderEntry));
  }

  private BuilderEntry createBuilderEntry(final UInt64 minBid, final UInt64 builderBoostFactor) {
    return BUILDER_ENTRY_SCHEMA.create(
        Bytes.of("https://builder.example.com".getBytes(StandardCharsets.UTF_8)),
        dataStructureUtil.randomSignedBuilderRequestAuth(),
        List.of(),
        UInt64.MAX_VALUE,
        minBid,
        builderBoostFactor);
  }

  private SignedExecutionPayloadBid randomLocalSelfBuiltBid(final UInt64 slot) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64(),
            UInt64.ZERO,
            slot,
            UInt64.ZERO,
            UInt64.ZERO,
            schema.getBlobKzgCommitmentsSchema().createFromElements(List.of()),
            dataStructureUtil.randomBytes32());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, BLSSignature.infinity());
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot,
      final Bytes32 parentBlockRoot,
      final Bytes32 parentBlockHash,
      final UInt64 value) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            parentBlockHash,
            parentBlockRoot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            slot,
            value,
            UInt64.ZERO,
            schema
                .getBlobKzgCommitmentsSchema()
                .createFromElements(dataStructureUtil.randomBlobKzgCommitments().asList()),
            dataStructureUtil.randomBytes32());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, dataStructureUtil.randomSignature());
  }
}
