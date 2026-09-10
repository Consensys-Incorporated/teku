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

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderEntry
    extends Container6<
        BuilderEntry,
        SszByteList,
        SignedBuilderRequestAuth,
        SszList<SszPublicKey>,
        SszUInt64,
        SszUInt64,
        SszUInt64> {

  protected BuilderEntry(
      final BuilderEntrySchema schema,
      final Bytes url,
      final SignedBuilderRequestAuth auth,
      final List<BLSPublicKey> builderPubkeys,
      final UInt64 maxExecutionPayment,
      final UInt64 minBid,
      final UInt64 builderBoostFactor) {
    super(
        schema,
        schema.getUrlSchema().fromBytes(url),
        auth,
        schema
            .getBuilderPubkeysSchema()
            .createFromElements(builderPubkeys.stream().map(SszPublicKey::new).toList()),
        SszUInt64.of(maxExecutionPayment),
        SszUInt64.of(minBid),
        SszUInt64.of(builderBoostFactor));
  }

  protected BuilderEntry(final BuilderEntrySchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public String getUrl() {
    return new String(getField0().getBytes().toArrayUnsafe(), StandardCharsets.UTF_8);
  }

  public SignedBuilderRequestAuth getAuth() {
    return getField1();
  }

  public SszList<SszPublicKey> getBuilderPubkeys() {
    return getField2();
  }

  public UInt64 getMaxExecutionPayment() {
    return getField3().get();
  }

  public UInt64 getMinBid() {
    return getField4().get();
  }

  public UInt64 getBuilderBoostFactor() {
    return getField5().get();
  }

  @Override
  public BuilderEntrySchema getSchema() {
    return (BuilderEntrySchema) super.getSchema();
  }
}
