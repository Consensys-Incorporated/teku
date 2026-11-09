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

package tech.pegasys.teku.spec.logic.versions.bellatrix.block;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltairTest;

public class BlockProcessorBellatrixTest extends BlockProcessorAltairTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetBellatrix();
  }

  @Test
  public void shouldCreateNewPayloadRequest() throws BlockProcessingException {
    final BeaconState preState = createBeaconState();
    final BeaconBlockBody blockBody = dataStructureUtil.randomBeaconBlockBody();

    final NewPayloadRequest newPayloadRequest =
        spec.getBlockProcessor(UInt64.ONE).computeNewPayloadRequest(preState, blockBody);

    assertThat(newPayloadRequest.getExecutionPayload())
        .isEqualTo(blockBody.getOptionalExecutionPayload().orElseThrow());
    assertThat(newPayloadRequest.getVersionedHashes()).isEmpty();
  }
}
