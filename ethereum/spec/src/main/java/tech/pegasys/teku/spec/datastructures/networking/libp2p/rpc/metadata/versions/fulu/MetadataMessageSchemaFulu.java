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
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;

public class MetadataMessageSchemaFulu
    extends ContainerSchema4<MetadataMessageFulu, SszUInt64, SszBitvector, SszBitvector, SszUInt64>
    implements MetadataMessageSchema<MetadataMessageFulu> {
  public MetadataMessageSchemaFulu(final NetworkingSpecConfig networkingSpecConfig) {
    super(
        "MetadataMessage",
        namedSchema("seq_number", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "attnets", SszBitvectorSchema.create(networkingSpecConfig.getAttestationSubnetCount())),
        namedSchema(
            "syncnets", SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT)),
        namedSchema("custody_group_count", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  @Override
  public MetadataMessageFulu create(
      final UInt64 seqNumber,
      final Iterable<Integer> attnets,
      final Iterable<Integer> syncnets,
      final Optional<UInt64> custodyGroupCount) {
    return new MetadataMessageFulu(
        this,
        seqNumber,
        getAttnetsSchema().ofBits(attnets),
        getSyncnetsSchema().ofBits(syncnets),
        custodyGroupCount.orElse(UInt64.ZERO));
  }

  @Override
  public MetadataMessageFulu createDefault() {
    return new MetadataMessageFulu(this);
  }

  @Override
  public MetadataMessageFulu createFromBackingNode(final TreeNode node) {
    return new MetadataMessageFulu(this, node);
  }

  private SszBitvectorSchema<SszBitvector> getAttnetsSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema1();
  }

  private SszBitvectorSchema<SszBitvector> getSyncnetsSchema() {
    return (SszBitvectorSchema<SszBitvector>) getFieldSchema2();
  }
}
