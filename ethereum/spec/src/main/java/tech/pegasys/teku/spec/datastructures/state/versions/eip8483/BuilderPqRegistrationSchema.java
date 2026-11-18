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
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class BuilderPqRegistrationSchema
    extends ContainerSchema2<BuilderPqRegistration, SszPublicKey, SszBytes32> {

  public BuilderPqRegistrationSchema() {
    super(
        "BuilderPQRegistration",
        namedSchema("builder_pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("pq_data", SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  public BuilderPqRegistration create(final BLSPublicKey builderPubkey, final Bytes32 pqData) {
    return new BuilderPqRegistration(this, builderPubkey, pqData);
  }

  @Override
  public BuilderPqRegistration createFromBackingNode(final TreeNode node) {
    return new BuilderPqRegistration(this, node);
  }
}
