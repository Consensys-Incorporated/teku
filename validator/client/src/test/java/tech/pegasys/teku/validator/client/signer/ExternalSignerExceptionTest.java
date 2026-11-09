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

import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.validator.api.signer.SignType;

class ExternalSignerExceptionTest {

  private URI urlWithPassword;
  private static final String START_MESSAGE =
      "Request to external signer at (http://host.com/foo/bar)";

  @BeforeEach
  void setup() throws URISyntaxException {
    urlWithPassword = new URI("http://user:pass@host.com/foo/bar");
  }

  @Test
  void shouldSanitizeUrlWhenGivenThrowable() {
    final ExternalSignerException exception =
        new ExternalSignerException(
            urlWithPassword, SignType.ATTESTATION, "Message", new IllegalArgumentException("test"));
    assertThat(exception.getMessage()).startsWith(START_MESSAGE);
  }

  @Test
  void shouldSanitizeUrlWhenNotGivenThrowable() {
    final ExternalSignerException exception =
        new ExternalSignerException(urlWithPassword, SignType.ATTESTATION, "Message");
    assertThat(exception.getMessage()).startsWith(START_MESSAGE);
  }
}
