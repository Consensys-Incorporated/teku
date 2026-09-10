/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.datastructures.builder.versions.gloas;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SignedBuilderRequestAuthSchema
    extends ContainerSchema2<SignedBuilderRequestAuth, BuilderRequestAuth, SszSignature> {

  public SignedBuilderRequestAuthSchema(final BuilderRequestAuthSchema builderRequestAuthSchema) {
    super(
        "SignedBuilderRequestAuth",
        namedSchema("message", builderRequestAuthSchema),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SignedBuilderRequestAuth create(
      final BuilderRequestAuth message, final BLSSignature signature) {
    return new SignedBuilderRequestAuth(this, message, signature);
  }

  @Override
  public SignedBuilderRequestAuth createFromBackingNode(final TreeNode node) {
    return new SignedBuilderRequestAuth(this, node);
  }
}
