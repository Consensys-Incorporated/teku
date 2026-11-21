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

package tech.pegasys.teku.spec.signatures.xmss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class VrfLeafZeroStateTest {

  private static final int DEPTH = 8;
  private static final Bytes32 SEED = Bytes32.fromHexStringLenient("0x5eed");
  private static final VrfTree TREE = VrfTree.derive(SEED, DEPTH, 4);

  @Test
  void computeProof_matchesTheFullTreeForEverySlot() {
    final VrfLeafZeroState state = VrfLeafZeroState.fromBytes(SEED, DEPTH, stateOf(SEED));

    assertThat(state.getRoot()).isEqualTo(TREE.getRoot());
    IntStream.range(0, 1 << DEPTH)
        .mapToObj(UInt64::valueOf)
        .forEach(
            slot ->
                assertThat(state.computeProof(slot))
                    .describedAs("slot %s", slot)
                    .isEqualTo(TREE.computeProof(slot)));
  }

  @Test
  void toBytes_roundTrips() {
    final Bytes serialized = stateOf(SEED);

    assertThat(VrfLeafZeroState.fromBytes(SEED, DEPTH, serialized).toBytes()).isEqualTo(serialized);
  }

  @Test
  void fromBytes_rejectsTheStateOfAnotherSeed() {
    final Bytes otherState = stateOf(Bytes32.fromHexStringLenient("0x0be5"));

    assertThatThrownBy(() -> VrfLeafZeroState.fromBytes(SEED, DEPTH, otherState))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not derived from this VRF seed");
  }

  @Test
  void fromBytes_rejectsAStateForAnotherSlot() {
    final Bytes state = stateOf(SEED);
    final Bytes atSlotOne =
        Bytes.concatenate(
            state.slice(0, Bytes32.SIZE),
            Bytes.ofUnsignedInt(1, ByteOrder.LITTLE_ENDIAN),
            state.slice(Bytes32.SIZE + Integer.BYTES));

    assertThatThrownBy(() -> VrfLeafZeroState.fromBytes(SEED, DEPTH, atSlotOne))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("slot 1");
  }

  @Test
  void fromBytes_rejectsTheWrongSize() {
    assertThatThrownBy(() -> VrfLeafZeroState.fromBytes(SEED, DEPTH, stateOf(SEED).slice(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fromBytes_readsTheDevnetToolingState() throws IOException {
    // Entry 0 of ethereum-package's static_files/xmss-keys/b9dec4e1daa17dfe.vrf (pq branch at
    // d4c9a8ace9): validator 0 of its default mnemonic, whose VRF seed the genesis tool's own VRF
    // test pins
    final Bytes state =
        Bytes.wrap(
            Resources.toByteArray(
                Resources.getResource(getClass(), "vrf_state_devnet_validator_0.bin")));
    final Bytes32 seed =
        Bytes32.fromHexString("0x543fc5a4d86e79a434e9741770f7ee17c25bdc31be8d61436d7f9836538b03df");

    final VrfLeafZeroState vrfState = VrfLeafZeroState.fromBytes(seed, state);

    // The VRF root the devnet genesis registered for validator 0
    assertThat(vrfState.getRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2c8d6a710d3a22803be6490fef9c9247e30ac68793bffb7cbc151d17f8313cb0"));
    for (final long slot : new long[] {0, 1, 2, 3, 7, 100, 1234}) {
      assertThat(
              VrfTree.computeRootFromProof(
                  vrfState.computeProof(UInt64.valueOf(slot)), UInt64.valueOf(slot)))
          .describedAs("slot %s", slot)
          .isEqualTo(vrfState.getRoot());
    }
  }

  @Test
  void find_picksTheSeedsStateFromAFileOfStates() throws IOException {
    final Bytes devnetState =
        Bytes.wrap(
            Resources.toByteArray(
                Resources.getResource(getClass(), "vrf_state_devnet_validator_0.bin")));
    final Bytes32 seed =
        Bytes32.fromHexString("0x543fc5a4d86e79a434e9741770f7ee17c25bdc31be8d61436d7f9836538b03df");
    // Another validator's state first: its siblings don't open this seed's leaf 0 to its root
    final Bytes otherState =
        Bytes.concatenate(Bytes32.fromHexStringLenient("0x01"), devnetState.slice(Bytes32.SIZE));
    final Bytes states = Bytes.concatenate(otherState, devnetState);

    assertThat(VrfLeafZeroState.find(seed, states).map(VrfLeafZeroState::toBytes))
        .contains(devnetState);
    assertThat(VrfLeafZeroState.find(Bytes32.fromHexStringLenient("0x5eed"), states)).isEmpty();
  }

  /** The serialized slot-0 state of the depth-{@link #DEPTH} tree of {@code seed}. */
  private static Bytes stateOf(final Bytes32 seed) {
    final VrfTree tree = VrfTree.derive(seed, DEPTH, 4);
    return Bytes.concatenate(
        tree.getRoot(),
        Bytes.wrap(new byte[Integer.BYTES]),
        Bytes.concatenate(
            IntStream.range(0, DEPTH)
                .mapToObj(height -> VrfTree.computeNode(seed, height, 1))
                .toArray(Bytes[]::new)));
  }
}
