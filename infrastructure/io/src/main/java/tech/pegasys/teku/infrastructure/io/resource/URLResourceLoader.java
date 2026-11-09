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

package tech.pegasys.teku.infrastructure.io.resource;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class URLResourceLoader extends ResourceLoader {

  private static final Logger LOG = LogManager.getLogger();
  public static final int DEFAULT_TIMEOUT_MS = (int) TimeUnit.MINUTES.toMillis(2);
  private final Optional<String> acceptHeader;
  private final int timeoutMillis;

  protected URLResourceLoader(
      final Optional<String> acceptHeader, final Predicate<String> sourceFilter) {
    this(acceptHeader, sourceFilter, DEFAULT_TIMEOUT_MS);
  }

  protected URLResourceLoader(
      final Optional<String> acceptHeader,
      final Predicate<String> sourceFilter,
      final int timeoutMillis) {
    super(sourceFilter);
    this.acceptHeader = acceptHeader;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  Optional<InputStream> loadSource(final String source) throws IOException {
    if (!source.contains(":")) {
      // Doesn't look like a URL
      return Optional.empty();
    }

    try {
      final URL url = new URI(source).toURL();
      final URLConnection connection = url.openConnection();
      connection.setConnectTimeout(timeoutMillis);
      connection.setReadTimeout(timeoutMillis);
      acceptHeader.ifPresent(type -> connection.setRequestProperty("Accept", type));
      connection.setRequestProperty("Accept-Encoding", "gzip");
      if (url.getUserInfo() != null) {
        final String credentials =
            Base64.getEncoder().encodeToString(url.getUserInfo().getBytes(UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + credentials);
      }
      connection.connect();
      final String encoding = connection.getHeaderField("Content-Encoding");
      InputStream stream = connection.getInputStream();
      if ("gzip".equalsIgnoreCase(encoding)) {
        stream = new GZIPInputStream(stream);
      }
      return Optional.of(stream);
    } catch (final SocketTimeoutException | ConnectException e) {
      throw e;
    } catch (final Exception e) {
      LOG.debug("Failed to load resource as URL", e);
      return Optional.empty();
    }
  }
}
