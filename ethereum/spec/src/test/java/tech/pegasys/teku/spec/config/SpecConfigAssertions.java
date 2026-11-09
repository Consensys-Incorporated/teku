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

package tech.pegasys.teku.spec.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpecConfigAssertions {

  static void assertAllAltairFieldsSet(final SpecConfig config) throws Exception {
    assertAllFieldsSet(config, SpecConfigAltair.class);
  }

  static void assertAllBellatrixFieldsSet(final SpecConfig config) throws Exception {
    assertAllFieldsSet(config, SpecConfigBellatrix.class);
  }

  static void assertAllFieldsSet(final SpecConfig config, final Class<?> targetConfig)
      throws Exception {
    for (Method method : listGetters(targetConfig)) {
      final Object value = method.invoke(config);
      assertThat(value).describedAs(method.getName().substring(3)).isNotNull();
    }
  }

  private static List<Method> listGetters(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()))
        .filter(m -> m.getParameterTypes().length == 0)
        .filter(m -> m.getName().startsWith("get"))
        .filter(m -> !m.getName().endsWith("InEffect"))
        .collect(Collectors.toList());
  }
}
