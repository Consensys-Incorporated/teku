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

package tech.pegasys.teku.api.migrated;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class StateValidatorIdentitySchema
    extends ContainerSchema3<StateValidatorIdentity, SszUInt64, SszPublicKey, SszUInt64> {

  public StateValidatorIdentitySchema() {
    super(
        "StateValidatorIdentity",
        namedSchema("index", UINT64_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("activation_epoch", UINT64_SCHEMA));
  }

  @Override
  public StateValidatorIdentity createFromBackingNode(final TreeNode node) {
    return new StateValidatorIdentity(this, node);
  }
}
