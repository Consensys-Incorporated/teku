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

package tech.pegasys.teku.networking.nat;

import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

public class TekuNatServiceConfiguration extends DefaultUpnpServiceConfiguration {

  @Override
  @SuppressWarnings("rawtypes")
  public StreamClient createStreamClient() {
    return new OkHttpStreamClient(
        new StreamClientConfigurationImpl(getSyncProtocolExecutorService()));
  }

  @Override
  @SuppressWarnings("rawtypes")
  public StreamServer createStreamServer(final NetworkAddressFactory networkAddressFactory) {
    return null;
  }
}
