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

package tech.pegasys.teku.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentTypesTest {
  @Test
  void getResponseContentType_shouldUseFirstSpecifiedItemWhenAnythingSupported() {
    final List<String> supported = List.of("application/json", "application/ssz");
    final Optional<String> result =
        ContentTypes.getResponseContentType(supported, Optional.of("*/*"));
    assertThat(result).contains("application/json");
  }

  @Test
  void getResponseContentType_shouldUseFirstSpecifiedItemWhenAnySubset() {
    final List<String> supported =
        List.of("application/json", "text/plain", "text/other", "application/ssz");
    final Optional<String> result =
        ContentTypes.getResponseContentType(supported, Optional.of("text/*"));
    assertThat(result).contains("text/plain");
  }

  @Test
  void getRequestContentType_shouldThrowExceptionWhenSpecifiedTypeNotSupported() {
    assertThatThrownBy(
            () ->
                ContentTypes.getRequestContentType(
                    Optional.of("application/nope"),
                    List.of("application/json"),
                    "application/json"))
        .isInstanceOf(ContentTypeNotSupportedException.class);
  }

  @Test
  void getRequestContentType_shouldReturnDefaultTypeWhenNoTypeSpecified() {
    assertThat(
            ContentTypes.getRequestContentType(
                Optional.empty(),
                List.of("application/ssz", "application/json", "text/plain"),
                "application/json"))
        .isEqualTo("application/json");
  }

  @Test
  void getRequestContentType_shouldReturnSpecifiedTypeWhenSupported() {
    assertThat(
            ContentTypes.getRequestContentType(
                Optional.of("text/plain"),
                List.of("application/ssz", "application/json", "text/plain"),
                "application/json"))
        .isEqualTo("text/plain");
  }

  @Test
  void getRequestContentType_shouldReturnSpecifiedTypeWhenSupportedWithCharset() {
    assertThat(
            ContentTypes.getRequestContentType(
                Optional.of("text/plain; charset=utf8"),
                List.of("application/ssz", "application/json", "text/plain"),
                "application/json"))
        .isEqualTo("text/plain");
  }
}
