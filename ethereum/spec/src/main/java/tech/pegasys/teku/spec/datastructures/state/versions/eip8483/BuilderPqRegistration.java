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

package tech.pegasys.teku.spec.datastructures.state.versions.eip8483;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderPqRegistration
    extends Container2<BuilderPqRegistration, SszPublicKey, SszBytes32> {

  BuilderPqRegistration(
      final BuilderPqRegistrationSchema schema,
      final BLSPublicKey builderPubkey,
      final Bytes32 pqData) {
    super(schema, new SszPublicKey(builderPubkey), SszBytes32.of(pqData));
  }

  BuilderPqRegistration(final BuilderPqRegistrationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BLSPublicKey getBuilderPubkey() {
    return getField0().getBLSPublicKey();
  }

  /** The builder's 32-byte XMSS public key. */
  public Bytes32 getPqData() {
    return getField1().get();
  }

  @Override
  public BuilderPqRegistrationSchema getSchema() {
    return (BuilderPqRegistrationSchema) super.getSchema();
  }
}
