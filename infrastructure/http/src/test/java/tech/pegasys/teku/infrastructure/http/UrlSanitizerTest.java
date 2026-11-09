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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UrlSanitizerTest {
  @Test
  void shouldRemoveBasicCredentialsFromUrl() {
    final String input = "http://user:pass@localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("http://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldRemoveBasicCredentialsFromUrlWithUnusualScheme() {
    final String input = "yasf://user:pass@localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("yasf://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldNotModifyUrlWithNoCredentials() {
    final String input = "http://localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("http://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldNotModifyStringThatIsNotAUrl() {
    final String input = "user:passlocalhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("user:passlocalhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldDetectPathInUrl() {
    final String input = "https://someurl.xyz/path";
    assertThat(UrlSanitizer.urlContainsNonEmptyPath(input)).isTrue();
  }

  @Test
  void shouldDetectNoPathInUrl() {
    final String input = "https://someurl.xyz";
    assertThat(UrlSanitizer.urlContainsNonEmptyPath(input)).isFalse();
  }

  @Test
  void shouldIgnoreEmptyPath() {
    final String input = "https://someurl.xyz/";
    assertThat(UrlSanitizer.urlContainsNonEmptyPath(input)).isFalse();
  }

  @Test
  void shouldNotDetectPathInStringThatIsNonUrl() {
    final String input = "\\not-a-url/path";
    assertThat(UrlSanitizer.urlContainsNonEmptyPath(input)).isFalse();
  }

  @Test
  public void AppendPathShouldFailWithNullUrl() {
    assertThatThrownBy(() -> UrlSanitizer.appendPath(null, "foo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void AppendPathShouldFailWithEmptyUrl() {
    assertThatThrownBy(() -> UrlSanitizer.appendPath("", "foo"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("appendDataParams")
  public void shouldCreateCorrectUriWithPath(
      final String url, final String path, final String expected) {
    assertThat(UrlSanitizer.appendPath(url, path)).isEqualTo(expected);
  }

  private static Stream<Arguments> appendDataParams() {
    return Stream.of(
        Arguments.of("http://foo.com", "hello", "http://foo.com/hello"),
        Arguments.of("http://foo.com/", "hello", "http://foo.com/hello"),
        Arguments.of("http://foo.com", null, "http://foo.com"),
        Arguments.of("http://foo.com", "", "http://foo.com"));
  }
}
