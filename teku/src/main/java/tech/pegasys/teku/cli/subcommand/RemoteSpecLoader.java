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

package tech.pegasys.teku.cli.subcommand;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuth;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorMinimalTypeDefClient;

class RemoteSpecLoader {

  private static final long RETRY_DELAY = 5000;

  static Spec getSpecWithRetry(final List<URI> beaconEndpoints) {
    return retry(() -> getSpec(beaconEndpoints));
  }

  static Spec getSpec(final List<URI> beaconEndpoints) {
    if (beaconEndpoints.size() > 1) {
      return getSpecWithFailovers(createTypeDefClients(beaconEndpoints));
    }
    return getSpec(createTypeDefClients(beaconEndpoints).get(0));
  }

  static Spec getSpec(final OkHttpValidatorMinimalTypeDefClient apiClient) {
    try {
      return apiClient
          .getSpec()
          .map(config -> SpecConfigLoader.loadRemoteConfig(new HashMap<>(config)))
          .map(SpecFactory::create)
          .orElseThrow();
    } catch (final Throwable ex) {
      final String errMsg =
          String.format(
              "Failed to retrieve network spec from beacon node endpoint '%s'.\nDetails: %s."
                  + "\nEnsure local and remote software versions are up-to-date.",
              apiClient.getBaseEndpoint(), ex.getMessage());
      throw new InvalidConfigurationException(errMsg, ex);
    }
  }

  static OkHttpValidatorMinimalTypeDefClient createTypeDefClient(final URI endpoint) {
    return createTypeDefClients(List.of(endpoint)).get(0);
  }

  private static Spec getSpecWithFailovers(
      final List<OkHttpValidatorMinimalTypeDefClient> apiClients) {
    for (final OkHttpValidatorMinimalTypeDefClient apiClient : apiClients) {
      try {
        return getSpec(apiClient);
      } catch (final Throwable ex) {
        logError(ex);
      }
    }
    final String errMsg =
        "Failed to retrieve network spec from all configured beacon node endpoints.";
    throw new InvalidConfigurationException(errMsg);
  }

  private static <T> T retry(final Callable<T> callable) {
    try {
      return callable.call();
    } catch (Throwable ex) {
      logError(ex);
      sleep();
      return retry(callable);
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(RETRY_DELAY);
    } catch (final InterruptedException ex) {
      throw new ShuttingDownException();
    }
  }

  private static void logError(final Throwable ex) {
    SubCommandLogger.SUB_COMMAND_LOG.error(ex.getMessage());
  }

  private static List<OkHttpValidatorMinimalTypeDefClient> createTypeDefClients(
      final List<URI> baseEndpoints) {
    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS);
    List<HttpUrl> apiEndpoints = baseEndpoints.stream().map(HttpUrl::get).toList();
    if (apiEndpoints.size() > 1) {
      OkHttpClientAuth.addAuthInterceptorForMultipleEndpoints(apiEndpoints, httpClientBuilder);
    } else {
      OkHttpClientAuth.addAuthInterceptor(apiEndpoints.get(0), httpClientBuilder);
    }
    // Strip any authentication info from the URL(s) to ensure it doesn't get logged.
    apiEndpoints = stripAuthentication(apiEndpoints);
    final OkHttpClient okHttpClient = httpClientBuilder.build();
    return apiEndpoints.stream()
        .map(apiEndpoint -> new OkHttpValidatorMinimalTypeDefClient(apiEndpoint, okHttpClient))
        .toList();
  }

  private static List<HttpUrl> stripAuthentication(final List<HttpUrl> endpoints) {
    return endpoints.stream()
        .map(endpoint -> endpoint.newBuilder().username("").password("").build())
        .toList();
  }
}
