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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class ChainHeadTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void equals_shouldBeEqualToCopy() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final ChainHead chainHead = ChainHead.create(block);

    final ChainHead otherChainHead = copy(block);
    assertThat(chainHead).isEqualTo(otherChainHead);
  }

  @Test
  public void equals_shouldBNotBeEqualWhenBlocksDiffer() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    final SignedBlockAndState otherBlock = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);
    assertThat(block).isNotEqualTo(otherBlock);

    final ChainHead chainHead = ChainHead.create(block);
    final ChainHead otherChainHead = ChainHead.create(otherBlock);

    assertThat(chainHead).isNotEqualTo(otherChainHead);
  }

  @Test
  public void create_shouldDefaultToPendingPayloadStatusForBlockStateSummaries() {
    final SignedBlockAndState block = dataStructureUtil.randomSignedBlockAndState(UInt64.ONE);

    assertThat(ChainHead.create(block).getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
  }

  @SuppressWarnings("unchecked")
  private ChainHead copy(final SignedBlockAndState original) {
    final SignedBeaconBlock originalBlock = original.getSignedBeaconBlock().orElseThrow();
    final SignedBeaconBlock blockCopy = copy(originalBlock, originalBlock.getSchema());
    final BeaconState stateCopy =
        copy(
            original.getState(),
            SszSchema.as(
                BeaconState.class, spec.getGenesisSchemaDefinitions().getBeaconStateSchema()));
    final SignedBlockAndState blockAndStateCopy = new SignedBlockAndState(blockCopy, stateCopy);
    return ChainHead.create(blockAndStateCopy);
  }

  private <T extends SszData> T copy(final T original, final SszSchema<T> objType) {
    final Bytes serialized = original.sszSerialize();
    return objType.sszDeserialize(serialized);
  }
}
