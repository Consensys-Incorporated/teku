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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

public abstract class AbstractDataColumnSidecarValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator;
  final Peer peer;
  final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider;

  public AbstractDataColumnSidecarValidator(
      final Peer peer,
      final Spec spec,
      final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider) {
    this.peer = peer;
    this.spec = spec;
    this.dataColumnSidecarSignatureValidator = dataColumnSidecarSignatureValidator;
    this.blobKzgCommitmentsProvider = blobKzgCommitmentsProvider;
  }

  boolean verifyValidity(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());
    return dataColumnSidecarUtil.verifyDataColumnSidecarStructure(dataColumnSidecar);
  }

  SafeFuture<Optional<DataColumnSidecarValidationError>> verifyKzgProofs(
      final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarUtil dataColumnSidecarUtil =
        spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());
    return dataColumnSidecarUtil.validateAndVerifyKzgProofs(
        dataColumnSidecar, blobKzgCommitmentsProvider::getBlobKzgCommitments);
  }

  boolean verifyInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    try {
      return spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot())
          .verifyInclusionProof(dataColumnSidecar);
    } catch (final Exception ex) {
      LOG.debug(
          "Inclusion proof verification failed for DataColumnSidecar {}",
          dataColumnSidecar.toLogString(),
          ex);
      return false;
    }
  }

  public SafeFuture<Boolean> verifySignature(final DataColumnSidecar dataColumnSidecar) {
    return dataColumnSidecarSignatureValidator.validateSignature(dataColumnSidecar);
  }
}
