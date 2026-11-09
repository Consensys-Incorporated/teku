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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.client.ChainHead;

class ForkChoiceRatchetTest {

  private final ForkChoice forkChoice = mock(ForkChoice.class);
  private final SafeFuture<Optional<ChainHead>> processHeadResult = new SafeFuture<>();
  private final ForkChoiceRatchet ratchet = new ForkChoiceRatchet(forkChoice);

  @BeforeEach
  void setUp() {
    when(forkChoice.processHead(any())).thenReturn(processHeadResult);
  }

  @Test
  void shouldNotRunForkChoiceAgainForTheSameSlot() {
    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);

    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void shouldNotRunForkChoiceWhenSlotIsLessThanPreviousRun() {
    ratchet.scheduleForkChoiceForSlot(UInt64.valueOf(2));
    verify(forkChoice).processHead(UInt64.valueOf(2));

    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verifyNoMoreInteractions(forkChoice);
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldBeCompleteWhenLastForkChoiceForLaterSlot() {
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    ratchet.scheduleForkChoiceForSlot(UInt64.valueOf(3));

    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldCompleteWhenCurrentRunCompletesIfSlotIsTheSame() {
    ratchet.scheduleForkChoiceForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);

    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verify(forkChoice).getLastProcessHeadSlot();
    verifyNoMoreInteractions(forkChoice);
    assertThat(result).isNotDone();

    processHeadResult.complete(Optional.empty());
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldRunForkChoiceWhenSlotIsGreaterThanLastRun() {
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    ratchet.scheduleForkChoiceForSlot(UInt64.ZERO);
    verify(forkChoice).processHead(UInt64.ZERO);

    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
    assertThat(result).isNotDone();

    processHeadResult.complete(Optional.empty());
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceCompleteForSlot_shouldNotFailWhenForkChoiceFails() {
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(UInt64.ZERO);
    // Don't make block or attestation fail if fork choice fails, just go with the fork we're on
    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(UInt64.ONE);
    verify(forkChoice).processHead(UInt64.ONE);
    assertThat(result).isNotDone();

    processHeadResult.completeExceptionally(new RuntimeException("Ka-boom!"));
    assertThat(result).isCompleted();
  }

  @Test
  void ensureForkChoiceNotRerun() {
    final UInt64 slot = UInt64.valueOf(3);
    when(forkChoice.getLastProcessHeadSlot()).thenReturn(slot);
    final SafeFuture<Void> result = ratchet.ensureForkChoiceCompleteForSlot(slot);
    verify(forkChoice, never()).processHead(UInt64.valueOf(3));
    assertThat(result).isCompleted();
  }
}
