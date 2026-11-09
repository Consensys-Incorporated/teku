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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class PortAvailability {
  private static final Logger LOG = LogManager.getLogger();

  public static boolean isPortAvailableForTcp(final int port) {
    if (!isPortValid(port)) {
      return false;
    }
    try (final ServerSocket serverSocket = new ServerSocket()) {
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress(port));
      return true;
    } catch (IOException ex) {
      LOG.trace(String.format("failed to open port %d for TCP", port), ex);
    }
    return false;
  }

  public static boolean isPortAvailableForUdp(final int port) {
    if (!isPortValid(port)) {
      return false;
    }
    try (final DatagramSocket datagramSocket = new DatagramSocket(null)) {
      datagramSocket.setReuseAddress(true);
      datagramSocket.bind(new InetSocketAddress(port));
      return true;
    } catch (IOException ex) {
      LOG.trace(String.format("failed to open port %d for UDP", port), ex);
    }
    return false;
  }

  public static boolean isPortValid(final int port) {
    return (port >= 0 && port <= 65535);
  }

  public static void checkPortsAvailable(final int tcpPort, final Optional<Integer> maybeUdpPort) {
    if (!isPortAvailableForTcp(tcpPort)) {
      throw new InvalidConfigurationException(
          String.format(
              "P2P Port %d (TCP) is already in use. Check for other processes using this port.",
              tcpPort));
    }
    maybeUdpPort.ifPresent(
        udpPort -> {
          if (!isPortAvailableForUdp(udpPort)) {
            throw new InvalidConfigurationException(
                String.format(
                    "P2P Port %d (UDP) is already in use. Check for other processes using this port.",
                    udpPort));
          }
        });
  }
}
