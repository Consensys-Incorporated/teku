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

package tech.pegasys.teku.test.acceptance.das;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class DasCheckpointSyncAcceptanceTest extends AcceptanceTestBase {
  private static final String BACKFILL_COMPLETED =
      "Column custody backfill completed successfully.";
  private static final Pattern COLUMN_RETRIEVAL =
      Pattern.compile("Retrieving missing column DataColumnSlotAndIdentifier\\[slot=(\\d+),");

  @Test
  public void shouldBackfillSupernodeCustodyWhileArchiving() throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createArchivalConfigBuilder()
                .withRealNetwork()
                .withSubscribeAllCustodySubnetsEnabled()
                .withDataColumnSidecarExtensionRetentionEpochs(0)
                .withDataColumnPruningIntervalSeconds(1)
                .withInteropValidators(0, 64)
                .build());
    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();
    final SpecConfigFulu config =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    final UInt64 firstFuluSlot =
        primaryNode.getSpec().computeStartSlotAtEpoch(config.getFuluForkEpoch());
    // Keep enough historical slots to observe archival interleaved with backfill.
    final UInt64 minimumCheckpointSlot = firstFuluSlot.plus(24);
    primaryNode.waitFor(
        () ->
            assertThat(primaryNode.getFinalizedBlock().getSlot())
                .isGreaterThanOrEqualTo(minimumCheckpointSlot),
        6,
        TimeUnit.MINUTES);
    final UInt64 checkpointSlot = primaryNode.getFinalizedBlock().getSlot();
    final int halfColumns = config.getNumberOfColumns() / 2;
    final List<UInt64> retainedIndices = UInt64.range(ZERO, UInt64.valueOf(halfColumns)).toList();
    waitForPhysicalColumns(
        primaryNode,
        getHistoricalBlocks(primaryNode, firstFuluSlot, checkpointSlot.decrement()),
        retainedIndices);

    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createArchivalConfigBuilder()
                .withRealNetwork()
                .withSubscribeAllCustodySubnetsEnabled()
                .withDataColumnSidecarExtensionRetentionEpochs(0)
                .withDataColumnPruningIntervalSeconds(1)
                .withCustodySyncBatchSize(1)
                .withDasDisableElRecovery()
                .withCheckpointSyncUrl(primaryNode.getBeaconRestApiUrl())
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                .withStartupTargetPeerCount(1)
                .withInteropValidators(0, 0)
                .build());
    secondaryNode.start();
    secondaryNode.waitFor(
        () -> assertThat(secondaryNode.getFinalizedBlock()).isNotNull(), 1, TimeUnit.MINUTES);
    // This is at least the secondary's checkpoint anchor, even if finality already advanced.
    final UInt64 secondaryStartupFinalizedSlot = secondaryNode.getFinalizedBlock().getSlot();
    waitForBackfillCompletion(secondaryNode);

    assertArchivingOverlappedBackfill(secondaryNode, checkpointSlot);
    secondaryNode.waitFor(
        () ->
            assertThat(secondaryNode.getFinalizedBlock().getSlot())
                .isGreaterThan(secondaryStartupFinalizedSlot.plus(1)),
        3,
        TimeUnit.MINUTES);
    // Freeze the entire eligible range, including blocks forward-synced after the checkpoint.
    final UInt64 archiveEndSlot =
        secondaryNode
            .getSpec()
            .computeStartSlotAtEpoch(secondaryNode.getFinalizedEpoch().orElseThrow())
            .decrement();
    final List<SignedBeaconBlock> expectedBlocks =
        getHistoricalBlocks(primaryNode, firstFuluSlot, archiveEndSlot);
    assertThat(expectedBlocks)
        .anyMatch(block -> block.getSlot().isGreaterThan(secondaryStartupFinalizedSlot));
    waitForPhysicalColumns(secondaryNode, expectedBlocks, retainedIndices);
    assertThat(
            primaryNode.getMetricValue(
                "beacon_data_column_sidecar_archive_reconstruction_successes_total"))
        .isPositive();
    verifyArchivedRangeCanBeBackfilled(
        secondaryNode, genesisTime, expectedBlocks, config.getNumberOfColumns());
    waitForPhysicalColumns(secondaryNode, expectedBlocks, retainedIndices);
  }

  private void verifyArchivedRangeCanBeBackfilled(
      final TekuBeaconNode archivedNode,
      final UInt64 genesisTime,
      final List<SignedBeaconBlock> expectedBlocks,
      final int numberOfColumns)
      throws Exception {
    // Status advertises availability only after its next epoch refresh following backfill.
    archivedNode.waitFor(
        () ->
            assertThat(archivedNode.getMetricValue("beacon_earliest_available_slot"))
                .isLessThanOrEqualTo(expectedBlocks.getFirst().getSlot().doubleValue()),
        2,
        TimeUnit.MINUTES);
    // Discovery is disabled in the DSL. This node can only fetch from the archived node.
    final TekuBeaconNode verificationNode =
        createTekuBeaconNode(
            createArchivalConfigBuilder()
                .withRealNetwork()
                .withSubscribeAllCustodySubnetsEnabled()
                .withDataColumnSidecarExtensionRetentionEpochs(Integer.MAX_VALUE)
                // Keep supernode recovery outside the test window: historical columns must
                // arrive via RPC, not be reconstructed locally from the first half.
                .withDataColumnSidecarRecoveryMaxDelayMillis(TimeUnit.HOURS.toMillis(1))
                .withDasDisableElRecovery()
                .withCheckpointSyncUrl(archivedNode.getBeaconRestApiUrl())
                .withGenesisTime(genesisTime.intValue())
                .withPeers(archivedNode)
                .withStartupTargetPeerCount(1)
                .withInteropValidators(0, 0)
                .build());
    verificationNode.start();
    waitForBackfillCompletion(verificationNode);
    waitForPhysicalColumns(
        verificationNode,
        expectedBlocks,
        UInt64.range(ZERO, UInt64.valueOf(numberOfColumns)).toList());
    final List<String> localRebuilds =
        verificationNode.getFilteredOutputContaining("").stream()
            .filter(
                line ->
                    line.contains("Rebuilding columns for slot ")
                        || line.contains("Recovery for block: "))
            .toList();
    for (final SignedBeaconBlock block : expectedBlocks) {
      assertThat(localRebuilds)
          .as("Columns at slot %s must be served by the archived peer", block.getSlot())
          .noneMatch(line -> line.contains(block.getRoot().toHexString()));
    }
    assertThat(
            archivedNode.getMetricValue(
                "beacon_data_column_sidecar_archive_reconstruction_successes_total"))
        .isPositive();
  }

  private void waitForBackfillCompletion(final TekuBeaconNode node) {
    node.waitForLogMessageContaining(BACKFILL_COMPLETED, 5, TimeUnit.MINUTES);
  }

  private List<SignedBeaconBlock> getHistoricalBlocks(
      final TekuBeaconNode node, final UInt64 firstSlot, final UInt64 lastSlot) throws IOException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    // The genesis block has no blobs, even when Fulu is active from genesis.
    for (final UInt64 slot : UInt64.rangeClosed(firstSlot.max(ONE), lastSlot).toList()) {
      node.getBlockAtSlot(slot).ifPresent(blocks::add);
    }
    assertThat(blocks).isNotEmpty();
    return blocks;
  }

  private void waitForPhysicalColumns(
      final TekuBeaconNode node,
      final List<SignedBeaconBlock> blocks,
      final List<UInt64> expectedIndices) {
    node.waitFor(
        () -> {
          for (final SignedBeaconBlock block : blocks) {
            assertThat(node.getBlockAtSlot(block.getSlot()).map(SignedBeaconBlock::getRoot))
                .as("Backfilled block at slot %s", block.getSlot())
                .contains(block.getRoot());
            // The execution stub produces three blobs in every block.
            assertThat(node.getDataColumnSidecarIndices(block.getRoot().toHexString()))
                .as("Physical columns at slot %s", block.getSlot())
                .containsExactlyInAnyOrderElementsOf(expectedIndices);
          }
        },
        3,
        TimeUnit.MINUTES);
  }

  private void assertArchivingOverlappedBackfill(
      final TekuBeaconNode node, final UInt64 checkpointSlot) {
    // Require observed interleaving, not just two services enabled in the same configuration.
    final List<String> activity = node.getFilteredOutputContaining("");
    final int completion =
        IntStream.range(0, activity.size())
            .filter(i -> activity.get(i).contains(BACKFILL_COMPLETED))
            .findFirst()
            .orElseThrow();
    final int firstRetrieval =
        IntStream.range(0, completion)
            .filter(i -> isHistoricalRetrieval(activity.get(i), checkpointSlot))
            .findFirst()
            .orElseThrow();
    final int lastRetrieval =
        IntStream.range(0, completion)
            .filter(i -> isHistoricalRetrieval(activity.get(i), checkpointSlot))
            .max()
            .orElseThrow();
    assertThat(activity.subList(firstRetrieval + 1, lastRetrieval))
        .as("Successful archival between historical custody requests, before backfill completion")
        .anyMatch(
            line ->
                line.matches(
                    ".*Archived data column sidecars to proofs across [1-9][0-9]* slots.*"));
  }

  private boolean isHistoricalRetrieval(final String line, final UInt64 checkpointSlot) {
    final Matcher matcher = COLUMN_RETRIEVAL.matcher(line);
    return matcher.find() && UInt64.valueOf(matcher.group(1)).isLessThan(checkpointSlot);
  }

  @Test
  public void shouldBeAbleToCheckpointSyncAndBackfillCustody() throws Exception {
    // supernode with validators
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 64)
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    primaryNode.waitForNewFinalization();
    final SignedBeaconBlock checkpointFinalizedBlock = primaryNode.getFinalizedBlock();
    // We expect at least 1 full epoch in Fulu before checkpoint, so it's greater without equal
    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    assertThat(
            primaryNode
                .getSpec()
                .computeEpochAtSlot(checkpointFinalizedBlock.getSlot())
                .isGreaterThan(specConfigFulu.getFuluForkEpoch()))
        .isTrue();

    // late joining full node without validators
    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createConfigBuilder()
                .withRealNetwork()
                .withCheckpointSyncUrl(primaryNode.getBeaconRestApiUrl())
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                .withInteropValidators(0, 0)
                .build());

    secondaryNode.start();

    final UInt64 firstFuluSlot =
        primaryNode.getSpec().computeStartSlotAtEpoch(specConfigFulu.getFuluForkEpoch());

    // Wait until custody is backfilled is completed.
    // It will require:
    // - forward sync to catch up
    // - backfill to complete
    // - custody fully backfilled up to first fulu slot
    final int expectedCustodyCount = specConfigFulu.getCustodyRequirement();
    secondaryNode.waitForCustodyBackfill(firstFuluSlot, expectedCustodyCount);

    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();
    final int checkpointSlot = checkpointFinalizedBlock.getSlot().intValue();
    final int endSlot = blockAtHead.getSlot().intValue();

    // after checkpoint is synced with sidecars
    final int afterCheckpointSidecars =
        IntStream.range(checkpointSlot + 1, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot, expectedCustodyCount))
            .mapToInt(i -> i)
            .sum();
    assertThat(afterCheckpointSidecars).isGreaterThan(0);

    // checkpoint + before checkpoint sidecars are backfilled
    final int beforeCheckpointSidecars =
        IntStream.rangeClosed(firstFuluSlot.intValue(), checkpointSlot)
            .mapToObj(UInt64::valueOf)
            .map(slot -> getAndAssertDasCustody(secondaryNode, slot, expectedCustodyCount))
            .mapToInt(i -> i)
            .sum();
    assertThat(beforeCheckpointSidecars).isGreaterThan(0);
  }

  private int getAndAssertDasCustody(
      final TekuBeaconNode node, final UInt64 fuluSlot, final int expectedCustodyCount) {
    try {
      final Optional<SignedBeaconBlock> maybeBlock = node.getBlockAtSlot(fuluSlot);
      if (maybeBlock.isPresent()) {
        final SignedBeaconBlock block = maybeBlock.get();
        final boolean hasBlobs =
            !block
                .getBeaconBlock()
                .orElseThrow()
                .getBody()
                .toVersionDeneb()
                .orElseThrow()
                .getBlobKzgCommitments()
                .isEmpty();
        final int columnCount = node.getDataColumnSidecarCount(block.getRoot().toHexString());
        if (hasBlobs) {
          assertThat(columnCount).isEqualTo(expectedCustodyCount);
        } else {
          assertThat(columnCount).isZero();
        }
        return columnCount;
      } else {
        return 0;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TekuNodeConfigBuilder createArchivalConfigBuilder() throws IOException {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork("swift")
        .withBellatrixEpoch(ZERO)
        .withCapellaEpoch(ZERO)
        .withDenebEpoch(ZERO)
        .withElectraEpoch(ZERO)
        .withFuluEpoch(ZERO)
        .withCustodySyncPollPeriodSeconds(1)
        .withStubExecutionEngine(3)
        .withLogLevel("DEBUG");
  }

  private TekuNodeConfigBuilder createConfigBuilder() throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork(Resources.getResource("fulu-minimal.yaml"))
        .withStubExecutionEngine(3)
        .withLogLevel("DEBUG");
  }
}
