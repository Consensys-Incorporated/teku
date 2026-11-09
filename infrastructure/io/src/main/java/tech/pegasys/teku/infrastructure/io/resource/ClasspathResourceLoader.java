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

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class ClasspathResourceLoader extends ResourceLoader {

  private final Class<?> referenceClass;
  // We should constrain resource loading via an explicit whitelist
  // This helps guard against unintentionally loading sensitive resources from the file
  // system (which may be a part of the classpath)
  private final Set<String> availableResources;

  public ClasspathResourceLoader(
      final Class<?> referenceClass,
      final Collection<String> availableResources,
      final Predicate<String> resourceFilter) {
    super(resourceFilter);
    this.referenceClass = referenceClass;
    this.availableResources = new HashSet<>(availableResources);
  }

  @Override
  Optional<InputStream> loadSource(final String source) {
    if (!availableResources.contains(source)) {
      return Optional.empty();
    }
    return Optional.ofNullable(referenceClass.getResourceAsStream(source));
  }
}
