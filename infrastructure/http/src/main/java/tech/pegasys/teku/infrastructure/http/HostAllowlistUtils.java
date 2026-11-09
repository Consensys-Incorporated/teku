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

import static com.google.common.collect.Streams.stream;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;

public class HostAllowlistUtils {

  public static boolean isHostAuthorized(final List<String> allowlist, final String hostHeader) {
    Optional<String> optionalHost = getAndValidateHostHeader(hostHeader);
    return allowlist.contains("*")
        || (optionalHost.isPresent() && hostIsInAllowlist(allowlist, optionalHost.get()));
  }

  static Optional<String> getAndValidateHostHeader(final String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) {
      return Optional.empty();
    }

    final Iterable<String> splitHostHeader = Splitter.on(':').split(hostHeader);
    final long hostPieces = stream(splitHostHeader).count();
    if (hostPieces > 1) {
      // If the host contains a colon, verify the host is correctly formed - host [ ":" port ]
      if (hostPieces > 2 || !Iterables.get(splitHostHeader, 1).matches("\\d{1,5}+")) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(Iterables.get(splitHostHeader, 0));
  }

  static boolean hostIsInAllowlist(final List<String> allowlist, final String hostHeader) {
    return allowlist.stream()
        .anyMatch(allowlistEntry -> allowlistEntry.equalsIgnoreCase(hostHeader));
  }
}
