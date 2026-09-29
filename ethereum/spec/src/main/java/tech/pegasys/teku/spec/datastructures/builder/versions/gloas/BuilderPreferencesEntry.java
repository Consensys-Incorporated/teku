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
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BuilderPreferencesEntry
    extends Container4<
        BuilderPreferencesEntry, SszPublicKey, SszByteList, SignedBuilderRequestAuth, SszUInt64> {

  protected BuilderPreferencesEntry(
      final BuilderPreferencesEntrySchema schema,
      final BLSPublicKey proposerPubkey,
      final Bytes url,
      final SignedBuilderRequestAuth auth,
      final UInt64 maxExecutionPayment) {
    super(
        schema,
        new SszPublicKey(proposerPubkey),
        schema.getUrlSchema().fromBytes(url),
        auth,
        SszUInt64.of(maxExecutionPayment));
  }

  protected BuilderPreferencesEntry(
      final BuilderPreferencesEntrySchema schema, final TreeNode backingTree) {
    super(schema, backingTree);
  }

  public BLSPublicKey getProposerPubkey() {
    return getField0().getBLSPublicKey();
  }

  public String getUrl() {
    return new String(getField1().getBytes().toArrayUnsafe(), StandardCharsets.UTF_8);
  }

  public SignedBuilderRequestAuth getAuth() {
    return getField2();
  }

  public UInt64 getMaxExecutionPayment() {
    return getField3().get();
  }

  @Override
  public BuilderPreferencesEntrySchema getSchema() {
    return (BuilderPreferencesEntrySchema) super.getSchema();
  }
}
