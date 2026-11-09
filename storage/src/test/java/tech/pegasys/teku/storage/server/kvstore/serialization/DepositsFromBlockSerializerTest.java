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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositsFromBlockSerializerTest {
  private final DepositsFromBlockEventSerializer serializer =
      new DepositsFromBlockEventSerializer();
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void shouldEncodeAndDecodeWithMultipleDeposits() {
    final DepositsFromBlockEvent event =
        DepositsFromBlockEvent.create(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            Stream.of(
                dataStructureUtil.randomDepositEvent(3),
                dataStructureUtil.randomDepositEvent(4),
                dataStructureUtil.randomDepositEvent(5)));
    final byte[] bytes = serializer.serialize(event);
    final DepositsFromBlockEvent result = serializer.deserialize(bytes);
    assertThat(result).usingDefaultComparator().isEqualTo(event);
  }

  @Test
  public void shouldEncodeAndDecodeWithSingleDeposit() {
    final DepositsFromBlockEvent event =
        DepositsFromBlockEvent.create(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            Stream.of(dataStructureUtil.randomDepositEvent()));
    final byte[] bytes = serializer.serialize(event);
    final DepositsFromBlockEvent result = serializer.deserialize(bytes);
    assertThat(result).usingDefaultComparator().isEqualTo(event);
  }
}
