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

package tech.pegasys.teku.infrastructure.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.types.CoreTypes;
import tech.pegasys.teku.infrastructure.json.types.OneOfTypeTestTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class JsonUtilTest {

  @Test
  void getAttribute() throws Exception {
    final Optional<String> result =
        JsonUtil.getAttribute("{\"slot\": \"1234567\"}", CoreTypes.STRING_TYPE, "slot");
    assertThat(result).contains("1234567");
  }

  @Test
  void getAttribute_notFirstField() throws Exception {
    final Optional<String> result =
        JsonUtil.getAttribute(
            "{\"a\":\"zzz\", \"slot\": \"1234567\"}", CoreTypes.STRING_TYPE, "slot");
    assertThat(result).contains("1234567");
  }

  @Test
  void getAttribute_missing() throws Exception {
    final Optional<String> result = JsonUtil.getAttribute("{}", CoreTypes.STRING_TYPE, "slot");
    assertThat(result).isEmpty();
  }

  @Test
  void getAttribute_missingOnlyInChildObject() throws Exception {
    final Optional<String> result =
        JsonUtil.getAttribute("{\"data\": { \"slot\": \"1\"}}", CoreTypes.STRING_TYPE, "slot");
    assertThat(result).isEmpty();
  }

  @Test
  void getAttribute_deepSearch() throws Exception {
    final Optional<String> result =
        JsonUtil.getAttribute(
            "{\"data\": { \"slot\": \"1\"}}", CoreTypes.STRING_TYPE, "data", "slot");
    assertThat(result).contains("1");
  }

  @Test
  void getAttribute_deeperSearch() throws Exception {
    final Optional<String> result =
        JsonUtil.getAttribute(
            "{\"message\": {\"data\": { \"slot\": \"1\"}}}",
            CoreTypes.STRING_TYPE,
            "message",
            "data",
            "slot");
    assertThat(result).contains("1");
  }

  @Test
  void getAttribute_getsAttributeAtParent() throws Exception {
    final Optional<UInt64> result =
        JsonUtil.getAttribute(
            "{\"data\": { \"slot\": \"1\"},"
                + "\"meta\": [ {\"slot\": \"2\"}, {\"slot\": \"3\"}],"
                + " \"slot\":\"1234\"}",
            CoreTypes.UINT64_TYPE,
            "slot");
    assertThat(result).contains(UInt64.valueOf(1234));
  }

  @Test
  void getAttribute_throwsJsonProcessingException() {
    assertThatThrownBy(() -> JsonUtil.getAttribute("{", CoreTypes.STRING_TYPE, "slot"))
        .isInstanceOf(JsonProcessingException.class);
  }

  @Test
  void getAttribute_shouldReadObject() throws Exception {
    final Optional<OneOfTypeTestTypeDefinition.TestObjA> result =
        JsonUtil.getAttribute(
            "{\"data\": " + "{\"value1\":\"FOO\"}}", OneOfTypeTestTypeDefinition.TYPE_A, "data");
    assertThat(result).contains(new OneOfTypeTestTypeDefinition.TestObjA("FOO"));
  }
}
