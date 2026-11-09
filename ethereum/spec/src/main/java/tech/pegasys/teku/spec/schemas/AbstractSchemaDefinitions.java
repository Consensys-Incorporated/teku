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

package tech.pegasys.teku.spec.schemas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTNETS_ENR_FIELD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.HISTORICAL_BATCH_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SYNCNETS_ENR_FIELD_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public abstract class AbstractSchemaDefinitions implements SchemaDefinitions {
  protected SchemaRegistry schemaRegistry;

  final SszBitvectorSchema<SszBitvector> attnetsENRFieldSchema;
  private final SszBitvectorSchema<SszBitvector> syncnetsENRFieldSchema;
  private final HistoricalBatchSchema historicalBatchSchema;
  private final BeaconBlocksByRootRequestMessageSchema beaconBlocksByRootRequestMessageSchema;

  public AbstractSchemaDefinitions(final SchemaRegistry schemaRegistry) {
    this.schemaRegistry = schemaRegistry;
    this.historicalBatchSchema = schemaRegistry.get(HISTORICAL_BATCH_SCHEMA);
    this.beaconBlocksByRootRequestMessageSchema =
        schemaRegistry.get(BEACON_BLOCKS_BY_ROOT_REQUEST_MESSAGE_SCHEMA);
    this.syncnetsENRFieldSchema = schemaRegistry.get(SYNCNETS_ENR_FIELD_SCHEMA);
    this.attnetsENRFieldSchema = schemaRegistry.get(ATTNETS_ENR_FIELD_SCHEMA);
  }

  @Override
  public SszListSchema<Deposit, ?> getDepositsSchema() {
    return getBeaconBlockSchema().getBodySchema().getDepositsSchema();
  }

  @Override
  public SchemaRegistry getSchemaRegistry() {
    return schemaRegistry;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema() {
    return attnetsENRFieldSchema;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema() {
    return syncnetsENRFieldSchema;
  }

  @Override
  public HistoricalBatchSchema getHistoricalBatchSchema() {
    return historicalBatchSchema;
  }

  @Override
  public BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      getBeaconBlocksByRootRequestMessageSchema() {
    return beaconBlocksByRootRequestMessageSchema;
  }
}
