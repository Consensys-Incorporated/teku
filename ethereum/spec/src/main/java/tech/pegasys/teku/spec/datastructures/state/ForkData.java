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

package tech.pegasys.teku.spec.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ForkData extends Container2<ForkData, SszBytes4, SszBytes32> {

  public static class ForkDataSchema extends ContainerSchema2<ForkData, SszBytes4, SszBytes32> {

    public ForkDataSchema() {
      super(
          "ForkData",
          namedSchema("current_version", SszPrimitiveSchemas.BYTES4_SCHEMA),
          namedSchema("genesis_validators_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public ForkData createFromBackingNode(final TreeNode node) {
      return new ForkData(this, node);
    }
  }

  public static final ForkDataSchema SSZ_SCHEMA = new ForkDataSchema();

  private ForkData(final ForkDataSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ForkData(final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    super(SSZ_SCHEMA, SszBytes4.of(currentVersion), SszBytes32.of(genesisValidatorsRoot));
  }

  public Bytes4 getCurrentVersion() {
    return getField0().get();
  }

  public Bytes32 getGenesisValidatorsRoot() {
    return getField1().get();
  }
}
