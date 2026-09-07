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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;
import tech.pegasys.teku.spec.schemas.ApiSchemas;

public class BuilderEntrySchema
    extends ContainerSchema6<
        BuilderEntry,
        SszByteList,
        SignedBuilderRequestAuth,
        SszList<SszPublicKey>,
        SszUInt64,
        SszUInt64,
        SszUInt64> {

  private static final long MAX_BUILDER_URL_SIZE = 2048;
  private static final long MAX_BUILDER_PUBKEYS = 64;

  public BuilderEntrySchema() {
    super(
        "BuilderEntry",
        namedSchema("url", SszByteListSchema.create(MAX_BUILDER_URL_SIZE)),
        namedSchema("auth", ApiSchemas.SIGNED_BUILDER_REQUEST_AUTH_SCHEMA),
        namedSchema(
            "builder_pubkeys",
            SszListSchema.create(SszPublicKeySchema.INSTANCE, MAX_BUILDER_PUBKEYS)),
        namedSchema("max_execution_payment", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("min_bid", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("builder_boost_factor", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public BuilderEntry create(
      final Bytes url,
      final SignedBuilderRequestAuth auth,
      final List<BLSPublicKey> builderPubkeys,
      final UInt64 maxExecutionPayment,
      final UInt64 minBid,
      final UInt64 builderBoostFactor) {
    return new BuilderEntry(
        this, url, auth, builderPubkeys, maxExecutionPayment, minBid, builderBoostFactor);
  }

  @Override
  public BuilderEntry createFromBackingNode(final TreeNode node) {
    return new BuilderEntry(this, node);
  }

  public SszByteListSchema<?> getUrlSchema() {
    return (SszByteListSchema<?>) getChildSchema(getFieldIndex("url"));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszPublicKey, ?> getBuilderPubkeysSchema() {
    return (SszListSchema<SszPublicKey, ?>) getChildSchema(getFieldIndex("builder_pubkeys"));
  }
}
