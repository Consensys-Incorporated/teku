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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MinGenesisTimeBlockEventSerializerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final MinGenesisTimeBlockEventSerializer serializer =
      new MinGenesisTimeBlockEventSerializer();

  @Test
  void shouldSurviveSerialization() {
    final MinGenesisTimeBlockEvent original =
        new MinGenesisTimeBlockEvent(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32());
    final byte[] serialized = serializer.serialize(original);
    final MinGenesisTimeBlockEvent deserialized = serializer.deserialize(serialized);

    assertThat(deserialized).usingDefaultComparator().isEqualTo(original);
  }
}
