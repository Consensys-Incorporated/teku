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

import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema;

public class OkHttpStakedBuilderClientProvider implements StakedBuilderClientProvider {

  private final Spec spec;

  private final OkHttpClient okHttpClient =
      new OkHttpClient.Builder()
          // Default pool (5 connections, 5-min keep-alive) is too small and short-lived:
          // connections
          // expire mid-epoch (~6.4 min), forcing TCP/TLS re-handshakes during posting. Size the
          // pool to one connection per known builder with a keep-alive that outlasts an epoch.
          .connectionPool(
              new ConnectionPool(
                  (int) BuilderConfigSchema.MAX_BUILDER_ENTRIES, 10, TimeUnit.MINUTES))
          .callTimeout(BUILDER_CALL_TIMEOUT)
          .build();
  private final LRUCache<String, StakedBuilderClient> clients =
      // reuse MAX_BUILDER_ENTRIES for the clients cache capacity
      LRUCache.create((int) BuilderConfigSchema.MAX_BUILDER_ENTRIES);

  public OkHttpStakedBuilderClientProvider(final Spec spec) {
    this.spec = spec;
  }

  @Override
  public StakedBuilderClient getClient(final String url) {
    return clients.get(
        url,
        __ ->
            new OkHttpStakedBuilderClient(
                spec,
                // Trailing slash required so HttpUrl.resolve appends the API path rather than
                // replacing the last segment of the base URL.
                HttpUrl.get(url.endsWith("/") ? url : url + "/"),
                okHttpClient));
  }
}
