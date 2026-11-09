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

package tech.pegasys.teku.storage.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.storage.api.StorageUpdate.NonUpdating;

class StorageUpdateTest {

  @SuppressWarnings("unchecked")
  @Test
  void shouldBeEmptyWhenNoValuesSet() throws Exception {
    final Constructor<StorageUpdate> constructor =
        (Constructor<StorageUpdate>) StorageUpdate.class.getDeclaredConstructors()[0];
    final Parameter[] parameters = constructor.getParameters();
    final Object[] values =
        Stream.of(parameters).map(this::createEmptyValue).toArray(Object[]::new);
    final StorageUpdate storageUpdate = constructor.newInstance(values);
    assertThat(storageUpdate.isEmpty()).isTrue();
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldNotBeEmptyWhenAnyValueSet() throws Exception {
    final Constructor<StorageUpdate> constructor =
        (Constructor<StorageUpdate>) StorageUpdate.class.getDeclaredConstructors()[0];
    final Parameter[] parameters = constructor.getParameters();
    final Object[] emptyValues =
        Stream.of(parameters).map(this::createEmptyValue).toArray(Object[]::new);

    int skippedConfigurations = 0;
    for (int i = 0; i < parameters.length; i++) {
      final Parameter parameter = parameters[i];

      // Create a set of constructor args with just the one value not empty
      final Object[] values = Arrays.copyOf(emptyValues, emptyValues.length);
      values[i] = createNonEmptyValue(parameter);

      try {
        final StorageUpdate storageUpdate = constructor.newInstance(values);
        if (isNonUpdatingParameter(parameter)) {
          assertThat(storageUpdate.isEmpty())
              .describedAs(
                  "isEmpty must be true with non-updating parameter %s (%s) set", i, parameter)
              .isTrue();
        } else {
          assertThat(storageUpdate.isEmpty())
              .describedAs("isEmpty must be false with parameter %s (%s) set", i, parameter)
              .isFalse();
        }
      } catch (final InvocationTargetException e) {
        if (!(e.getCause() instanceof IllegalArgumentException)) {
          throw e;
        }
        // Not a valid configuration, skip this case.
        skippedConfigurations++;
      }
    }
    // We know we can't set optimisticTransitionBlockRoot without setting
    // optimisticTransitionBlockRootSet but check that's the only case that failed
    assertThat(skippedConfigurations)
        .describedAs("Number of parameters that resulted in invalid argument exceptions")
        .isEqualTo(1);
  }

  private boolean isNonUpdatingParameter(final Parameter parameter) {
    return parameter.getAnnotationsByType(NonUpdating.class).length > 0;
  }

  private Object createNonEmptyValue(final Parameter parameter) {
    // Note that because Java generics aren't preserved, we cheat and just use any type for content
    if (parameter.getType().isAssignableFrom(Optional.class)) {
      return Optional.of("value");
    } else if (parameter.getType().isAssignableFrom(Map.class)) {
      return Map.of("a", "b");
    } else if (parameter.getType().isAssignableFrom(Set.class)) {
      return Set.of("value");
    } else if (parameter.getType().equals(Boolean.TYPE)) {
      return true;
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + parameter.getType());
  }

  private Object createEmptyValue(final Parameter parameter) {
    if (parameter.getType().isAssignableFrom(Optional.class)) {
      return Optional.empty();
    } else if (parameter.getType().isAssignableFrom(Map.class)) {
      return Collections.emptyMap();
    } else if (parameter.getType().isAssignableFrom(Set.class)) {
      return Collections.emptySet();
    } else if (parameter.getType().equals(Boolean.TYPE)) {
      return false;
    }
    throw new IllegalArgumentException("Unsupported parameter type: " + parameter.getType());
  }
}
