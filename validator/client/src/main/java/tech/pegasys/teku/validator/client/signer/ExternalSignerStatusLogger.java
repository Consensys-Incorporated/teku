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

package tech.pegasys.teku.validator.client.signer;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

public class ExternalSignerStatusLogger {
  private static final Logger LOG = LogManager.getLogger();
  private static final Duration FIXED_DELAY = Duration.ofSeconds(30);
  private final StatusLogger statusLogger;
  private final BooleanSupplier upcheckSupplier;
  private final URL signingServiceUrl;
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean isReachable = new AtomicBoolean(false);

  public ExternalSignerStatusLogger(
      final StatusLogger statusLogger,
      final BooleanSupplier upcheckSupplier,
      final URL signingServiceUrl,
      final AsyncRunner asyncRunner) {
    this.statusLogger = statusLogger;
    this.upcheckSupplier = upcheckSupplier;
    this.signingServiceUrl = signingServiceUrl;
    this.asyncRunner = asyncRunner;
  }

  public void logWithFixedDelay() {
    asyncRunner.runWithFixedDelay(
        this::log,
        FIXED_DELAY,
        err -> LOG.debug("Unexpected error calling external signer upcheck", err));
  }

  public void log() {
    final boolean upcheckStatus = upcheckSupplier.getAsBoolean();
    if (upcheckStatus) {
      if (isReachable.compareAndSet(false, true)) {
        statusLogger.externalSignerStatus(signingServiceUrl, true);
      }
    } else {
      isReachable.set(false);
      statusLogger.externalSignerStatus(signingServiceUrl, false);
    }
  }
}
