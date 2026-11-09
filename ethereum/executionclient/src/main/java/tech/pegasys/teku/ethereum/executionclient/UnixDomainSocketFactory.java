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

package tech.pegasys.teku.ethereum.executionclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import javax.net.SocketFactory;

class UnixDomainSocketFactory extends SocketFactory {

  private final Path path;

  UnixDomainSocketFactory(final Path path) {
    this.path = path;
  }

  @Override
  public Socket createSocket() {
    return new UnixDomainSocket(path);
  }

  @Override
  public Socket createSocket(final String host, final int port) throws IOException {
    final Socket socket = createSocket();
    socket.connect(UnixDomainSocketAddress.of(path));
    return socket;
  }

  @Override
  public Socket createSocket(
      final String host, final int port, final InetAddress localHost, final int localPort)
      throws IOException {
    return createSocket(host, port);
  }

  @Override
  public Socket createSocket(final InetAddress host, final int port) throws IOException {
    final Socket socket = createSocket();
    socket.connect(UnixDomainSocketAddress.of(path));
    return socket;
  }

  @Override
  public Socket createSocket(
      final InetAddress address,
      final int port,
      final InetAddress localAddress,
      final int localPort)
      throws IOException {
    return createSocket(address, port);
  }

  private static class UnixDomainSocket extends Socket {

    private final Path path;
    private SocketChannel channel;
    private boolean closed;

    UnixDomainSocket(final Path path) {
      this.path = path;
    }

    @Override
    public void connect(final SocketAddress endpoint, final int timeout) throws IOException {
      final SocketChannel newChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
      try {
        newChannel.connect(UnixDomainSocketAddress.of(path));
      } catch (final IOException e) {
        newChannel.close();
        throw e;
      }
      channel = newChannel;
    }

    @Override
    public void connect(final SocketAddress endpoint) throws IOException {
      connect(endpoint, 0);
    }

    @Override
    public InputStream getInputStream() {
      return Channels.newInputStream(channel);
    }

    @Override
    public OutputStream getOutputStream() {
      return Channels.newOutputStream(channel);
    }

    @Override
    public boolean isConnected() {
      return channel != null && channel.isConnected();
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    @Override
    public synchronized void close() throws IOException {
      closed = true;
      if (channel != null) {
        channel.close();
      }
    }
  }
}
