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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SigningData extends Container2<SigningData, SszBytes32, SszBytes32> {

  public static class SigningDataSchema
      extends ContainerSchema2<SigningData, SszBytes32, SszBytes32> {

    public SigningDataSchema() {
      super(
          "SigningData",
          namedSchema("object_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("domain", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public SigningData createFromBackingNode(final TreeNode node) {
      return new SigningData(this, node);
    }
  }

  public static final SigningDataSchema SSZ_SCHEMA = new SigningDataSchema();

  private SigningData(final SigningDataSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public SigningData(final Bytes32 objectRoot, final Bytes32 domain) {
    super(SSZ_SCHEMA, SszBytes32.of(objectRoot), SszBytes32.of(domain));
  }

  public Bytes32 getObjectRoot() {
    return getField0().get();
  }

  public Bytes32 getDomain() {
    return getField1().get();
  }
}
