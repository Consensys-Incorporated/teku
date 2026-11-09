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

package tech.pegasys.teku.data.publisher;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MetricsPublisher {

  private static final Logger LOG = LogManager.getLogger();
  private static final MediaType MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
  private final OkHttpClient client;
  private final HttpUrl endpoint;

  public MetricsPublisher(final OkHttpClient client, final HttpUrl endpoint) {
    this.client = client;
    this.endpoint = endpoint;
  }

  public void publishMetrics(final String json) throws IOException {
    RequestBody body = RequestBody.create(json, MEDIA_TYPE);
    Request request = new Request.Builder().url(endpoint).post(body).build();
    Response response = this.client.newCall(request).execute();
    int responseCode = response.code();
    response.close();
    if (responseCode != SC_OK) {
      LOG.error(
          "Failed to publish metrics to external metrics service. Response code {}", responseCode);
    }
  }
}
