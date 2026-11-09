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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.fulu;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class MetadataMessageFulu
    extends Container4<MetadataMessageFulu, SszUInt64, SszBitvector, SszBitvector, SszUInt64>
    implements MetadataMessage {

  MetadataMessageFulu(final MetadataMessageSchemaFulu schema) {
    super(schema);
  }

  MetadataMessageFulu(final MetadataMessageSchemaFulu schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  MetadataMessageFulu(
      final MetadataMessageSchemaFulu schema,
      final UInt64 seqNumber,
      final SszBitvector attNets,
      final SszBitvector syncNets,
      final UInt64 custodyGroupCount) {
    super(schema, SszUInt64.of(seqNumber), attNets, syncNets, SszUInt64.of(custodyGroupCount));
  }

  @Override
  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public SszBitvector getAttnets() {
    return getField1();
  }

  public SszBitvector getSyncnets() {
    return getField2();
  }

  public UInt64 getCustodyGroupCount() {
    return getField3().get();
  }

  @Override
  public Optional<SszBitvector> getOptionalSyncnets() {
    return Optional.of(getSyncnets());
  }

  @Override
  public Optional<UInt64> getOptionalCustodyGroupCount() {
    return Optional.of(getCustodyGroupCount());
  }
}
