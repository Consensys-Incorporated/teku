/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.builder.rest;

import static tech.pegasys.teku.spec.config.Constants.BUILDER_CALL_TIMEOUT;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;

public class StakedBuilderClientProvider {

  private final Spec spec;
  private final AsyncRunner asyncRunner;

  private final OkHttpClient okHttpClient =
      new OkHttpClient.Builder().callTimeout(BUILDER_CALL_TIMEOUT).build();
  private final Map<String, StakedBuilderClient> clients = new ConcurrentHashMap<>();

  public StakedBuilderClientProvider(final Spec spec, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
  }

  public StakedBuilderClient getClient(final String url) {
    return clients.computeIfAbsent(
        url,
        __ ->
            new OkHttpStakedBuilderClient(
                asyncRunner,
                spec,
                // Trailing slash required so HttpUrl.resolve appends the API path rather than
                // replacing the last segment of the base URL.
                HttpUrl.get(url.endsWith("/") ? url : url + "/"),
                okHttpClient));
  }
}
