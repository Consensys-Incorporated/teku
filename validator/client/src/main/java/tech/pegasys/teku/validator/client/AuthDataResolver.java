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

import com.google.common.base.CharMatcher;
import com.google.common.net.InetAddresses;
import java.net.IDN;
import java.net.URL;
import java.util.Locale;

public class AuthDataResolver {

  // Spec: hostname lowercased, ASCII, IPv6 in compressed form (RFC 5952) inside brackets
  public static String resolveDefaultAuthData(final URL builderUrl) {
    final String host = builderUrl.getHost();
    // Handle IPv6 inside brackets
    if (host.startsWith("[") && host.endsWith("]")) {
      final String ipv6 = host.substring(1, host.length() - 1);
      // toAddrString from Guava follows RFC 5952
      return "[" + InetAddresses.toAddrString(InetAddresses.forString(ipv6)) + "]";
    }
    if (CharMatcher.ascii().matchesAllOf(host)) {
      return host.toLowerCase(Locale.ROOT);
    } else {
      return IDN.toASCII(host).toLowerCase(Locale.ROOT);
    }
  }
}
