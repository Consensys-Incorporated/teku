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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockGossipValidator blockGossipValidator = mock(BlockGossipValidator.class);

  private final BlockValidator blockValidator = new BlockValidator(blockGossipValidator);

  @Test
  public void shouldExposeGossipValidation() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();

    when(blockGossipValidator.validate(eq(block), eq(true)))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    assertThat(blockValidator.validateGossip(block))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(blockGossipValidator).validate(eq(block), eq(true));
    verifyNoMoreInteractions(blockGossipValidator);
  }
}
