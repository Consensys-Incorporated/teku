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

package tech.pegasys.teku.validator.remote;

import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.convertToOkHttpUrls;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import okhttp3.HttpUrl;

public class RemoteBeaconNodeEndpoints {

  private final List<HttpUrl> endpoints;

  public RemoteBeaconNodeEndpoints(final List<URI> endpoints) {
    this.endpoints = new ArrayList<>(stripAuthentication(convertToOkHttpUrls(endpoints)));
  }

  public HttpUrl getPrimaryEndpoint() {
    return endpoints.get(0);
  }

  public List<HttpUrl> getFailoverEndpoints() {
    return endpoints.subList(1, endpoints.size());
  }

  private List<HttpUrl> stripAuthentication(final List<HttpUrl> endpoints) {
    return endpoints.stream()
        .map(endpoint -> endpoint.newBuilder().username("").password("").build())
        .toList();
  }
}
