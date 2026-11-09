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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ContextPayloadMismatchException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.storage.client.RecentChainData;

class ForkDigestRpcContextCodec<TPayload extends SszData>
    implements RpcContextCodec<Bytes4, TPayload> {
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final ForkDigestPayloadContext<TPayload> payloadContext;

  ForkDigestRpcContextCodec(
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkDigestPayloadContext<TPayload> payloadContext) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.payloadContext = payloadContext;
  }

  @Override
  public RpcByteBufDecoder<Bytes4> getContextDecoder() {
    return new ForkDigestContextDecoder();
  }

  @Override
  public Bytes encodeContext(final TPayload responsePayload) {
    final UInt64 epoch =
        spec.computeEpochAtSlot(payloadContext.getSlotFromPayload(responsePayload));
    final SpecMilestone milestone = spec.getForkSchedule().getSpecMilestoneAtEpoch(epoch);
    final Optional<BlobParameters> maybeBpoFork = spec.getBpoFork(epoch);
    return recentChainData.getForkDigest(milestone, maybeBpoFork).getWrappedBytes();
  }

  @Override
  public Optional<SszSchema<TPayload>> getSchemaFromContext(final Bytes4 forkDigest) {
    return recentChainData
        .getMilestoneByForkDigest(forkDigest)
        .map(spec::forMilestone)
        .map(SpecVersion::getSchemaDefinitions)
        .map(payloadContext::getSchemaFromSchemaDefinitions);
  }

  @Override
  public void verifyPayloadMatchesContext(final Bytes4 forkDigest, final TPayload responsePayload)
      throws RpcException {
    if (!payloadContext.rejectsContextSlotMismatch()) {
      return;
    }
    final Bytes expectedForkDigest = encodeContext(responsePayload);
    if (!expectedForkDigest.equals(forkDigest.getWrappedBytes())) {
      throw new ContextPayloadMismatchException(
          String.format(
              "fork digest %s but slot %s requires %s",
              forkDigest, payloadContext.getSlotFromPayload(responsePayload), expectedForkDigest));
    }
  }
}
