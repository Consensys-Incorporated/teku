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

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class SyncCommittee
    extends Container2<SyncCommittee, SszVector<SszPublicKey>, SszPublicKey> {

  public static class SyncCommitteeSchema
      extends ContainerSchema2<SyncCommittee, SszVector<SszPublicKey>, SszPublicKey> {

    public SyncCommitteeSchema(final SpecConfigAltair specConfigAltair) {
      super(
          "SyncCommittee",
          namedSchema(
              "pubkeys",
              SszVectorSchema.create(
                  SszPublicKeySchema.INSTANCE, specConfigAltair.getSyncCommitteeSize())),
          namedSchema("aggregate_pubkey", SszPublicKeySchema.INSTANCE));
    }

    @Override
    public SyncCommittee createFromBackingNode(final TreeNode node) {
      return new SyncCommittee(this, node);
    }

    public SyncCommittee create(
        final List<SszPublicKey> pubkeys, final SszPublicKey aggregatePubkey) {
      return create(getPubkeysSchema().createFromElements(pubkeys), aggregatePubkey);
    }

    public SyncCommittee create(
        final SszVector<SszPublicKey> pubkeys, final SszPublicKey aggregatePubkey) {
      return new SyncCommittee(this, pubkeys, aggregatePubkey);
    }

    @SuppressWarnings("unchecked")
    public SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>> getPubkeysSchema() {
      return (SszVectorSchema<SszPublicKey, SszVector<SszPublicKey>>) getChildSchema(0);
    }
  }

  private SyncCommittee(final SyncCommitteeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private SyncCommittee(
      final SyncCommitteeSchema type,
      final SszVector<SszPublicKey> pubkeys,
      final SszPublicKey aggregatePubkey) {
    super(type, pubkeys, aggregatePubkey);
  }

  public SszVector<SszPublicKey> getPubkeys() {
    return getField0();
  }

  public SszPublicKey getAggregatePubkey() {
    return getField1();
  }
}
