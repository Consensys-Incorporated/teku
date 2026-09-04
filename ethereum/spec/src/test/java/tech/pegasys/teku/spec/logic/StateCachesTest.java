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

package tech.pegasys.teku.spec.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class StateCachesTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(3, spec);
  protected ChainBuilder chainBuilder = storageSystem.chainBuilder();
  protected ChainUpdater chainUpdater = storageSystem.chainUpdater();

  private BeaconState stateWithCaches;
  private SignedBlockAndState bestBlock;

  private final BlockRewardCalculatorUtil rewardCalculator = new BlockRewardCalculatorUtil(spec);

  @BeforeEach
  void setUp() {
    chainUpdater.initializeGenesis();
    bestBlock = chainUpdater.advanceChainUntil(2);
    stateWithCaches = bestBlock.getState();
    assertThat(BeaconStateCache.getSlotCaches(stateWithCaches)).isNotEqualTo(SlotCaches.getNoOp());
    assertThat(BeaconStateCache.getTransitionCaches(stateWithCaches))
        .isNotEqualTo(TransitionCaches.getNoOp());
  }

  @Test
  void SlotCaches_processSlotShouldCleanUpSlotCaches()
      throws SlotProcessingException, EpochProcessingException {

    final SlotCaches slotCaches = BeaconStateCache.getSlotCaches(stateWithCaches);
    slotCaches.increaseBlockProposerRewards(UInt64.valueOf(84));
    slotCaches.setBlockExecutionValue(UInt256.valueOf(42));
    slotCaches.setBuilderUrl("https://www.foobar.com");

    final BeaconState stateAtSlot3 = spec.processSlots(stateWithCaches, UInt64.valueOf(3));

    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3).getBlockExecutionValue())
        .isEqualByComparingTo(UInt256.ZERO);
    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3).getBlockProposerRewards())
        .isEqualByComparingTo(UInt64.ZERO);
    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3).getBuilderUrl()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(BlockRewardsSource.class)
  void SlotCaches_blockProcessingShouldIncreaseBlockRewardsWhenBlockHasAttestations(
      final BlockRewardsSource blockRewardsSource)
      throws SlotProcessingException, EpochProcessingException, BlockProcessingException {

    assertThat(BeaconStateCache.getSlotCaches(stateWithCaches).getBlockExecutionValue())
        .isEqualByComparingTo(UInt256.ZERO);
    assertThat(BeaconStateCache.getSlotCaches(stateWithCaches).getBlockProposerRewards())
        .isEqualByComparingTo(UInt64.ZERO);

    final BeaconState stateAtSlot3 = spec.processSlots(stateWithCaches, UInt64.valueOf(3));

    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3).getBlockExecutionValue())
        .isEqualByComparingTo(UInt256.ZERO);
    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3).getBlockProposerRewards())
        .isEqualByComparingTo(UInt64.ZERO);

    // execution value should not be affected by block processing
    BeaconStateCache.getSlotCaches(stateAtSlot3).setBlockExecutionValue(UInt256.valueOf(42));

    final SignedBeaconBlock blockAtSlot3 = createWorthyBlock(blockRewardsSource);

    final BeaconState stateAtSlot3WithBlock =
        spec.atSlot(UInt64.valueOf(3))
            .getBlockProcessor()
            .processUnsignedBlock(
                stateAtSlot3,
                blockAtSlot3.getMessage(),
                IndexedAttestationCache.NOOP,
                BLSSignatureVerifier.NOOP,
                Optional.empty());

    final UInt64 expectedRewards =
        UInt64.valueOf(
            rewardCalculator
                .getBlockRewardData(blockAtSlot3.getMessage(), stateWithCaches)
                .getTotal());
    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3WithBlock).getBlockProposerRewards())
        .isEqualByComparingTo(expectedRewards);

    // execution value should not be affected by block processing
    assertThat(BeaconStateCache.getSlotCaches(stateAtSlot3WithBlock).getBlockExecutionValue())
        .isEqualByComparingTo(UInt256.valueOf(42));
  }

  private SignedBeaconBlock createWorthyBlock(final BlockRewardsSource blockRewardsSource) {
    final ChainBuilder.BlockOptions blockOptions = ChainBuilder.BlockOptions.create();

    if (blockRewardsSource.isAttestation()) {
      final Attestation attestation =
          chainBuilder.streamValidAttestationsForBlockAtSlot(3).findFirst().orElseThrow();

      blockOptions.addAttestation(attestation);
    }

    if (blockRewardsSource.isSyncCommittee()) {
      final SyncAggregate syncAggregate =
          dataStructureUtil.randomSyncAggregate(
              0, 3, 4, 7, 8, 9, 10, 16, 17, 20, 23, 25, 26, 29, 30);
      blockOptions.setSyncAggregate(syncAggregate);
    }

    if (blockRewardsSource.isAttesterSlashing()) {
      final Attestation attestation =
          chainBuilder.streamValidAttestationsForBlockAtSlot(3).findFirst().orElseThrow();
      blockOptions.addAttesterSlashing(
          chainBuilder.createAttesterSlashingForAttestation(attestation, bestBlock));
    }

    if (blockRewardsSource.isProposerSlashing()) {
      blockOptions.addProposerSlashing(
          chainBuilder.createProposerSlashingForAttestation(bestBlock));
    }

    return chainBuilder.generateBlockAtSlot(3, blockOptions).getBlock();
  }

  private enum BlockRewardsSource {
    ATTESTATION,
    SYNC_COMMITTEE,
    ATTESTER_SLASHING,
    PROPOSER_SLASHING,
    ALL,
    NONE;

    boolean isAttestation() {
      return this == ATTESTATION || this == ALL;
    }

    boolean isSyncCommittee() {
      return this == SYNC_COMMITTEE || this == ALL;
    }

    boolean isAttesterSlashing() {
      return this == ATTESTER_SLASHING || this == ALL;
    }

    boolean isProposerSlashing() {
      return this == PROPOSER_SLASHING || this == ALL;
    }
  }
}
