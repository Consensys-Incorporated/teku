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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SszSerializerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SszSerializer<SignedBeaconBlock> blockSerializer =
      new SszSerializer<>(spec.getGenesisSchemaDefinitions().getSignedBeaconBlockSchema());
  private final SszSerializer<Checkpoint> checkpointSerializer =
      new SszSerializer<>(Checkpoint.SSZ_SCHEMA);

  @Test
  public void roundTrip_block() {
    final SignedBeaconBlock value = dataStructureUtil.randomSignedBeaconBlock(11);
    final byte[] bytes = blockSerializer.serialize(value);
    final SignedBeaconBlock deserialized = blockSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }

  @Test
  public void roundTrip_checkpoint() {
    final Checkpoint value = dataStructureUtil.randomCheckpoint();
    final byte[] bytes = checkpointSerializer.serialize(value);
    final Checkpoint deserialized = checkpointSerializer.deserialize(bytes);
    assertThat(deserialized).isEqualTo(value);
  }
}
