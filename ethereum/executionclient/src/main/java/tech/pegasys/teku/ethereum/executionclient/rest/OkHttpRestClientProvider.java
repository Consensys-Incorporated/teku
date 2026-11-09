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

package tech.pegasys.teku.ethereum.executionclient.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Duration;
import java.util.Optional;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.OkHttpClientCreator;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

class OkHttpRestClientProvider implements RestClientProvider {

  private static final Logger LOG = LogManager.getLogger();

  private final OkHttpRestClient okHttpRestClient;

  OkHttpRestClientProvider(
      final String endpoint,
      final Duration timeout,
      final Optional<JwtConfig> jwtConfig,
      final TimeProvider timeProvider) {
    checkNotNull(endpoint);
    final OkHttpClient okHttpClient =
        OkHttpClientCreator.create(timeout, LOG, jwtConfig, timeProvider);
    this.okHttpRestClient = new OkHttpRestClient(okHttpClient, endpoint);
  }

  @Override
  public RestClient getRestClient() {
    return okHttpRestClient;
  }

  @Override
  public boolean isStub() {
    return false;
  }
}
