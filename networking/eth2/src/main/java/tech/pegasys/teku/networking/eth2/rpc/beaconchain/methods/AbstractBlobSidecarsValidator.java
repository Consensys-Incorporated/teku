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

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsResponseInvalidResponseException.InvalidResponseType.BLOB_SIDECAR_KZG_VERIFICATION_FAILED;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;

public class AbstractBlobSidecarsValidator {

  private static final Logger LOG = LogManager.getLogger();

  protected final Peer peer;
  protected final Spec spec;

  public AbstractBlobSidecarsValidator(final Peer peer, final Spec spec) {
    this.peer = peer;
    this.spec = spec;
  }

  protected void verifyKzg(final BlobSidecar blobSidecar) {
    if (!verifyBlobKzgProof(blobSidecar)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_KZG_VERIFICATION_FAILED);
    }
  }

  protected void verifyInclusionProof(final BlobSidecar blobSidecar) {
    if (!verifyBlobSidecarInclusionProof(blobSidecar)) {
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED);
    }
  }

  private boolean verifyBlobKzgProof(final BlobSidecar blobSidecar) {
    try {
      return spec.atSlot(blobSidecar.getSlot()).miscHelpers().verifyBlobKzgProof(blobSidecar);
    } catch (final Exception ex) {
      LOG.debug("KZG verification failed for BlobSidecar {}", blobSidecar.toLogString());
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_KZG_VERIFICATION_FAILED, ex);
    }
  }

  private boolean verifyBlobSidecarInclusionProof(final BlobSidecar blobSidecar) {
    try {
      return MiscHelpersDeneb.required(spec.atSlot(blobSidecar.getSlot()).miscHelpers())
          .verifyBlobKzgCommitmentInclusionProof(blobSidecar);
    } catch (final Exception ex) {
      LOG.debug(
          "Block inclusion proof verification failed for BlobSidecar {}",
          blobSidecar.toLogString());
      throw new BlobSidecarsResponseInvalidResponseException(
          peer, BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED, ex);
    }
  }
}
