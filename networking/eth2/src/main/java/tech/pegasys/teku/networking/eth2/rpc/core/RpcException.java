/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.RESOURCE_UNAVAILABLE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SERVER_ERROR_CODE;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcErrorMessage;

public class RpcException extends Exception {
  private static final Logger LOG = LogManager.getLogger();

  public static final int MAXIMUM_ERROR_MESSAGE_LENGTH = 256;

  // Server errors
  public static class ServerErrorException extends RpcException {
    public ServerErrorException() {
      super(SERVER_ERROR_CODE, "Unexpected error");
    }
  }

  /**
   * Raised locally when data received from a peer cannot be decoded as the protocol requires. As
   * opposed to an error response sent by the peer, this indicates the peer is misbehaving.
   */
  public abstract static class MalformedDataException extends RpcException {
    protected MalformedDataException(final String errorMessage) {
      super(INVALID_REQUEST_CODE, errorMessage);
    }
  }

  // Malformed data
  public static class DeserializationFailedException extends MalformedDataException {
    public DeserializationFailedException() {
      super("Failed to deserialize payload");
    }
  }

  public static class DecompressFailedException extends MalformedDataException {
    public DecompressFailedException() {
      super("Failed to uncompress message");
    }
  }

  public static class UnrecognizedContextBytesException extends MalformedDataException {
    public UnrecognizedContextBytesException(final String context) {
      super(
          "Failed to recognize context bytes: "
              + context
              + ". Must request blocks with compatible fork.");
    }
  }

  // Unexpected message length
  public static class ExtraDataAppendedException extends MalformedDataException {
    public ExtraDataAppendedException() {
      super("Extra data appended to end of message");
    }

    public ExtraDataAppendedException(final String details) {
      super("Extra data appended to end of message: " + details);
    }
  }

  public static class MessageTruncatedException extends MalformedDataException {
    public MessageTruncatedException() {
      super("Message was truncated");
    }
  }

  public static class PayloadTruncatedException extends MalformedDataException {
    public PayloadTruncatedException() {
      super("Message payload smaller than expected");
    }
  }

  public static class AdditionalDataReceivedException extends MalformedDataException {
    public AdditionalDataReceivedException() {
      super("Received additional response after request completed");
    }
  }

  // Constraint violation
  public static class ChunkTooLongException extends MalformedDataException {
    public ChunkTooLongException() {
      super("Chunk exceeds maximum allowed length");
    }
  }

  public static class InvalidRpcMethodVersion extends RpcException {
    public InvalidRpcMethodVersion(final String errorMessage) {
      super(INVALID_REQUEST_CODE, errorMessage);
    }
  }

  // Unavailable data

  public static class ResourceUnavailableException extends RpcException {
    public ResourceUnavailableException(final String errorMessage) {
      super(RESOURCE_UNAVAILABLE, errorMessage);
    }
  }

  // Custom errors

  public static class LengthOutOfBoundsException extends MalformedDataException {
    public LengthOutOfBoundsException() {
      super("Chunk length is not within bounds for expected type");
    }
  }

  private final byte responseCode;
  private final String errorMessage;

  public RpcException(final byte responseCode, final String errorMessage) {
    super("[Code " + ((int) responseCode) + "] " + errorMessage);
    this.responseCode = responseCode;
    this.errorMessage = errorMessage;
  }

  public RpcException(final byte responseCode, final RpcErrorMessage errorMessage) {
    this(responseCode, errorMessage.toString());
  }

  public byte getResponseCode() {
    return responseCode;
  }

  public String getErrorMessageString() {
    return errorMessage;
  }

  public RpcErrorMessage getErrorMessage() {
    Bytes bytes = Bytes.wrap(errorMessage.getBytes(UTF_8));
    if (bytes.size() > MAXIMUM_ERROR_MESSAGE_LENGTH) {
      LOG.debug("Message {} was longer than {} bytes", errorMessage, MAXIMUM_ERROR_MESSAGE_LENGTH);
      return new RpcErrorMessage(bytes.slice(0, MAXIMUM_ERROR_MESSAGE_LENGTH));
    }
    return new RpcErrorMessage(bytes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RpcException that = (RpcException) o;
    return responseCode == that.responseCode && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(responseCode, errorMessage);
  }
}
