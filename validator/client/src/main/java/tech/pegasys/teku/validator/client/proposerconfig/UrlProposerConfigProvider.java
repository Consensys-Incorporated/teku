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

import java.net.URL;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;

public class UrlProposerConfigProvider extends AbstractProposerConfigProvider {
  private final URL source;

  UrlProposerConfigProvider(
      final AsyncRunner asyncRunner,
      final boolean refresh,
      final ProposerConfigLoader proposerConfigLoader,
      final TimeProvider timeProvider,
      final URL source) {
    super(asyncRunner, refresh, proposerConfigLoader, timeProvider);
    this.source = source;
  }

  @Override
  protected ProposerConfig internalGetProposerConfig() {
    return proposerConfigLoader.getProposerConfig(source);
  }
}
