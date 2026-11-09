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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;

/**
 * https://github.com/ethereum/consensus-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata
 */
public class MetadataMessagePhase0
    extends Container2<MetadataMessagePhase0, SszUInt64, SszBitvector> implements MetadataMessage {

  MetadataMessagePhase0(final MetadataMessageSchemaPhase0 type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  MetadataMessagePhase0(final MetadataMessageSchemaPhase0 type) {
    super(type);
  }

  MetadataMessagePhase0(
      final MetadataMessageSchemaPhase0 schema,
      final UInt64 seqNumber,
      final SszBitvector attnets) {
    super(schema, SszUInt64.of(seqNumber), attnets);
  }

  @Override
  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public SszBitvector getAttnets() {
    return getField1();
  }
}
