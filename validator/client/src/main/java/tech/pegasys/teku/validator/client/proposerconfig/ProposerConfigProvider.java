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

package tech.pegasys.teku.validator.client.proposerconfig;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;

public interface ProposerConfigProvider {
  ProposerConfigProvider NOOP = () -> SafeFuture.completedFuture(Optional.empty());

  static ProposerConfigProvider create(
      final AsyncRunner asyncRunner,
      final boolean refresh,
      final ProposerConfigLoader proposerConfigLoader,
      final TimeProvider timeProvider,
      final Optional<String> source) {

    if (source.isPresent()) {
      URL sourceUrl;
      try {
        sourceUrl = URI.create(source.get()).toURL();
        return new UrlProposerConfigProvider(
            asyncRunner, refresh, proposerConfigLoader, timeProvider, sourceUrl);
      } catch (IllegalArgumentException | MalformedURLException e1) {
        try {
          sourceUrl = new File(source.get()).toURI().toURL();
        } catch (MalformedURLException e2) {
          throw new RuntimeException("Unable to translate file to URL", e2);
        }
      }
      return new UrlProposerConfigProvider(
          asyncRunner, refresh, proposerConfigLoader, timeProvider, sourceUrl);
    }
    return NOOP;
  }

  SafeFuture<Optional<ProposerConfig>> getProposerConfig();
}
