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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.commons.lang3.StringUtils;

public class UrlSanitizer {

  public static String sanitizePotentialUrl(final String possibleUrl) {
    try {
      final URI uri = new URI(possibleUrl);
      return sanitizeUri(uri).toASCIIString();
    } catch (final URISyntaxException e) {
      // Not actually a URI so no need to sanitize
      return possibleUrl;
    }
  }

  public static URL sanitizeUrl(final URL url) {
    try {
      final URI sanitizedUri = sanitizeUri(url.toURI());
      return sanitizedUri.toURL();
    } catch (final URISyntaxException | MalformedURLException e) {
      // can't sanitize, so will just return the method argument
      return url;
    }
  }

  public static String appendPath(final String url, final String path) {
    if (StringUtils.isEmpty(url)) {
      throw new IllegalArgumentException("Base URL cannot be null/empty");
    }

    final String sanitizedPath = path == null ? "" : path;
    final URL urlWithPath;
    try {
      urlWithPath = URI.create(url).resolve(sanitizedPath).toURL();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid URL " + url, e);
    }

    return urlWithPath.toString();
  }

  public static boolean urlContainsNonEmptyPath(final String url) {
    try {
      final URI uri = new URI(url);
      return uri.getPath() != null && uri.getPath().length() > 1;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static URI sanitizeUri(final URI uri) throws URISyntaxException {
    return new URI(
        uri.getScheme(),
        null,
        uri.getHost(),
        uri.getPort(),
        uri.getPath(),
        uri.getQuery(),
        uri.getFragment());
  }
}
