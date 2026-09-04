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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_MAX_PROFIT;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_EXECUTION;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.BUILDER_CONFIG_SCHEMA;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
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
    final SignedExecutionPayloadBid bid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    when(circuitBreaker.isBuilderAllowed(any(), any())).thenReturn(false);

    assertThat(
            selector.selectBestRemoteBid(
                Set.of(toRemoteBid(bid)),
                List.of(),
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
