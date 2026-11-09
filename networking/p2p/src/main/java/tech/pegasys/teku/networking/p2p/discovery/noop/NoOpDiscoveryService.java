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

package tech.pegasys.teku.networking.p2p.discovery.noop;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer;
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService;

public class NoOpDiscoveryService implements DiscoveryService {

  @Override
  public SafeFuture<?> start() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<?> stop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    return Stream.empty();
  }

  @Override
  public SafeFuture<Collection<DiscoveryPeer>> searchForPeers() {
    return SafeFuture.completedFuture(Collections.emptyList());
  }

  @Override
  public Optional<String> getEnr() {
    return Optional.empty();
  }

  @Override
  public Optional<Bytes> getNodeId() {
    return Optional.of(Bytes.wrap(BigInteger.valueOf(1).toByteArray()));
  }

  @Override
  public Optional<List<String>> getDiscoveryAddresses() {
    return Optional.empty();
  }

  @Override
  public void updateCustomENRField(final String fieldName, final Bytes value) {}

  @Override
  public Optional<String> lookupEnr(final UInt256 nodeId) {
    return Optional.empty();
  }
}
