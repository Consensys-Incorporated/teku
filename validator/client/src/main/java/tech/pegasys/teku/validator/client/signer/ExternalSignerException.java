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

import java.net.URI;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.validator.api.signer.SignType;

public class ExternalSignerException extends RuntimeException {

  public ExternalSignerException(final String message) {
    super(message);
  }

  public ExternalSignerException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public ExternalSignerException(final URI url, final SignType type, final String message) {
    super(formattedMessage(url, type, message));
  }

  public ExternalSignerException(
      final URI url, final SignType type, final String message, final Throwable cause) {
    super(formattedMessage(url, type, message), cause);
  }

  private static String formattedMessage(final URI url, final SignType type, final String message) {
    return "Request to external signer at ("
        + UrlSanitizer.sanitizePotentialUrl(url.toString())
        + ") failed signing type "
        + type
        + " - "
        + message;
  }
}
