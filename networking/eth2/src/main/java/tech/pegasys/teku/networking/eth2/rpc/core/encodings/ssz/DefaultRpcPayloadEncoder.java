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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;

public class DefaultRpcPayloadEncoder<T extends SszData> implements RpcPayloadEncoder<T> {
  private static final Logger LOG = LogManager.getLogger();
  private final SszSchema<T> type;

  public DefaultRpcPayloadEncoder(final SszSchema<T> type) {
    this.type = type;
  }

  @Override
  public Bytes encode(final T message) {
    return message.sszSerialize();
  }

  @Override
  public T decode(final Bytes message) throws RpcException {
    try {
      final T decoded = type.sszDeserialize(message);
      type.getNetworkSszValidator().ifPresent(validator -> validator.validate(decoded));
      return decoded;
    } catch (final SszDeserializeException e) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Failed to parse network message: " + message, e);
      }
      throw new DeserializationFailedException();
    }
  }

  @Override
  public boolean isLengthWithinBounds(final long length) {
    return type.getNetworkSszLengthBounds().isWithinBounds(length);
  }
}
