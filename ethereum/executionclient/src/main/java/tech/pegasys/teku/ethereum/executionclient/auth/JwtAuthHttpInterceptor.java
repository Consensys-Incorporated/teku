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

package tech.pegasys.teku.ethereum.executionclient.auth;

import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.Response;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public class JwtAuthHttpInterceptor implements Interceptor {
  private final SafeTokenProvider tokenProvider;

  private final TimeProvider timeProvider;

  public JwtAuthHttpInterceptor(final JwtConfig jwtConfig, final TimeProvider timeProvider) {
    this.tokenProvider = new SafeTokenProvider(new TokenProvider(jwtConfig));
    this.timeProvider = timeProvider;
  }

  @Override
  public Response intercept(final Chain chain) throws IOException {
    final Optional<Token> optionalToken = tokenProvider.token(timeProvider.getTimeInMillis());
    if (optionalToken.isEmpty()) {
      return chain.proceed(chain.request());
    }
    final Token token = optionalToken.get();
    final String authHeader = "Bearer " + token.getJwtToken();
    return chain.proceed(
        chain.request().newBuilder().header(HttpHeaders.AUTHORIZATION, authHeader).build());
  }
}
