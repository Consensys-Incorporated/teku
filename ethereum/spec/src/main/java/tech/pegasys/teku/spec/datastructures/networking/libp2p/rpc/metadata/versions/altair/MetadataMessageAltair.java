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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

public class MetadataMessageAltair
    extends Container3<MetadataMessageAltair, SszUInt64, SszBitvector, SszBitvector>
    implements MetadataMessage {

  MetadataMessageAltair(final MetadataMessageSchemaAltair schema) {
    super(schema);
  }

  MetadataMessageAltair(final MetadataMessageSchemaAltair schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  MetadataMessageAltair(
      final MetadataMessageSchemaAltair schema,
      final UInt64 seqNumber,
      final SszBitvector attNets,
      final SszBitvector syncNets) {
    super(schema, SszUInt64.of(seqNumber), attNets, syncNets);
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

  @Override
  public Optional<SszBitvector> getOptionalSyncnets() {
    return Optional.of(getSyncnets());
  }
}
