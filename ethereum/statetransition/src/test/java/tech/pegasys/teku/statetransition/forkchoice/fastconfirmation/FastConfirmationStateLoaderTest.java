/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.FastConfirmationStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

class FastConfirmationStateLoaderTest {

  // Minimal preset: SLOTS_PER_EPOCH == 8. Current slot 18 -> epoch 2, epoch start slot 16.
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private static final UInt64 CURRENT_SLOT = UInt64.valueOf(18);

  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private final ReadOnlyForkChoiceStrategy forkChoice = mock(ReadOnlyForkChoiceStrategy.class);
  private final Checkpoint previousObserved = new Checkpoint(UInt64.valueOf(1), Bytes32.random());
  private final Checkpoint currentObserved = new Checkpoint(UInt64.valueOf(2), Bytes32.random());
  private final Bytes32 head = Bytes32.random();

  private final BeaconState previousState = mock(BeaconState.class);
  private final BeaconState currentState = mock(BeaconState.class);
  private final BeaconState headState = mock(BeaconState.class);

  private final FastConfirmationStore fcrStore =
      new FastConfirmationStore(
          store,
          Bytes32.random(),
          previousObserved,
          currentObserved,
          previousObserved,
          Bytes32.random(),
          head);

  @BeforeEach
  void setUp() {
    when(store.getForkChoiceStrategy()).thenReturn(forkChoice);
    // Head in the current epoch by default: the pulled-up head state is the head block state.
    when(forkChoice.blockSlot(head)).thenReturn(Optional.of(UInt64.valueOf(17)));
  }

  @Test
  void shouldLoadAllSourceStatesAtEpochStart() {
    stubCheckpointState(previousObserved, Optional.of(previousState));
    stubCheckpointState(currentObserved, Optional.of(currentState));
    stubHeadState(Optional.of(headState));

    final FastConfirmationStates states = load(true).join().orElseThrow();

    assertThat(states.previousBalanceSource()).contains(previousState);
    assertThat(states.currentBalanceSource()).isSameAs(currentState);
    assertThat(states.pulledUpHeadState()).isSameAs(headState);
  }

  @Test
  void shouldSkipPreviousBalanceSourceWhenNotAtEpochStart() {
    stubCheckpointState(currentObserved, Optional.of(currentState));
    stubHeadState(Optional.of(headState));

    final FastConfirmationStates states = load(false).join().orElseThrow();

    assertThat(states.previousBalanceSource()).isEmpty();
    assertThat(states.currentBalanceSource()).isSameAs(currentState);
    assertThat(states.pulledUpHeadState()).isSameAs(headState);
    // The previous epoch's checkpoint state must not be fetched off epoch-start slots.
    verify(store, never()).retrieveCheckpointState(previousObserved);
  }

  @Test
  void shouldLoadPulledUpHeadStateViaCheckpointCacheWhenHeadLagsCurrentEpoch() {
    // Head block before the current epoch start: the pulled-up head state is the (current epoch,
    // head) checkpoint state, shared with attestation processing, not the raw block state.
    when(forkChoice.blockSlot(head)).thenReturn(Optional.of(UInt64.valueOf(15)));
    stubCheckpointState(currentObserved, Optional.of(currentState));
    stubCheckpointState(new Checkpoint(UInt64.valueOf(2), head), Optional.of(headState));

    final FastConfirmationStates states = load(false).join().orElseThrow();

    assertThat(states.pulledUpHeadState()).isSameAs(headState);
    verify(store, never()).retrieveBlockState(any(Bytes32.class));
  }

  @Test
  void shouldReturnEmptyWhenHeadStateUnavailable() {
    stubCheckpointState(currentObserved, Optional.of(currentState));
    stubHeadState(Optional.empty());

    assertThat(load(false).join()).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenCurrentBalanceSourceUnavailable() {
    stubCheckpointState(currentObserved, Optional.empty());
    stubHeadState(Optional.of(headState));

    assertThat(load(false).join()).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenPreviousBalanceSourceUnavailableAtEpochStart() {
    stubCheckpointState(previousObserved, Optional.empty());
    stubCheckpointState(currentObserved, Optional.of(currentState));
    stubHeadState(Optional.of(headState));

    assertThat(load(true).join()).isEmpty();
  }

  private SafeFuture<Optional<FastConfirmationStates>> load(
      final boolean includePreviousBalanceSource) {
    final SafeFuture<Optional<FastConfirmationStates>> result =
        FastConfirmationStateLoader.load(
            spec, fcrStore, head, CURRENT_SLOT, includePreviousBalanceSource);
    assertThatSafeFuture(result).isCompleted();
    return result;
  }

  private void stubCheckpointState(final Checkpoint checkpoint, final Optional<BeaconState> state) {
    when(store.retrieveCheckpointState(checkpoint)).thenReturn(SafeFuture.completedFuture(state));
  }

  private void stubHeadState(final Optional<BeaconState> state) {
    when(store.retrieveBlockState(head)).thenReturn(SafeFuture.completedFuture(state));
  }
}
