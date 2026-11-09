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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;

public class StatusMessagePhase0
    extends Container5<StatusMessagePhase0, SszBytes4, SszBytes32, SszUInt64, SszBytes32, SszUInt64>
    implements StatusMessage, RpcRequest {

  StatusMessagePhase0(final StatusMessageSchemaPhase0 type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  StatusMessagePhase0(final StatusMessageSchemaPhase0 type) {
    super(type);
  }

  public StatusMessagePhase0(
      final StatusMessageSchemaPhase0 schema,
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot) {
    super(
        schema,
        SszBytes4.of(forkDigest),
        SszBytes32.of(finalizedRoot),
        SszUInt64.of(finalizedEpoch),
        SszBytes32.of(headRoot),
        SszUInt64.of(headSlot));
  }

  @VisibleForTesting
  public StatusMessagePhase0(
      final Bytes4 forkDigest,
      final Bytes32 finalizedRoot,
      final UInt64 finalizedEpoch,
      final Bytes32 headRoot,
      final UInt64 headSlot) {
    this(
        new StatusMessageSchemaPhase0(),
        forkDigest,
        finalizedRoot,
        finalizedEpoch,
        headRoot,
        headSlot);
  }

  @VisibleForTesting
  public static StatusMessagePhase0 createPreGenesisStatus(final Spec spec) {
    return new StatusMessagePhase0(
        createPreGenesisForkDigest(spec), Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, UInt64.ZERO);
  }

  private static Bytes4 createPreGenesisForkDigest(final Spec spec) {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final Bytes4 genesisFork = genesisSpec.getConfig().getGenesisForkVersion();
    final Bytes32 emptyValidatorsRoot = Bytes32.ZERO;
    return genesisSpec.miscHelpers().computeForkDigest(genesisFork, emptyValidatorsRoot);
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
}
