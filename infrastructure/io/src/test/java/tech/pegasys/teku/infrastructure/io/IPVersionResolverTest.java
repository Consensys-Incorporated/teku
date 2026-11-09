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

package tech.pegasys.teku.infrastructure.io;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tech.pegasys.teku.infrastructure.io.IPVersionResolver.IPVersion;

class IPVersionResolverTest {

  @ParameterizedTest
  @CsvSource({
    "0.0.0.0,IP_V4",
    "1.2.3.4,IP_V4",
    "01.102.103.104,IP_V4",
    "::,IP_V6",
    "::0,IP_V6",
    "2001:db8:3333:4444:5555:6666:7777:8888,IP_V6"
  })
  void resolvesIPVersion(final String ip, final IPVersion expectedVersion) {
    final IPVersion result = IPVersionResolver.resolve(ip);
    assertThat(result).isEqualTo(expectedVersion);
  }
}
