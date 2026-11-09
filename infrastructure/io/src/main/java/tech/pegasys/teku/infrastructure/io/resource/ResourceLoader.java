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

package tech.pegasys.teku.infrastructure.io.resource;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;

public abstract class ResourceLoader {
  private final Predicate<String> sourceFilter;

  protected ResourceLoader(final Predicate<String> sourceFilter) {
    this.sourceFilter = sourceFilter;
  }

  public static ResourceLoader urlOrFile() {
    return urlOrFile(Optional.empty(), __ -> true);
  }

  public static ResourceLoader urlOrFile(final String acceptHeader) {
    checkNotNull(acceptHeader, "acceptHeader cannot be null");
    return urlOrFile(Optional.of(acceptHeader), __ -> true);
  }

  public static ResourceLoader urlOrFile(
      final Optional<String> acceptHeader, final Predicate<String> sourceFilter) {
    return new FallbackResourceLoader(
        sourceFilter,
        new URLResourceLoader(acceptHeader, sourceFilter),
        new FileResourceLoader(sourceFilter));
  }

  public static ResourceLoader classpathUrlOrFile(
      final Class<?> referenceClass,
      final List<String> availableResources,
      final Predicate<String> sourceFilter) {
    return new FallbackResourceLoader(
        sourceFilter,
        new ClasspathResourceLoader(referenceClass, availableResources, sourceFilter),
        new URLResourceLoader(Optional.empty(), sourceFilter),
        new FileResourceLoader(sourceFilter));
  }

  /**
   * Given a list of potential sources, returns a stream of the first available source
   *
   * @param sources A list of potential sources
   * @return The first source that can be successfully read
   */
  public Optional<InputStream> load(final String... sources) throws IOException {
    final List<String> validSources = Arrays.stream(sources).filter(sourceFilter).toList();

    Optional<InputStream> result = Optional.empty();
    for (String validSource : validSources) {
      result = loadSource(validSource);
      if (result.isPresent()) {
        break;
      }
    }

    return result;
  }

  abstract Optional<InputStream> loadSource(String source) throws IOException;

  public Optional<Bytes> loadBytes(final String... sources) throws IOException {
    final Optional<InputStream> maybeStream = load(sources);
    if (maybeStream.isEmpty()) {
      return Optional.empty();
    }
    try (InputStream in = maybeStream.get()) {
      return Optional.of(Bytes.wrap(in.readAllBytes()));
    }
  }
}
