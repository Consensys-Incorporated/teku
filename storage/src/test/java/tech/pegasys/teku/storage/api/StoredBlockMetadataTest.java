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

package tech.pegasys.teku.storage.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class StoredBlockMetadataTest {

  @Test
  void fromBlockAndState_shouldCaptureGloasForkChoiceRebuildDataFromSignedBlock() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final ExecutionPayloadBid bid =
        blockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .map(SignedExecutionPayloadBid::getMessage)
            .orElseThrow();

    final StoredBlockMetadata metadata = StoredBlockMetadata.fromBlockAndState(spec, blockAndState);

    assertThat(metadata.getGloasForkChoiceRebuildData())
        .contains(
            new GloasForkChoiceRebuildData(
                bid.getParentBlockHash(), bid.getBlockHash(), Optional.empty()));
  }

  @Test
  void fromBlockAndState_shouldCaptureGloasForkChoiceRebuildDataFromStateOnlySummary() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SignedBlockAndState blockAndState =
        dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final ExecutionPayloadBid latestBid =
        blockAndState
            .getState()
            .toVersionGloas()
            .map(BeaconStateGloas::getLatestExecutionPayloadBid)
            .orElseThrow();

    final StoredBlockMetadata metadata =
        StoredBlockMetadata.fromBlockAndState(
            spec, StateAndBlockSummary.create(blockAndState.getState()));

    assertThat(metadata.getGloasForkChoiceRebuildData())
        .contains(
            new GloasForkChoiceRebuildData(
                latestBid.getParentBlockHash(), latestBid.getBlockHash(), Optional.empty()));
  }
}
