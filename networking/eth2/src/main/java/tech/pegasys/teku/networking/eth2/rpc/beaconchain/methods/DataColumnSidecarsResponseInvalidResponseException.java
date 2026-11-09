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

import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;

public class DataColumnSidecarsResponseInvalidResponseException extends InvalidResponseException {

  public DataColumnSidecarsResponseInvalidResponseException(
      final Peer peer, final InvalidResponseType invalidResponseType) {
    super(formatMessage(peer, invalidResponseType));
  }

  public DataColumnSidecarsResponseInvalidResponseException(
      final Peer peer,
      final InvalidResponseType invalidResponseType,
      final DataColumnSidecarValidationError validationError) {
    super(formatMessage(peer, invalidResponseType, validationError.description()));
  }

  public DataColumnSidecarsResponseInvalidResponseException(
      final Peer peer, final InvalidResponseType invalidResponseType, final Throwable cause) {
    super(formatMessage(peer, invalidResponseType), asException(cause));
  }

  private static String formatMessage(
      final Peer peer, final InvalidResponseType invalidResponseType) {
    return String.format(
        "Received invalid response from peer %s: %s", peer, invalidResponseType.describe());
  }

  private static String formatMessage(
      final Peer peer, final InvalidResponseType invalidResponseType, final String details) {
    return String.format("%s: %s", formatMessage(peer, invalidResponseType), details);
  }

  private static Exception asException(final Throwable cause) {
    return cause instanceof Exception exception ? exception : new RuntimeException(cause);
  }

  public enum InvalidResponseType {
    DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED("Validation of DataColumnSidecar has failed"),
    DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED(
        "KZG verification for DataColumnSidecar has failed"),
    DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED(
        "Inclusion verification for DataColumnSidecar has failed"),
    DATA_COLUMN_SIDECAR_SLOT_NOT_IN_RANGE("DataColumnSidecar's slot is not within requested range"),
    DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER(
        "DataColumnSidecar is not within requested identifiers"),
    DATA_COLUMN_SIDECAR_HEADER_INVALID_SIGNATURE(
        "DataColumnSidecar's beacon block header signature verification failed");

    private final String description;

    InvalidResponseType(final String description) {
      this.description = description;
    }

    public String describe() {
      return description;
    }
  }
}
