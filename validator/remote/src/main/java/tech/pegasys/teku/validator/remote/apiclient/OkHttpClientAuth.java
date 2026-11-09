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

package tech.pegasys.teku.validator.remote.apiclient;

import com.google.common.base.Strings;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class OkHttpClientAuth {

  public static void addAuthInterceptor(
      final HttpUrl endpoint, final OkHttpClient.Builder httpClientBuilder) {
    if (endpointContainsCredentials(endpoint)) {
      final Interceptor interceptor =
          chain ->
              chain.proceed(
                  addAuthorizationHeader(chain.request(), createBasicCredentials(endpoint)));
      httpClientBuilder.addInterceptor(interceptor);
    }
  }

  public static void addAuthInterceptorForMultipleEndpoints(
      final List<HttpUrl> endpoints, final OkHttpClient.Builder httpClientBuilder) {
    final Map<String, String> credentialsByHost =
        endpoints.stream()
            .filter(OkHttpClientAuth::endpointContainsCredentials)
            .collect(Collectors.toMap(HttpUrl::host, OkHttpClientAuth::createBasicCredentials));
    if (credentialsByHost.isEmpty()) {
      return;
    }
    final Interceptor interceptor =
        chain -> {
          final String host = chain.request().url().host();
          if (!credentialsByHost.containsKey(host)) {
            return chain.proceed(chain.request());
          }
          return chain.proceed(
              addAuthorizationHeader(chain.request(), credentialsByHost.get(host)));
        };
    httpClientBuilder.addInterceptor(interceptor);
  }

  private static boolean endpointContainsCredentials(final HttpUrl endpoint) {
    return !Strings.isNullOrEmpty(endpoint.password());
  }

  private static String createBasicCredentials(final HttpUrl endpoint) {
    return Credentials.basic(endpoint.username(), endpoint.password());
  }

  private static Request addAuthorizationHeader(final Request request, final String credentials) {
    return request.newBuilder().header("Authorization", credentials).build();
  }
}
