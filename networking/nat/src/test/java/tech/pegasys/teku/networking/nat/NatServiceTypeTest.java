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

package tech.pegasys.teku.networking.nat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NatServiceTypeTest {

  @Test
  void shouldThrowExceptionIfServiceTypeNotValid() {
    assertThatThrownBy(() -> NatServiceType.fromString("invalid"))
        .hasMessageContaining("Invalid NAT service type");
  }

  @ParameterizedTest
  @MethodSource("stringToNatMethod")
  void shouldAcceptValidValues(final String natString, final NatServiceType natServiceType) {
    assertThat(NatServiceType.fromString(natString)).isEqualTo(natServiceType);
  }

  public static Stream<Arguments> stringToNatMethod() {
    Stream.Builder<Arguments> builder = Stream.builder();

    builder.add(Arguments.of("teku_discovery", NatServiceType.TEKU_DISCOVERY));
    builder.add(Arguments.of("teku_p2p", NatServiceType.TEKU_P2P));
    builder.add(Arguments.of("TEKU_DISCOVERY", NatServiceType.TEKU_DISCOVERY));
    builder.add(Arguments.of("TEKU_P2P", NatServiceType.TEKU_P2P));

    return builder.build();
  }
}
