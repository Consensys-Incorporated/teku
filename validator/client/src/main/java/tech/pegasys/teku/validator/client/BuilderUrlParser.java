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

package tech.pegasys.teku.validator.client;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Strings.isNullOrEmpty;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.MAX_BUILDER_URL_SIZE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class BuilderUrlParser {

  public static URL parseBuilderUrl(final String builderUrl) {
    checkArgument(
        builderUrl != null
            && !builderUrl.isEmpty()
            && builderUrl.getBytes(StandardCharsets.UTF_8).length <= MAX_BUILDER_URL_SIZE,
        "builder url must be between 1 and %s UTF-8 bytes",
        MAX_BUILDER_URL_SIZE);
    try {
      final URL parsedBuilderUrl = URI.create(builderUrl).toURL();
      if (isNullOrEmpty(parsedBuilderUrl.getHost())) {
        throw new InvalidConfigurationException(
            "Invalid configuration. Builder URL (" + builderUrl + ") must contain a valid host");
      }
      // sanity check that we can resolve the default auth data without issues
      try {
        AuthDataResolver.resolveDefaultAuthData(parsedBuilderUrl);
      } catch (Exception e) {
        throw new InvalidConfigurationException(
            "Invalid configuration. Cannot resolve the default auth data for " + builderUrl, e);
      }
      return parsedBuilderUrl;
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw new InvalidConfigurationException(
          "Invalid configuration. Builder URL (" + builderUrl + ") has invalid syntax", e);
    }
  }
}
