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

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ContentTypes {

  public static final String JSON = "application/json";
  public static final String OCTET_STREAM = "application/octet-stream";
  public static final String EVENT_STREAM = "text/event-stream";

  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();
  private static final Splitter SEMICOLON_SPLITTER = Splitter.on(';').trimResults();

  public static String getRequestContentType(
      final Optional<String> specifiedContentType,
      final Collection<String> supportedTypes,
      final String defaultContentType) {
    if (specifiedContentType.isEmpty()) {
      return defaultContentType;
    }
    // Parameters such as charset don't affect which type is selected
    final String mediaType = withoutParameters(specifiedContentType.get());
    return supportedTypes.stream()
        .filter(supportedType -> supportedType.equalsIgnoreCase(mediaType))
        .findFirst()
        .orElseThrow(
            () ->
                new ContentTypeNotSupportedException(
                    "Request content type "
                        + specifiedContentType.get()
                        + " is not supported. Must be one of: "
                        + supportedTypes));
  }

  /**
   * Selects the supported type the accept header gives the highest quality, as in RFC 9110. Each
   * type takes the quality of the most specific range that matches it ({@code type/subtype}, then
   * {@code type/*}, then {@code *}{@code /*}). Ties go to the earliest type in {@code types}, so
   * list them in order of preference. Types with quality 0 are never selected.
   */
  public static Optional<String> getResponseContentType(
      final List<String> types, final Optional<String> acceptHeader) {
    if (acceptHeader.isEmpty()) {
      return Optional.empty();
    }
    final List<MediaRange> ranges = parseAcceptHeader(acceptHeader.get());
    Optional<String> bestType = Optional.empty();
    float bestQuality = 0;
    for (final String type : types) {
      final float quality = quality(type.toLowerCase(Locale.ROOT), ranges);
      if (quality > bestQuality) {
        bestType = Optional.of(type);
        bestQuality = quality;
      }
    }
    return bestType;
  }

  private static float quality(final String type, final List<MediaRange> ranges) {
    int bestSpecificity = -1;
    float quality = 0;
    for (final MediaRange range : ranges) {
      final int specificity = range.specificityFor(type);
      if (specificity > bestSpecificity) {
        bestSpecificity = specificity;
        quality = range.quality();
      }
    }
    return quality;
  }

  private static List<MediaRange> parseAcceptHeader(final String acceptHeader) {
    final List<MediaRange> ranges = new ArrayList<>();
    for (final String range : COMMA_SPLITTER.split(acceptHeader.toLowerCase(Locale.ROOT))) {
      final List<String> parts = SEMICOLON_SPLITTER.splitToList(range);
      String mediaType = parts.get(0);
      if (mediaType.equals("*")) {
        // Java's URLConnection sends a bare "*"
        mediaType = "*/*";
      }
      if (!mediaType.contains("/")) {
        continue;
      }
      float quality = 1;
      for (final String parameter : parts.subList(1, parts.size())) {
        if (parameter.startsWith("q=")) {
          quality = parseQuality(parameter.substring(2));
        }
      }
      ranges.add(new MediaRange(mediaType, quality));
    }
    return ranges;
  }

  /** An invalid quality counts as 1, so a malformed header doesn't exclude a type. */
  private static float parseQuality(final String value) {
    try {
      final float quality = Float.parseFloat(value);
      return quality >= 0 && quality <= 1 ? quality : 1;
    } catch (final NumberFormatException e) {
      return 1;
    }
  }

  private static String withoutParameters(final String mediaType) {
    final int parametersStart = mediaType.indexOf(';');
    return (parametersStart < 0 ? mediaType : mediaType.substring(0, parametersStart)).trim();
  }

  private record MediaRange(String mediaType, float quality) {

    /** Returns 2 for an exact match, 1 for type/*, 0 for any type, or -1 when it doesn't match. */
    int specificityFor(final String type) {
      if (mediaType.equals(type)) {
        return 2;
      }
      if (mediaType.equals("*/*")) {
        return 0;
      }
      if (mediaType.endsWith("/*")
          && type.startsWith(mediaType.substring(0, mediaType.length() - 1))) {
        return 1;
      }
      return -1;
    }
  }
}
