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

package tech.pegasys.teku.networking.p2p.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * CAUTION: this API is unstable and might be changed in any version in backward incompatible way
 */
public interface DiscoveryService {

  SafeFuture<?> start();

  SafeFuture<?> stop();

  Stream<DiscoveryPeer> streamKnownPeers();

  SafeFuture<Collection<DiscoveryPeer>> searchForPeers();

  Optional<String> getEnr();

  Optional<Bytes> getNodeId();

  Optional<List<String>> getDiscoveryAddresses();

  void updateCustomENRField(String fieldName, Bytes value);

  Optional<String> lookupEnr(UInt256 nodeId);
}
