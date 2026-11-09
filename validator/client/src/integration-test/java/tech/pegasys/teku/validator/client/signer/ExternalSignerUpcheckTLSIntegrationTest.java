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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.net.MalformedURLException;
import java.net.URI;
import org.junit.jupiter.api.Test;

public class ExternalSignerUpcheckTLSIntegrationTest
    extends AbstractSecureExternalSignerIntegrationTest {

  @Test
  void upcheckReturnsTrueWhenStatusCodeIs200() {
    client
        .when(request().withSecure(true).withMethod("GET").withPath("/upcheck"))
        .respond(response().withStatusCode(200));

    assertThat(externalSignerUpcheck.upcheck()).isTrue();
  }

  @Test
  void upcheckReturnsFalseWhenStatusCodeIsNot200() {
    client
        .when(request().withSecure(true).withMethod("GET").withPath("/upcheck"))
        .respond(response().withStatusCode(404));

    assertThat(externalSignerUpcheck.upcheck()).isFalse();
  }

  @Test
  void upcheckReturnsFalseWhenServerIsDown() throws MalformedURLException {
    final ExternalSignerUpcheck externalSignerUpcheck =
        new ExternalSignerUpcheck(
            externalSignerHttpClientFactory.get(),
            // an unused port
            URI.create("https://127.0.0.1:79").toURL(),
            validatorConfig.getValidatorExternalSignerTimeout());
    assertThat(externalSignerUpcheck.upcheck()).isFalse();
  }
}
