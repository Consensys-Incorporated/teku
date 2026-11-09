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

package tech.pegasys.teku.spec.logic.versions.phase0.block;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessorTest;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class BlockProcessorPhase0Test extends BlockProcessorTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalPhase0();
  }

  @Test
  void processAttestationsShouldSupportPhase0() throws BlockProcessingException {
    final MutableBeaconState state =
        (MutableBeaconState) dataStructureUtil.randomBeaconState(UInt64.ONE).createWritableCopy();
    final var attestations =
        genesisSpec.getSchemaDefinitions().getBeaconBlockBodySchema().getAttestationsSchema().of();

    genesisSpec
        .getBlockProcessor()
        .processAttestations(state, attestations, BLSSignatureVerifier.SIMPLE);
  }
}
