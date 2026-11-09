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

package tech.pegasys.teku.beaconrestapi;

import static org.mockito.Mockito.mock;

import java.util.function.IntSupplier;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.api.NetworkDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.ValidatorDataProvider;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.beacon.sync.events.SyncingStatus;
import tech.pegasys.teku.infrastructure.restapi.StubRestApiRequest;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionCache;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractMigratedBeaconHandlerTest {
  protected final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  protected Spec spec = TestSpecFactory.createMinimalPhase0();

  protected final NetworkDataProvider network = new NetworkDataProvider(eth2P2PNetwork);

  protected final SyncService syncService = mock(SyncService.class);
  protected int rejectedExecutionCount = 0;
  protected final IntSupplier rejectedExecutionSupplier = () -> rejectedExecutionCount;
  protected final SyncDataProvider syncDataProvider =
      new SyncDataProvider(syncService, rejectedExecutionSupplier);
  protected SchemaDefinitionCache schemaDefinitionCache = new SchemaDefinitionCache(spec);
  protected DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  protected ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  protected ValidatorDataProvider validatorDataProvider = mock(ValidatorDataProvider.class);
  protected final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  protected final ExecutionClientDataProvider executionClientDataProvider =
      mock(ExecutionClientDataProvider.class);

  // Getting a NullPointerException from this? Call setHandler as the first thing you do. :)
  protected StubRestApiRequest request;
  protected RestApiEndpoint handler;

  protected void setHandler(final RestApiEndpoint handler) {
    this.handler = handler;
    this.request = new StubRestApiRequest(handler.getMetadata());
  }

  protected SyncingStatus getSyncStatus(
      final boolean isSyncing,
      final long startSlot,
      final long currentSlot,
      final long highestSlot) {
    return new SyncingStatus(
        isSyncing,
        UInt64.valueOf(currentSlot),
        UInt64.valueOf(startSlot),
        UInt64.valueOf(highestSlot));
  }

  protected void setSpec(final Spec spec) {
    this.spec = spec;
    this.dataStructureUtil = new DataStructureUtil(this.spec);
    schemaDefinitionCache = new SchemaDefinitionCache(this.spec);
  }

  protected <T> ObjectAndMetaData<T> withMetaData(final T value) {
    return new ObjectAndMetaData<>(value, spec.getGenesisSpec().getMilestone(), false, true, false);
  }
}
