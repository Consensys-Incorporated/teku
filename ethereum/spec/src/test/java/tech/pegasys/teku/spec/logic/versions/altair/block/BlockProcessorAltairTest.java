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

package tech.pegasys.teku.spec.logic.versions.altair.block;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessorTest;

public class BlockProcessorAltairTest extends BlockProcessorTest {
  final BLSSignatureVerifier signatureVerifier = mock(BLSSignatureVerifier.class);

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalAltair();
  }

  @Test
  void eth2FastAggregateVerify_shouldReturnFalseIfNoPublicKeysAndNotInfinitySignature() {
    assertThat(
            BlockProcessorAltair.eth2FastAggregateVerify(
                signatureVerifier, List.of(), Bytes32.ZERO, BLSTestUtil.randomSignature(7654)))
        .isFalse();
    verifyNoInteractions(signatureVerifier);
  }

  @Test
  void eth2FastAggregateVerify_shouldReturnTrueIfNoPublicKeysAndInfinitySignature() {
    assertThat(
            BlockProcessorAltair.eth2FastAggregateVerify(
                signatureVerifier, List.of(), Bytes32.ZERO, BLSSignature.infinity()))
        .isTrue();
    verifyNoInteractions(signatureVerifier);
  }
}
