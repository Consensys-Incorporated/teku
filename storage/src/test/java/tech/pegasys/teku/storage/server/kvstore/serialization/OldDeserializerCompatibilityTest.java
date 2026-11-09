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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

class OldDeserializerCompatibilityTest {

  private static final Bytes32 CURRENT_ROOT =
      Bytes32.fromHexString("0x235bc3400c2839fd856a524871200bd5e362db615fc4565e1870ed9a2a936464");
  private static final Bytes32 NEXT_ROOT =
      Bytes32.fromHexString("0x367cbd40ac7318427aadb97345a91fa2e965daf3158d7f1846f1306305f41bef");
  private static final Spec SPEC =
      TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(2));

  private final VoteTrackerSerializer newSerializer = new VoteTrackerSerializer(SPEC);
  private final OldVoteTrackerDeserializer oldDeserializer = new OldVoteTrackerDeserializer();

  @Test
  void oldDeserializerShouldReadPreGloasFormat() {
    final VoteTracker vote =
        new VoteTracker(
            CURRENT_ROOT,
            NEXT_ROOT,
            true,
            false,
            UInt64.valueOf(9),
            false,
            UInt64.valueOf(8),
            false);

    final byte[] serialized = newSerializer.serialize(vote);

    assertThat(serialized).hasSize(74);
    assertThat(oldDeserializer.deserialize(serialized))
        .isEqualTo(new LegacyVoteTracker(CURRENT_ROOT, NEXT_ROOT, UInt64.ONE, true, false));
  }

  private static class OldVoteTrackerDeserializer {
    LegacyVoteTracker deserialize(final byte[] data) {
      return SSZ.decode(
          Bytes.of(data),
          reader -> {
            final Bytes32 currentRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
            final Bytes32 nextRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
            final UInt64 nextEpoch = UInt64.fromLongBits(reader.readUInt64());
            final boolean nextEquivocating;
            final boolean currentEquivocating;
            if (reader.isComplete()) {
              nextEquivocating = false;
              currentEquivocating = false;
            } else {
              nextEquivocating = reader.readBoolean();
              currentEquivocating = reader.readBoolean();
            }
            return new LegacyVoteTracker(
                currentRoot, nextRoot, nextEpoch, nextEquivocating, currentEquivocating);
          });
    }
  }

  private record LegacyVoteTracker(
      Bytes32 currentRoot,
      Bytes32 nextRoot,
      UInt64 nextEpoch,
      boolean nextEquivocating,
      boolean currentEquivocating) {}
}
