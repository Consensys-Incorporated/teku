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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.client.ProposerConfig;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;

public abstract class AbstractProposerConfigProvider implements ProposerConfigProvider {
  private static final Logger LOG = LogManager.getLogger();

  public static final int LAST_PROPOSER_CONFIG_VALIDITY_PERIOD = 300;

  private final boolean refresh;
  private final AsyncRunner asyncRunner;
  protected final ProposerConfigLoader proposerConfigLoader;
  private final TimeProvider timeProvider;
  private Optional<ProposerConfig> lastProposerConfig = Optional.empty();
  private UInt64 lastProposerConfigTimeStamp = UInt64.ZERO;
  private SafeFuture<Optional<ProposerConfig>> futureProposerConfig =
      SafeFuture.completedFuture(Optional.empty());

  AbstractProposerConfigProvider(
      final AsyncRunner asyncRunner,
      final boolean refresh,
      final ProposerConfigLoader proposerConfigLoader,
      final TimeProvider timeProvider) {
    this.asyncRunner = asyncRunner;
    this.refresh = refresh;
    this.proposerConfigLoader = proposerConfigLoader;
    this.timeProvider = timeProvider;
  }

  @Override
  public synchronized SafeFuture<Optional<ProposerConfig>> getProposerConfig() {
    if (lastProposerConfig.isPresent() && !refresh) {
      return SafeFuture.completedFuture(lastProposerConfig);
    }

    if (!futureProposerConfig.isDone()) {
      // a proposer config reload is in progress, use that as result
      return futureProposerConfig;
    }

    if (lastProposerConfig.isPresent()
        && lastProposerConfigTimeStamp.isGreaterThan(
            timeProvider.getTimeInSeconds().minus(LAST_PROPOSER_CONFIG_VALIDITY_PERIOD))) {
      // last proposer config is still valid
      return SafeFuture.completedFuture(lastProposerConfig);
    }

    futureProposerConfig =
        asyncRunner
            .runAsync(this::internalGetProposerConfig)
            .orTimeout(30, TimeUnit.SECONDS)
            .thenApply(this::updateProposerConfig)
            .exceptionally(this::handleException);

    return futureProposerConfig;
  }

  private synchronized Optional<ProposerConfig> handleException(final Throwable throwable) {
    if (lastProposerConfig.isPresent()) {
      LOG.warn("An error occurred while obtaining config, providing last loaded config", throwable);
      return lastProposerConfig;
    }
    throw new RuntimeException(
        "An error occurred while obtaining config and there is no previously loaded config",
        throwable);
  }

  private synchronized Optional<ProposerConfig> updateProposerConfig(
      final ProposerConfig proposerConfig) {
    lastProposerConfig = Optional.of(proposerConfig);
    lastProposerConfigTimeStamp = timeProvider.getTimeInSeconds();
    LOG.info(
        "Proposer config successfully loaded. It contains the default configuration and {} specific configuration(s).",
        proposerConfig.getNumberOfProposerConfigs());
    return lastProposerConfig;
  }

  protected abstract ProposerConfig internalGetProposerConfig();
}
