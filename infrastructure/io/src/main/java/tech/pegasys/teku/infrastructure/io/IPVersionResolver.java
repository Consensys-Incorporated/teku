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

package tech.pegasys.teku.infrastructure.io;

import java.io.UncheckedIOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class IPVersionResolver {

  public enum IPVersion {
    IP_V4("IPv4"),
    IP_V6("IPv6");

    private final String name;

    IPVersion(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  public static IPVersion resolve(final String address) {
    try {
      final InetAddress inetAddress = InetAddress.getByName(address);
      return resolve(inetAddress);
    } catch (final UnknownHostException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  public static IPVersion resolve(final InetAddress inetAddress) {
    return inetAddress instanceof Inet6Address ? IPVersion.IP_V6 : IPVersion.IP_V4;
  }

  public static IPVersion resolve(final InetSocketAddress inetSocketAddress) {
    return resolve(inetSocketAddress.getAddress());
  }
}
