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

package tech.pegasys.teku.dataproviders.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;

class StreamingStateRegeneratorTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);

  @Test
  void shouldHandleValidChainFromGenesis() throws Exception {
    // Build a small chain
    final SignedBlockAndState genesis = chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(10);
    final List<SignedBlockAndState> newBlocksAndStates =
        chainBuilder
            .streamBlocksAndStates(genesis.getSlot().plus(UInt64.ONE), chainBuilder.getLatestSlot())
            .toList();

    final SignedBlockAndState lastBlockAndState = newBlocksAndStates.getLast();
    final BeaconState result =
        StreamingStateRegenerator.regenerate(
            spec,
            genesis.getState(),
            newBlocksAndStates.stream().map(SignedBlockAndState::getBlock));
    assertThat(result).isEqualTo(lastBlockAndState.getState());
  }
}
