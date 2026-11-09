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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JsonTestUtil {

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getObject(
      final Map<String, Object> input, final String... names) {
    Map<String, Object> current = input;
    for (String name : names) {
      assertThat(current).containsKey(name);
      current = (Map<String, Object>) current.get(name);
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> getList(final Map<String, Object> input, final String name) {
    return (List<T>) input.get(name);
  }

  public static Map<String, Object> parse(final String json) throws Exception {
    return new ObjectMapper()
        .readerFor(
            TypeFactory.defaultInstance()
                .constructMapType(LinkedHashMap.class, String.class, Object.class))
        .readValue(json);
  }

  public static Map<String, String> parseStringMap(final String json) throws Exception {
    return new ObjectMapper()
        .readerFor(
            TypeFactory.defaultInstance()
                .constructMapType(LinkedHashMap.class, String.class, String.class))
        .readValue(json);
  }

  public static String parseString(final String json) throws Exception {
    return new ObjectMapper().readerFor(String.class).readValue(json);
  }

  public static List<Object> parseList(final String json) throws Exception {
    return new ObjectMapper()
        .readerFor(
            TypeFactory.defaultInstance()
                .constructCollectionLikeType(ArrayList.class, Object.class))
        .readValue(json);
  }

  public static JsonNode parseAsJsonNode(final String json) throws Exception {
    return new ObjectMapper().readTree(json);
  }
}
