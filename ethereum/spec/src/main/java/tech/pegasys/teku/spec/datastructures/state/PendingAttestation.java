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

package tech.pegasys.teku.spec.datastructures.state;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;

public class PendingAttestation
    extends Container4<PendingAttestation, SszBitlist, AttestationData, SszUInt64, SszUInt64> {

  public static class PendingAttestationSchema
      extends ContainerSchema4<
          PendingAttestation, SszBitlist, AttestationData, SszUInt64, SszUInt64> {

    public PendingAttestationSchema(final SpecConfig config) {
      super(
          "PendingAttestation",
          namedSchema(
              "aggregation_bits", SszBitlistSchema.create(config.getMaxValidatorsPerCommittee())),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("inclusion_delay", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    public PendingAttestation create(
        final SszBitlist aggregationBitfield,
        final AttestationData data,
        final UInt64 inclusionDelay,
        final UInt64 proposerIndex) {
      return new PendingAttestation(this, aggregationBitfield, data, inclusionDelay, proposerIndex);
    }

    public SszBitlistSchema<?> getAggregationBitfieldSchema() {
      return (SszBitlistSchema<?>) getFieldSchema0();
    }

    @Override
    public PendingAttestation createFromBackingNode(final TreeNode node) {
      return new PendingAttestation(this, node);
    }
  }

  private PendingAttestation(final PendingAttestationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public PendingAttestation(
      final PendingAttestationSchema schema,
      final SszBitlist aggregationBitfield,
      final AttestationData data,
      final UInt64 inclusionDelay,
      final UInt64 proposerIndex) {
    super(
        schema,
        aggregationBitfield,
        data,
        SszUInt64.of(inclusionDelay),
        SszUInt64.of(proposerIndex));
  }

  @Override
  public PendingAttestationSchema getSchema() {
    return (PendingAttestationSchema) super.getSchema();
  }

  public SszBitlist getAggregationBits() {
    return getField0();
  }

  public AttestationData getData() {
    return getField1();
  }

  public UInt64 getInclusionDelay() {
    return getField2().get();
  }

  public UInt64 getProposerIndex() {
    return getField3().get();
  }
}
