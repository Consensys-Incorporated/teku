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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthDataResolverTest {

  @ParameterizedTest
  @MethodSource("authDataTestCases")
  void shouldResolveDefaultAuthData(final String builderUrl, final String expectedAuthData)
      throws MalformedURLException {
    assertThat(AuthDataResolver.resolveDefaultAuthData(URI.create(builderUrl).toURL()))
        .isEqualTo(expectedAuthData);
  }

  static Stream<Arguments> authDataTestCases() {
    return Stream.of(
        Arguments.of("https://builder.example.com/", "builder.example.com"),
        Arguments.of("HTTPS://Builder.Example.com:443/bids?x=1", "builder.example.com"),
        Arguments.of("https://builder.example.com:8080", "builder.example.com"),
        Arguments.of("https://user:pw@builder.example.com/", "builder.example.com"),
        Arguments.of("https://10.0.0.5:18550/eth/v1/builder", "10.0.0.5"),
        Arguments.of("https://[0:0:0:0:0:0:0:1]:8443/", "[::1]"),
        Arguments.of("https://[2001:db8::192.0.2.1]/", "[2001:db8::c000:201]"),
        Arguments.of("https://bücher.example.org/bids", "xn--bcher-kva.example.org"),
        Arguments.of("https://platåberget.dev", "xn--platberget-45a.dev"));
  }
}
