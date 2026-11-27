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

package tech.pegasys.teku.infrastructure.restapi.endpoints;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_ACCEPTABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_ACCEPT;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class JavalinEndpointAdapter implements Handler {

  private final RestApiEndpoint endpoint;

  public JavalinEndpointAdapter(final RestApiEndpoint endpoint) {
    this.endpoint = endpoint;
  }

  @Override
  public void handle(final Context ctx) throws Exception {
    final EndpointMetadata metadata = endpoint.getMetadata();
    final RestApiRequest request = new JavalinRestApiRequest(ctx, metadata);
    if (!metadata.isAcceptable(ctx.req().getHeaders(HEADER_ACCEPT))) {
      request.respondError(
          SC_NOT_ACCEPTABLE,
          "Accept header is not supported. Must match one of: "
              + metadata.getSupportedResponseContentTypes(SC_OK));
      return;
    }
    endpoint.handleRequest(request);
  }
}
