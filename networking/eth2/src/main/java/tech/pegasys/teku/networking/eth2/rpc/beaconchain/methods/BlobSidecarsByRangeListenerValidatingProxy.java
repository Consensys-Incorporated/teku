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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_SLOT_NOT_IN_RANGE;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_INDEX;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNEXPECTED_SLOT;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_UNKNOWN_PARENT;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class BlobSidecarsByRangeListenerValidatingProxy extends AbstractBlobSidecarsValidator
    implements RpcResponseListener<BlobSidecar> {

  private final RpcResponseListener<BlobSidecar> blobSidecarResponseListener;
  private final Integer maxBlobsPerBlock;
  private final UInt64 startSlot;
  private final UInt64 endSlot;

  private volatile Optional<BlobSidecarSummary> maybeLastBlobSidecarSummary = Optional.empty();

  public BlobSidecarsByRangeListenerValidatingProxy(
      final Spec spec,
      final Peer peer,
      final RpcResponseListener<BlobSidecar> blobSidecarResponseListener,
      final Integer maxBlobsPerBlock,
      final UInt64 startSlot,
      final UInt64 count) {
    super(peer, spec);
    this.blobSidecarResponseListener = blobSidecarResponseListener;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    this.startSlot = startSlot;
    this.endSlot = startSlot.plus(count).minusMinZero(1);
  }

  @Override
  public SafeFuture<?> onResponse(final BlobSidecar blobSidecar) {
    return SafeFuture.of(
        () -> {
          final UInt64 blobSidecarSlot = blobSidecar.getSlot();
          if (!blobSidecarSlotIsInRange(blobSidecarSlot)) {
            throw new BlobSidecarsResponseInvalidResponseException(
                peer, BLOB_SIDECAR_SLOT_NOT_IN_RANGE);
          }

          if (blobSidecar.getIndex().isGreaterThanOrEqualTo(maxBlobsPerBlock)) {
            throw new BlobSidecarsResponseInvalidResponseException(
                peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
          }

          final BlobSidecarSummary blobSidecarSummary = BlobSidecarSummary.create(blobSidecar);
          verifyBlobSidecarIsAfterLast(blobSidecarSummary);

          verifyInclusionProof(blobSidecar);
          verifyKzg(blobSidecar);

          maybeLastBlobSidecarSummary = Optional.of(blobSidecarSummary);
          return blobSidecarResponseListener.onResponse(blobSidecar);
        });
  }

  private boolean blobSidecarSlotIsInRange(final UInt64 blobSidecarSlot) {
    return blobSidecarSlot.isGreaterThanOrEqualTo(startSlot)
        && blobSidecarSlot.isLessThanOrEqualTo(endSlot);
  }

  private void verifyBlobSidecarIsAfterLast(final BlobSidecarSummary blobSidecarSummary) {
    // It's first blobSidecar in response
    if (maybeLastBlobSidecarSummary.isEmpty()) {
      if (!blobSidecarSummary.index.isZero()) {
        throw new BlobSidecarsResponseInvalidResponseException(peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
      }
      return;
    }

    // We have a previous blobSidecar, new could be from the same block
    final BlobSidecarSummary lastBlobSidecarSummary = maybeLastBlobSidecarSummary.orElseThrow();
    if (blobSidecarSummary.inTheSameBlock(lastBlobSidecarSummary)) {
      if (!blobSidecarSummary.index.equals(lastBlobSidecarSummary.index.increment())) {
        throw new BlobSidecarsResponseInvalidResponseException(peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
      }
      return;
    }

    // New blobSidecar is not from the same block
    if (!blobSidecarSummary.index.isZero()) {
      throw new BlobSidecarsResponseInvalidResponseException(peer, BLOB_SIDECAR_UNEXPECTED_INDEX);
    }

    if (blobSidecarSummary.slot.isGreaterThan(lastBlobSidecarSummary.slot.increment())) {
      // a slot has been skipped, we can't check the parent
      return;
    }

    if (blobSidecarSummary.slot.isLessThanOrEqualTo(lastBlobSidecarSummary.slot)) {
      throw new BlobSidecarsResponseInvalidResponseException(peer, BLOB_SIDECAR_UNEXPECTED_SLOT);
    }

    if (!blobSidecarSummary.blockParentRoot.equals(lastBlobSidecarSummary.blockRoot)) {
      throw new BlobSidecarsResponseInvalidResponseException(peer, BLOB_SIDECAR_UNKNOWN_PARENT);
    }
  }

  record BlobSidecarSummary(Bytes32 blockRoot, UInt64 index, UInt64 slot, Bytes32 blockParentRoot) {
    public static BlobSidecarSummary create(final BlobSidecar blobSidecar) {
      return new BlobSidecarSummary(
          blobSidecar.getBlockRoot(),
          blobSidecar.getIndex(),
          blobSidecar.getSlot(),
          blobSidecar.getSignedBeaconBlockHeader().getMessage().getParentRoot());
    }

    public boolean inTheSameBlock(final BlobSidecarSummary blobSidecarSummary) {
      return this.blockParentRoot.equals(blobSidecarSummary.blockParentRoot)
          && this.blockRoot.equals(blobSidecarSummary.blockRoot)
          && this.slot.equals(blobSidecarSummary.slot);
    }
  }
}
