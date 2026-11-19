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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeaderSchema;

public class ProposerSlashing
    extends Container2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader> {

  public static class ProposerSlashingSchema
      extends ContainerSchema2<ProposerSlashing, SignedBeaconBlockHeader, SignedBeaconBlockHeader> {

    /** The proposer slashing of every fork up to PQ, with BLS-signed headers. */
    public ProposerSlashingSchema() {
      this("ProposerSlashing", SignedBeaconBlockHeader.SSZ_SCHEMA);
    }

    public ProposerSlashingSchema(
        final String containerName, final SignedBeaconBlockHeaderSchema signedHeaderSchema) {
      super(
          containerName,
          namedSchema("signed_header_1", signedHeaderSchema),
          namedSchema("signed_header_2", signedHeaderSchema));
    }

    @Override
    public ProposerSlashing createFromBackingNode(final TreeNode node) {
      return new ProposerSlashing(this, node);
    }
  }

  public static final ProposerSlashingSchema SSZ_SCHEMA = new ProposerSlashingSchema();

  /** PQ proposer slashings hold XMSS-signed headers. */
  public static final ProposerSlashingSchema SSZ_SCHEMA_PQ =
      new ProposerSlashingSchema("ProposerSlashingPq", SignedBeaconBlockHeader.SSZ_SCHEMA_PQ);

  private ProposerSlashing(final ProposerSlashingSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  /** The schema follows the headers': BLS-signed headers up to PQ, XMSS-signed ones from PQ. */
  public ProposerSlashing(
      final SignedBeaconBlockHeader header1, final SignedBeaconBlockHeader header2) {
    super(header1.isXmssSigned() ? SSZ_SCHEMA_PQ : SSZ_SCHEMA, header1, header2);
  }

  public SignedBeaconBlockHeader getHeader1() {
    return getField0();
  }

  public SignedBeaconBlockHeader getHeader2() {
    return getField1();
  }

  @Override
  public ProposerSlashingSchema getSchema() {
    return (ProposerSlashingSchema) super.getSchema();
  }
}
