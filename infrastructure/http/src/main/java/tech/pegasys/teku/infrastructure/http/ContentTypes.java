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

package tech.pegasys.teku.infrastructure.http;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.commonjava.mimeparse.MIMEParse;

public class ContentTypes {

  public static final String JSON = "application/json";
  public static final String OCTET_STREAM = "application/octet-stream";
  public static final String EVENT_STREAM = "text/event-stream";
  private static final Logger LOG = LogManager.getLogger();

  public static String getRequestContentType(
      final Optional<String> specifiedContentType,
      final Collection<String> supportedTypes,
      final String defaultContentType) {
    if (specifiedContentType.isEmpty()) {
      return defaultContentType;
    }
    return findBestMatch(supportedTypes, specifiedContentType.get())
        .orElseThrow(
            () ->
                new ContentTypeNotSupportedException(
                    "Request content type "
                        + specifiedContentType.get()
                        + " is not supported. Must be one of: "
                        + supportedTypes));
  }

  public static Optional<String> getResponseContentType(
      final List<String> types, final Optional<String> maybeContentType) {
    if (maybeContentType.isEmpty()) {
      return Optional.empty();
    }
    final String contentType = maybeContentType.get();
    // Reverse the list because MIMEParse treats the last item as the most preferred.
    return findBestMatch(Lists.reverse(types), contentType);
  }

  private static Optional<String> findBestMatch(
      final Collection<String> types, final String contentType) {
    try {
      final String bestMatch = MIMEParse.bestMatch(types, contentType);
      return bestMatch.isEmpty() ? Optional.empty() : Optional.of(bestMatch);
    } catch (Exception e) {
      LOG.trace("Failed to parse accept header", e);
    }
    return Optional.empty();
  }
}
