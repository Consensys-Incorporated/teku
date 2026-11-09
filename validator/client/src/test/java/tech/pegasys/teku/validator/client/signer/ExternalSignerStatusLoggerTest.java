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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

class ExternalSignerStatusLoggerTest {
  private final StatusLogger statusLogger = mock(StatusLogger.class);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final AtomicBoolean externalSignerUpcheck = new AtomicBoolean();
  private static final URL SIGNER_URL;

  static {
    try {
      SIGNER_URL = URI.create("http://localhost:9000").toURL();
    } catch (final MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private ExternalSignerStatusLogger signerStatusLogger;

  @BeforeEach
  void setup() {
    signerStatusLogger =
        new ExternalSignerStatusLogger(
            statusLogger, externalSignerUpcheck::get, SIGNER_URL, asyncRunner);
    reset(statusLogger);
  }

  @Test
  void logOnlyOnceWhenExternalSignerIsReachable() {
    externalSignerUpcheck.set(true);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(1)).externalSignerStatus(any(), ArgumentMatchers.eq(true));
  }

  @Test
  void logEverytimeWhenExternalSignerIsNotReachable() {
    externalSignerUpcheck.set(false);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(2)).externalSignerStatus(any(), ArgumentMatchers.eq(false));
  }

  @Test
  void logWhenStatusChanges() {
    externalSignerUpcheck.set(true);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(1)).externalSignerStatus(any(), ArgumentMatchers.eq(true));
    reset(statusLogger);

    externalSignerUpcheck.set(false);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(2)).externalSignerStatus(any(), ArgumentMatchers.eq(false));
    reset(statusLogger);

    externalSignerUpcheck.set(true);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(1)).externalSignerStatus(any(), ArgumentMatchers.eq(true));
    reset(statusLogger);

    externalSignerUpcheck.set(false);
    signerStatusLogger.log();
    signerStatusLogger.log();
    verify(statusLogger, Mockito.times(2)).externalSignerStatus(any(), ArgumentMatchers.eq(false));
    reset(statusLogger);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void logWithFixDelayCallsLogWithUpcheckStatus(final boolean upcheck) {
    externalSignerUpcheck.set(upcheck);
    signerStatusLogger.logWithFixedDelay();
    Assertions.assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    verify(statusLogger, Mockito.times(1))
        .externalSignerStatus(any(), ArgumentMatchers.eq(upcheck));
  }
}
