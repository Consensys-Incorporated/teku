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

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.test.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.teku.test.acceptance.dsl.TekuBeaconNode;
import tech.pegasys.teku.test.acceptance.dsl.TekuNodeConfigBuilder;

public class Das50PercentRecoveryAcceptanceTest extends AcceptanceTestBase {
  private final int subnetCount = 128;
  private final int downloadTimeout = 12_000;
  private final int recoveryTimeout = 45_000;
  private static final Logger LOG = LogManager.getLogger();

  @Test
  public void shouldAbleToReconstructDataColumnSidecarsFrom50Percent_whenOnGossip()
      throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder(1)
                .withRealNetwork()
                // interop validators are not count for validator custody
                .withCustodyGroupCountOverride(subnetCount / 2)
                // we don't want to make this test extreme, withhold once and don't repeat
                .withDasPublishWithholdColumnsEverySlots(9999)
                .withInteropValidators(0, 64)
                .withDasDisableElRecovery()
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder(1)
                .withRealNetwork()
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                // supernode
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 0)
                .withDasDisableElRecovery()
                // need to disable this feature or node will go forward not waiting for
                // reconstruction
                .withColumnsDataAvailabilityHalfCheckDisabled()
                .withReworkedRecoveryTimeouts(recoveryTimeout, downloadTimeout)
                .build());

    secondaryNode.start();
    secondaryNode.waitForLogMessageContaining("Rest Api Configuration | Enabled: true");
    secondaryNode.setLogLevel(
        "tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarRecoveringCustodyImpl",
        "DEBUG");
    // DataColumnSidecars are withheld on primaryNode
    primaryNode.waitForLogMessageContaining("Withholding 64 non-custodied sidecars at");
    // DataColumnSidecars are reconstructed on secondaryNode
    secondaryNode.waitForLogMessageContaining("Data column sidecars recovery finished for block");
    final int firstFuluSlot =
        primaryNode
            .getSpec()
            .computeStartSlotAtEpoch(
                primaryNode
                    .getSpec()
                    .forMilestone(SpecMilestone.FULU)
                    .getConfig()
                    .getFuluForkEpoch())
            .intValue();
    secondaryNode.waitForBlockSatisfying(
        block -> assertThat(block.getSlot().intValue()).isGreaterThanOrEqualTo(firstFuluSlot));

    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();

    final int endSlot = blockAtHead.getSlot().intValue();

    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    final int allFuluColumns =
        IntStream.rangeClosed(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(
                slot ->
                    getAndAssertDasCustody(
                        secondaryNode, slot, specConfigFulu.getNumberOfColumns()))
            .mapToInt(i -> i)
            .sum();

    assertThat(allFuluColumns).isGreaterThan(0);
  }

  @Test
  public void shouldAbleToReconstructDataColumnSidecarsFrom50Percent_whenSyncing()
      throws Exception {
    final TekuBeaconNode primaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder(1)
                .withRealNetwork()
                .withDiscoveryNetwork()
                // interop validators are not count for validator custody
                .withCustodyGroupCountOverride(subnetCount / 2)
                // we don't want to make this test extreme, withhold once and don't repeat
                .withDasPublishWithholdColumnsEverySlots(9999)
                .withInteropValidators(0, 64)
                .withDasDisableElRecovery()
                .build());

    primaryNode.start();
    final UInt64 genesisTime = primaryNode.getGenesisTime();

    // this node syncs fast, need debug set on startup
    final TekuBeaconNode secondaryNode =
        createTekuBeaconNode(
            createFuluMinimalConfigBuilder(1)
                .withRealNetwork()
                .withDiscoveryNetwork()
                .withGenesisTime(genesisTime.intValue())
                .withPeers(primaryNode)
                // supernode
                .withSubscribeAllCustodySubnetsEnabled()
                .withInteropValidators(0, 0)
                .withDasDisableElRecovery()
                // need to disable this feature or node will go forward not waiting for
                // reconstruction
                .withColumnsDataAvailabilityHalfCheckDisabled()
                .withReworkedRecoveryTimeouts(recoveryTimeout, downloadTimeout)
                .build());

    // Wait for few epochs, so sync will kick-in when second node is started
    primaryNode.waitForEpochAtOrAbove(2);
    primaryNode.waitForEpochAtOrAbove(3);

    secondaryNode.start();
    secondaryNode.waitForLogMessageContaining("Rest Api Configuration | Enabled: true");
    secondaryNode.setLogLevel(
        "tech.pegasys.teku.statetransition.datacolumns.retriever.recovering.RebuildColumnsTask",
        "DEBUG");
    // DataColumnSidecars are reconstructed on secondaryNode
    secondaryNode.waitForLogMessageContaining("Rebuilding columns at");
    secondaryNode.waitForLogMessageContaining("Rebuilding columns DONE");
    // as first node custodies only 64 columns, it means we need to rebuild every single slot
    // sidecars one by one, so we will not wait for finalization, one slot recovery is ok

    final int firstFuluSlot =
        primaryNode
            .getSpec()
            .computeStartSlotAtEpoch(
                primaryNode
                    .getSpec()
                    .forMilestone(SpecMilestone.FULU)
                    .getConfig()
                    .getFuluForkEpoch())
            .intValue();
    secondaryNode.waitForBlockSatisfying(
        block -> assertThat(block.getSlot().intValue()).isGreaterThanOrEqualTo(firstFuluSlot),
        2,
        TimeUnit.MINUTES);
    final SignedBeaconBlock blockAtHead = secondaryNode.getHeadBlock();

    final int endSlot = blockAtHead.getSlot().intValue();
    final SpecConfigFulu specConfigFulu =
        SpecConfigFulu.required(primaryNode.getSpec().forMilestone(SpecMilestone.FULU).getConfig());
    final int allFuluColumns =
        IntStream.rangeClosed(firstFuluSlot, endSlot)
            .mapToObj(UInt64::valueOf)
            .map(
                slot ->
                    getAndAssertDasCustody(
                        secondaryNode, slot, specConfigFulu.getNumberOfColumns()))
            .mapToInt(i -> i)
            .sum();

    assertThat(allFuluColumns).isGreaterThan(0);
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
        LOG.debug("At slot {}, found {} das columns", fuluSlot, columnCount);
        return columnCount;
      } else {
        return 0;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TekuNodeConfigBuilder createFuluMinimalConfigBuilder(final int blobsToProduce)
      throws Exception {
    return TekuNodeConfigBuilder.createBeaconNode()
        .withNetwork(Resources.getResource("fulu-minimal.yaml"))
        .withStubExecutionEngine(blobsToProduce)
        .withLogLevel("INFO");
  }
}
