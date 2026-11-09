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

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.containers.Container6;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;

public class StatusMessageFulu
    extends Container6<
        StatusMessageFulu, SszBytes4, SszBytes32, SszUInt64, SszBytes32, SszUInt64, SszUInt64>
    implements StatusMessage, RpcRequest {

  StatusMessageFulu(final StatusMessageSchemaFulu type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  StatusMessageFulu(final StatusMessageSchemaFulu type) {
    super(type);
  }

  public StatusMessageFulu(
      final StatusMessageSchemaFulu schema,
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot,
      final UInt64 earliestAvailableSlot) {
    super(
        schema,
        SszBytes4.of(forkDigest),
        SszBytes32.of(finalizedRoot),
        SszUInt64.of(finalizedEpoch),
        SszBytes32.of(headRoot),
        SszUInt64.of(headSlot),
        SszUInt64.of(earliestAvailableSlot));
  }

  @VisibleForTesting
  public StatusMessageFulu(
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot,
      final UInt64 earliestAvailableSlot) {
    super(
        new StatusMessageSchemaFulu(),
        SszBytes4.of(forkDigest),
        SszBytes32.of(finalizedRoot),
        SszUInt64.of(finalizedEpoch),
        SszBytes32.of(headRoot),
        SszUInt64.of(headSlot),
        SszUInt64.of(earliestAvailableSlot));
  }

  @Override
  public Bytes4 getForkDigest() {
    return getField0().get();
  }

  @Override
  public Bytes32 getFinalizedRoot() {
    return getField1().get();
  }

  @Override
  public UInt64 getFinalizedEpoch() {
    return getField2().get();
  }

  @Override
  public Bytes32 getHeadRoot() {
    return getField3().get();
  }

  @Override
  public UInt64 getHeadSlot() {
    return getField4().get();
  }

  @Override
  public Optional<UInt64> getEarliestAvailableSlot() {
    return Optional.ofNullable(getField5().get());
  }
}
