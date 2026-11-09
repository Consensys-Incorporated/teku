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

package tech.pegasys.teku.statetransition.executionproofs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionProofGeneratorImplTest {

  final Spec spec = TestSpecFactory.createMinimalElectra();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldGenerateDifferentProofsForDifferentSubnets()
      throws ExecutionException, InterruptedException {
    ExecutionProofGeneratorImpl generator =
        new ExecutionProofGeneratorImpl(
            spec.getGenesisSchemaDefinitions().toVersionElectra().get());
    SignedBlockContainer block = dataStructureUtil.randomSignedBlockContents();

    int subnetA = 1;
    int subnetB = 2;

    ExecutionProof proofA =
        generator.generateExecutionProof(block, subnetA, Duration.ofMillis(0)).get();
    ExecutionProof proofB =
        generator.generateExecutionProof(block, subnetB, Duration.ofMillis(0)).get();

    assertNotNull(proofA);
    assertNotNull(proofB);
    assertThat(proofA).isNotEqualTo(proofB);
  }
}
