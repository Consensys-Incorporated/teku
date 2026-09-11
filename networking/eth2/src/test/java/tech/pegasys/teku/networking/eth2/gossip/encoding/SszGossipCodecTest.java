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

package tech.pegasys.teku.networking.eth2.gossip.encoding;

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
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszNetworkValidator;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;

class SszGossipCodecTest {

  @Test
  void decode_shouldUseNetworkSszLengthBounds() throws DecodingException {
    final SszLengthBounds networkBounds = SszLengthBounds.ofBytes(8, 16);
    @SuppressWarnings("unchecked")
    final SszSchema<SszData> schema = mock(SszSchema.class);
    final SszData expected = mock(SszData.class);
    doReturn(networkBounds).when(schema).getNetworkSszLengthBounds();
    doReturn(expected).when(schema).sszDeserialize(any(Bytes.class));

    final SszData actual = new SszGossipCodec().decode(Bytes.random(12), schema);

    assertThat(actual).isSameAs(expected);
    verify(schema).getNetworkSszLengthBounds();
    verify(schema, never()).getSszLengthBounds();
  }

  @Test
  void decode_shouldRunNetworkSszValidatorOnDecodedValue() throws DecodingException {
    @SuppressWarnings("unchecked")
    final SszSchema<SszData> schema = mock(SszSchema.class);
    @SuppressWarnings("unchecked")
    final SszNetworkValidator<SszData> validator = mock(SszNetworkValidator.class);
    final SszData expected = mock(SszData.class);
    doReturn(SszLengthBounds.ofBytes(0, 100)).when(schema).getNetworkSszLengthBounds();
    doReturn(expected).when(schema).sszDeserialize(any(Bytes.class));
    doReturn(Optional.of(validator)).when(schema).getNetworkSszValidator();

    final SszData actual = new SszGossipCodec().decode(Bytes.random(12), schema);

    assertThat(actual).isSameAs(expected);
    verify(validator).validate(expected);
  }

  @Test
  void decode_shouldFailWhenNetworkSszValidatorRejectsValue() {
    @SuppressWarnings("unchecked")
    final SszSchema<SszData> schema = mock(SszSchema.class);
    final SszData decoded = mock(SszData.class);
    doReturn(SszLengthBounds.ofBytes(0, 100)).when(schema).getNetworkSszLengthBounds();
    doReturn(decoded).when(schema).sszDeserialize(any(Bytes.class));
    final SszNetworkValidator<SszData> validator =
        __ -> {
          throw new SszDeserializeException("too many things");
        };
    doReturn(Optional.of(validator)).when(schema).getNetworkSszValidator();

    assertThatThrownBy(() -> new SszGossipCodec().decode(Bytes.random(12), schema))
        .isInstanceOf(DecodingException.class)
        .hasRootCauseMessage("too many things");
  }
}
