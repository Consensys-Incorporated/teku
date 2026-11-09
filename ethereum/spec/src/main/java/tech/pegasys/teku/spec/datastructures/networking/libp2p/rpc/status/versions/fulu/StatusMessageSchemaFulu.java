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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu;

import com.google.common.base.Preconditions;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;

public class StatusMessageSchemaFulu
    extends ContainerSchema6<
        StatusMessageFulu, SszBytes4, SszBytes32, SszUInt64, SszBytes32, SszUInt64, SszUInt64>
    implements StatusMessageSchema<StatusMessageFulu> {

  public StatusMessageSchemaFulu() {
    super(
        "StatusMessage",
        namedSchema("fork_digest", SszPrimitiveSchemas.BYTES4_SCHEMA),
        namedSchema("finalized_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("finalized_epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("head_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("head_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("earliest_available_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  @Override
  public StatusMessageFulu createFromBackingNode(final TreeNode node) {
    return new StatusMessageFulu(this, node);
  }

  @Override
  public StatusMessageFulu create(
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot,
      final Optional<UInt64> earliestAvailableSlot) {

    Preconditions.checkArgument(earliestAvailableSlot.isPresent());
    return new StatusMessageFulu(
        this,
        forkDigest,
        finalizedRoot,
        finalizedEpoch,
        headRoot,
        headSlot,
        earliestAvailableSlot.get());
  }

  @Override
  public StatusMessageFulu createDefault() {
    return new StatusMessageFulu(this);
  }
}
