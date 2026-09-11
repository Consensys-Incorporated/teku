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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz.DefaultRpcPayloadEncoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;

public class DefaultRpcPayloadEncoderTest {
  private final Spec spec = TestSpecFactory.createDefault();

  @SuppressWarnings({"rawtypes", "unchecked"})
  private final DefaultRpcPayloadEncoder<StatusMessage> statusMessageEncoder =
      new DefaultRpcPayloadEncoder<>(
          (StatusMessageSchema) spec.getGenesisSchemaDefinitions().getStatusMessageSchema());

  @Test
  public void decode_truncatedMessage() {
    final StatusMessage statusMessage = StatusMessagePhase0.createPreGenesisStatus(spec);
    final Bytes encoded = statusMessageEncoder.encode(statusMessage);

    for (int i = 0; i < encoded.size(); i++) {
      final Bytes truncated = encoded.slice(0, encoded.size() - 1);
      assertThatThrownBy(() -> statusMessageEncoder.decode(truncated))
          .isInstanceOf(DeserializationFailedException.class);
    }
  }

  @Test
  public void isLengthWithinBounds_shouldUseNetworkSszLengthBounds() {
    @SuppressWarnings("unchecked")
    final SszSchema<StatusMessage> schema = mock(SszSchema.class);
    final SszLengthBounds networkBounds = SszLengthBounds.ofBytes(8, 16);
    doReturn(networkBounds).when(schema).getNetworkSszLengthBounds();
    final DefaultRpcPayloadEncoder<StatusMessage> encoder = new DefaultRpcPayloadEncoder<>(schema);

    assertThat(encoder.isLengthWithinBounds(16)).isTrue();

    verify(schema).getNetworkSszLengthBounds();
    verify(schema, never()).getSszLengthBounds();
  }

  @Test
  public void decode_shouldRunNetworkSszValidatorOnDecodedValue() throws Exception {
    @SuppressWarnings("unchecked")
    final SszSchema<StatusMessage> schema = mock(SszSchema.class);
    @SuppressWarnings("unchecked")
    final SszNetworkValidator<StatusMessage> validator = mock(SszNetworkValidator.class);
    final StatusMessage expected = StatusMessagePhase0.createPreGenesisStatus(spec);
    doReturn(expected).when(schema).sszDeserialize(any(Bytes.class));
    doReturn(Optional.of(validator)).when(schema).getNetworkSszValidator();

    final StatusMessage actual = new DefaultRpcPayloadEncoder<>(schema).decode(Bytes.random(8));

    assertThat(actual).isSameAs(expected);
    verify(validator).validate(expected);
  }

  @Test
  public void decode_shouldFailWhenNetworkSszValidatorRejectsValue() {
    @SuppressWarnings("unchecked")
    final SszSchema<StatusMessage> schema = mock(SszSchema.class);
    final StatusMessage decoded = StatusMessagePhase0.createPreGenesisStatus(spec);
    doReturn(decoded).when(schema).sszDeserialize(any(Bytes.class));
    final SszNetworkValidator<StatusMessage> validator =
        __ -> {
          throw new SszDeserializeException("too many things");
        };
    doReturn(Optional.of(validator)).when(schema).getNetworkSszValidator();

    assertThatThrownBy(() -> new DefaultRpcPayloadEncoder<>(schema).decode(Bytes.random(8)))
        .isInstanceOf(DeserializationFailedException.class);
  }
}
