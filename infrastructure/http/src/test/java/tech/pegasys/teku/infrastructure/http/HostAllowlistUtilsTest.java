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
import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.getAndValidateHostHeader;
import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.hostIsInAllowlist;
import static tech.pegasys.teku.infrastructure.http.HostAllowlistUtils.isHostAuthorized;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostAllowlistUtilsTest {
  private static final String BAD_HOST = "too.many:colons:5051";

  @Test
  public void getAndValidateHostHeader_should() {
    assertThat(getAndValidateHostHeader("localhost")).contains("localhost");
    assertThat(getAndValidateHostHeader("localhost:5051")).contains("localhost");

    assertThat(getAndValidateHostHeader("")).isEmpty();
    assertThat(getAndValidateHostHeader(null)).isEmpty();
    assertThat(getAndValidateHostHeader(BAD_HOST)).isEmpty();
  }

  @Test
  public void hostIsInAllowlist_shouldBeTrueIfInList() {
    assertThat(hostIsInAllowlist(List.of("A", "B"), "A")).isTrue();
    assertThat(hostIsInAllowlist(List.of("A", "B"), "B")).isTrue();
    assertThat(hostIsInAllowlist(List.of("A", "MIXed.CASE"), "miXED.caSE")).isTrue();
  }

  @Test
  public void hostIsInAllowlist_shouldBeFalseIfNotInList() {
    assertThat(hostIsInAllowlist(List.of("A", "B"), "C")).isFalse();
    assertThat(hostIsInAllowlist(List.of("A", "B"), "AA")).isFalse();
    assertThat(hostIsInAllowlist(List.of("A.B.C", "B"), "A")).isFalse();
    // Empty values
    assertThat(hostIsInAllowlist(List.of("A", "B"), "")).isFalse();
    assertThat(hostIsInAllowlist(List.of("A", "B"), null)).isFalse();
  }

  @Test
  public void isHostAuthorized_shouldBeTrueIfInList() {
    assertThat(isHostAuthorized(List.of("A", "B"), "A")).isTrue();
    assertThat(isHostAuthorized(List.of("A", "B"), "B")).isTrue();
  }

  @Test
  public void isHostAuthorized_shouldBeFalseIfNotInList() {
    assertThat(isHostAuthorized(List.of("A", "B"), "C")).isFalse();
    assertThat(isHostAuthorized(List.of("A", "B"), "AA")).isFalse();
    assertThat(isHostAuthorized(List.of("A.B.C", "B"), "A")).isFalse();
    // Empty values
    assertThat(isHostAuthorized(List.of("A", "B"), "")).isFalse();
    assertThat(isHostAuthorized(List.of("A", "B"), null)).isFalse();
  }

  @Test
  public void isHostAuthorized_shouldBeTrueIfStarInList() {
    assertThat(isHostAuthorized(List.of("*"), "A")).isTrue();
    assertThat(isHostAuthorized(List.of("*"), "my.host.name")).isTrue();
    assertThat(isHostAuthorized(List.of("*", "B"), "A")).isTrue();
    assertThat(isHostAuthorized(List.of("*"), BAD_HOST)).isTrue();
    // Test empty values
    assertThat(isHostAuthorized(List.of("*"), "")).isTrue();
    assertThat(isHostAuthorized(List.of("*"), null)).isTrue();
  }

  @Test
  public void isHostAuthorized_shouldBeFalseIfHostNotCorrectlyFormatted() {
    assertThat(isHostAuthorized(List.of("A", "B"), BAD_HOST)).isFalse();
  }
}
