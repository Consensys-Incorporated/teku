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

import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.createSignedBlockSerializer;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Positive;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SignedBeaconBlockSerializerPropertyTest {
  @Property
  public boolean roundTrip(
      @ForAll final int seed,
      @ForAll(supplier = SpecSupplier.class) final Spec spec,
      @ForAll @Positive final long slotNum) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    final KvStoreSerializer<SignedBeaconBlock> serializer = createSignedBlockSerializer(spec);
    final SignedBeaconBlock value = dataStructureUtil.randomSignedBeaconBlock(slotNum);
    final byte[] serialized = serializer.serialize(value);
    final SignedBeaconBlock deserialized = serializer.deserialize(serialized);
    return deserialized.equals(value);
  }
}
