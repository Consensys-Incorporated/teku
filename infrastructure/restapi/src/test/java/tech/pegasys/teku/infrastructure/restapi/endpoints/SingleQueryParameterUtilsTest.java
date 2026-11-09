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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsBytes32;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsBytes32IfPresent;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsInt;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsIntegerIfPresent;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsLong;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsUInt64;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.getParameterValueAsUInt64IfPresent;
import static tech.pegasys.teku.infrastructure.restapi.endpoints.SingleQueryParameterUtils.validateQueryParameter;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SingleQueryParameterUtilsTest {
  public static final String KEY = "any";
  public static final String VALUE = "1";

  public static final Map<String, List<String>> INVALID_DATA = Map.of(KEY, List.of("1.5"));

  @Test
  public void validateParameters_shouldDetectMissingKey() {
    final Map<String, List<String>> data = Map.of();
    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldDetectEmptyString() {
    final Map<String, List<String>> data = Map.of(KEY, List.of());
    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldDetectMultipleEntries() {
    final Map<String, List<String>> data = Map.of(KEY, List.of("1", "2"));
    assertThrows(IllegalArgumentException.class, () -> validateQueryParameter(data, KEY));
  }

  @Test
  public void validateParameters_shouldReturnValue() {
    final Map<String, List<String>> data = Map.of(KEY, List.of(VALUE));
    assertThat(validateQueryParameter(data, KEY)).isEqualTo(VALUE);
  }

  @Test
  public void getParameterValueAsInt_shouldReturnValue() {
    final Map<String, List<String>> data = Map.of(KEY, List.of(VALUE));
    assertThat(getParameterValueAsInt(data, KEY)).isEqualTo(1);
  }

  @Test
  public void getParameterValueAsInt_shouldThrowIllegalArgIfNotIntValue_String() {
    final Map<String, List<String>> data = Map.of(KEY, List.of("not-an-int"));
    assertThatThrownBy(() -> getParameterValueAsInt(data, KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getParameterValueAsInt_shouldThrowIllegalArgIfNotIntValue_Decimal() {
    assertThatThrownBy(() -> getParameterValueAsInt(INVALID_DATA, KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getParameterAsUInt64_shouldReturnValue() {
    final Map<String, List<String>> data = Map.of(KEY, List.of("1"));
    assertThat(getParameterValueAsUInt64(data, KEY)).isEqualTo(ONE);
  }

  @Test
  public void getParameterAsUInt64_shouldThrowIfCannotParse() {
    assertThrows(
        IllegalArgumentException.class, () -> getParameterValueAsUInt64(INVALID_DATA, KEY));
  }

  @Test
  public void getParameterAsLong_shouldReturnValue() {
    Map<String, List<String>> data = Map.of(KEY, List.of("1"));
    assertThat(getParameterValueAsLong(data, KEY)).isEqualTo(1L);
  }

  @Test
  public void getParameterAsLong_shouldThrowIfCannotParse() {
    assertThatThrownBy(() -> getParameterValueAsLong(INVALID_DATA, KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getParameterAsBytes32_shouldThrowIfCannotParse() {
    assertThatThrownBy(() -> getParameterValueAsBytes32(INVALID_DATA, KEY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getParameterAsBytes32_shouldParseHex32String() {
    final Bytes32 bytes32 = Bytes32.random();
    final Map<String, List<String>> data = Map.of(KEY, List.of(bytes32.toHexString()));
    assertThat(getParameterValueAsBytes32(data, KEY)).isEqualTo(bytes32);
  }

  @Test
  public void getParameterAsBytes32IfPresent_shouldReturnEmptyIfNotPresent() {
    assertThat(getParameterValueAsBytes32IfPresent(Map.of(), "t")).isEmpty();
  }

  @Test
  public void getParameterAsBytes32IfPresent_shouldReturnData() {
    final Bytes32 bytes32 = Bytes32.random();
    final Map<String, List<String>> data = Map.of("t", List.of(bytes32.toHexString()));
    assertThat(getParameterValueAsBytes32IfPresent(data, "t")).isEqualTo(Optional.of(bytes32));
  }

  @Test
  public void getParameterAsUInt64IfPresent_shouldReturnEmptyIfNotPresent() {
    assertThat(getParameterValueAsUInt64IfPresent(Map.of(), "t")).isEmpty();
  }

  @Test
  public void getParameterAsUInt64IfPresent_shouldReturnData() {
    final UInt64 value = UInt64.valueOf("123456");
    final Map<String, List<String>> data = Map.of("t", List.of(value.toString()));
    assertThat(getParameterValueAsUInt64IfPresent(data, "t")).isEqualTo(Optional.of(value));
  }

  @Test
  public void getParameterAsIntegerIfPresent_shouldReturnEmptyIfNotPresent() {
    assertThat(getParameterValueAsIntegerIfPresent(Map.of(), "t")).isEmpty();
  }

  @Test
  public void getParameterAsIntegerIfPresent_shouldReturnData() {
    final Map<String, List<String>> data = Map.of("t", List.of("123456"));
    assertThat(getParameterValueAsIntegerIfPresent(data, "t")).isEqualTo(Optional.of(123456));
  }
}
