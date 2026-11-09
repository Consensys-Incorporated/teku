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

package tech.pegasys.teku.ethereum.executionclient;

import java.time.Duration;
import java.util.Optional;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtAuthHttpInterceptor;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class OkHttpClientCreator {

  // The timeout used by default for call and read timeouts when not specified
  // Note: this can be overridden per-request (e.g. for getPayload or newPayload
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(1);

  public static OkHttpClient create(
      final Logger logger, final Optional<JwtConfig> jwtConfig, final TimeProvider timeProvider) {
    return create(DEFAULT_TIMEOUT, logger, jwtConfig, timeProvider);
  }

  public static OkHttpClient create(
      final Duration timeout,
      final Logger logger,
      final Optional<JwtConfig> jwtConfig,
      final TimeProvider timeProvider) {
    final OkHttpClient.Builder builder =
        new OkHttpClient.Builder().callTimeout(timeout).readTimeout(timeout);
    if (logger.isTraceEnabled()) {
      final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(logger::trace);
      loggingInterceptor.setLevel(Level.BODY);
      builder.addInterceptor(loggingInterceptor);
    }
    jwtConfig.ifPresent(
        config -> builder.addInterceptor(new JwtAuthHttpInterceptor(config, timeProvider)));
    return builder.build();
  }
}
