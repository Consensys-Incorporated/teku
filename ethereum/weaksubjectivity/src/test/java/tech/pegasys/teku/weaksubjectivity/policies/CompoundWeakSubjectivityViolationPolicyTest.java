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

package tech.pegasys.teku.weaksubjectivity.policies;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;

public class CompoundWeakSubjectivityViolationPolicyTest {
  private final List<WeakSubjectivityViolationPolicy> policies =
      List.of(
          mock(WeakSubjectivityViolationPolicy.class), mock(WeakSubjectivityViolationPolicy.class));
  private final InOrder orderedPolicyMocks = inOrder(policies.get(0), policies.get(1));
  private final CompoundWeakSubjectivityViolationPolicy policy =
      new CompoundWeakSubjectivityViolationPolicy(policies);

  @Test
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod() {
    final CheckpointState latestFinalizedCheckpoint = mock(CheckpointState.class);
    final UInt64 currentEpoch = UInt64.valueOf(11);
    final UInt64 wsPeriod = UInt64.valueOf(100);
    policy.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
        currentEpoch, latestFinalizedCheckpoint, wsPeriod);

    orderedPolicyMocks
        .verify(policies.get(0))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            currentEpoch, latestFinalizedCheckpoint, wsPeriod);
    orderedPolicyMocks
        .verify(policies.get(1))
        .onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
            currentEpoch, latestFinalizedCheckpoint, wsPeriod);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void onChainInconsistentWithWeakSubjectivityCheckpoint() {
    final Checkpoint wsCheckpoint = mock(Checkpoint.class);
    final SignedBeaconBlock block = mock(SignedBeaconBlock.class);
    policy.onChainInconsistentWithWeakSubjectivityCheckpoint(
        wsCheckpoint, block.getRoot(), block.getSlot());

    orderedPolicyMocks
        .verify(policies.get(0))
        .onChainInconsistentWithWeakSubjectivityCheckpoint(
            wsCheckpoint, block.getRoot(), block.getSlot());
    orderedPolicyMocks
        .verify(policies.get(1))
        .onChainInconsistentWithWeakSubjectivityCheckpoint(
            wsCheckpoint, block.getRoot(), block.getSlot());
    orderedPolicyMocks.verifyNoMoreInteractions();
  }

  @Test
  public void onFailedToPerformValidation_withError() {
    final String msg = "Bla";
    final Throwable error = new RuntimeException();
    policy.onFailedToPerformValidation(msg, error);

    orderedPolicyMocks.verify(policies.get(0)).onFailedToPerformValidation(msg, error);
    orderedPolicyMocks.verify(policies.get(1)).onFailedToPerformValidation(msg, error);
    orderedPolicyMocks.verifyNoMoreInteractions();
  }
}
