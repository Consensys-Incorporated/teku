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

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class BuilderPreferencesEntrySchema
    extends ContainerSchema4<
        BuilderPreferencesEntry, SszPublicKey, SszByteList, SignedBuilderRequestAuth, SszUInt64> {

  public BuilderPreferencesEntrySchema(
      final long maxBuilderUrlSize, final SignedBuilderRequestAuthSchema authSchema) {
    super(
        "BuilderPreferencesEntry",
        namedSchema("proposer_pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("url", SszByteListSchema.create(maxBuilderUrlSize)),
        namedSchema("auth", authSchema),
        namedSchema("max_execution_payment", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public BuilderPreferencesEntry create(
      final BLSPublicKey proposerPubkey,
      final Bytes url,
      final SignedBuilderRequestAuth auth,
      final UInt64 maxExecutionPayment) {
    return new BuilderPreferencesEntry(this, proposerPubkey, url, auth, maxExecutionPayment);
  }

  @Override
  public BuilderPreferencesEntry createFromBackingNode(final TreeNode node) {
    return new BuilderPreferencesEntry(this, node);
  }

  public SszByteListSchema<?> getUrlSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex("url"));
  }
}
