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

package tech.pegasys.teku.test.acceptance.stubServer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

public class SuccessHandler implements HttpHandler {

  private String responseBody;

  public String getResponse() {
    return this.responseBody;
  }

  @Override
  public void handle(final HttpExchange exchange) throws IOException {
    InputStream is = exchange.getRequestBody();
    this.responseBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    String response = "ok";
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length());
    OutputStream os = exchange.getResponseBody();
    os.write(response.getBytes(StandardCharsets.UTF_8));
    os.close();
  }
}
