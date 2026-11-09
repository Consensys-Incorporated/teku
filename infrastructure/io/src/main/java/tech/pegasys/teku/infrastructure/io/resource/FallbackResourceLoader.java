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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Predicate;

public class FallbackResourceLoader extends ResourceLoader {

  private final ResourceLoader[] loaders;

  FallbackResourceLoader(final Predicate<String> sourceFilter, final ResourceLoader... loaders) {
    super(sourceFilter);
    this.loaders = loaders;
  }

  @Override
  Optional<InputStream> loadSource(final String source) throws IOException {
    for (ResourceLoader loader : loaders) {
      final Optional<InputStream> resource = loader.loadSource(source);
      if (resource.isPresent()) {
        return resource;
      }
    }
    return Optional.empty();
  }
}
