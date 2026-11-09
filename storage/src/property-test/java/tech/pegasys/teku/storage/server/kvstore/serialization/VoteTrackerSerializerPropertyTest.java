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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;

public class VoteTrackerSerializerPropertyTest {

  private static final KvStoreSerializer<VoteTracker> SERIALIZER =
      KvStoreSerializer.createVoteTrackerSerializer(TestSpecFactory.createMinimalGloas());

  @Property
  void roundTripGloasAtGenesisIsExact(
      @ForAll @Size(32) final byte[] currentRootBytes,
      @ForAll @Size(32) final byte[] nextRootBytes,
      @ForAll final boolean nextEquivocating,
      @ForAll final boolean currentEquivocating,
      @ForAll final long nextSlot,
      @ForAll final boolean nextFullPayloadHint,
      @ForAll final long currentSlot,
      @ForAll final boolean currentFullPayloadHint) {
    final VoteTracker value =
        new VoteTracker(
            Bytes32.wrap(currentRootBytes),
            Bytes32.wrap(nextRootBytes),
            nextEquivocating,
            currentEquivocating,
            UInt64.fromLongBits(nextSlot),
            nextFullPayloadHint,
            UInt64.fromLongBits(currentSlot),
            currentFullPayloadHint);
    final byte[] serialized = SERIALIZER.serialize(value);
    final VoteTracker deserialized = SERIALIZER.deserialize(serialized);
    assertThat(deserialized).isEqualTo(value);
  }
}
