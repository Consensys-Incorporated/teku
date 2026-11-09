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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainerSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof.AggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.SignedAggregateAndProof.SignedAggregateAndProofSchema;
import tech.pegasys.teku.spec.datastructures.state.HistoricalBatch.HistoricalBatchSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public interface SchemaDefinitions {

  BeaconStateSchema<?, ?> getBeaconStateSchema();

  SignedBeaconBlockSchema getSignedBeaconBlockSchema();

  BeaconBlockSchema getBeaconBlockSchema();

  BeaconBlockBodySchema<?> getBeaconBlockBodySchema();

  SszListSchema<Deposit, ?> getDepositsSchema();

  BeaconBlockSchema getBlindedBeaconBlockSchema();

  BeaconBlockBodySchema<?> getBlindedBeaconBlockBodySchema();

  SignedBeaconBlockSchema getSignedBlindedBeaconBlockSchema();

  BlockContainerSchema<BlockContainer> getBlockContainerSchema();

  BlockContainerSchema<BlockContainer> getBlindedBlockContainerSchema();

  SignedBlockContainerSchema<SignedBlockContainer> getSignedBlockContainerSchema();

  SignedBlockContainerSchema<SignedBlockContainer> getSignedBlindedBlockContainerSchema();

  MetadataMessageSchema<?> getMetadataMessageSchema();

  StatusMessageSchema<?> getStatusMessageSchema();

  SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema();

  SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema();

  HistoricalBatchSchema getHistoricalBatchSchema();

  SignedAggregateAndProofSchema getSignedAggregateAndProofSchema();

  AggregateAndProofSchema getAggregateAndProofSchema();

  AttestationSchema<Attestation> getAttestationSchema();

  IndexedAttestationSchema getIndexedAttestationSchema();

  AttesterSlashingSchema getAttesterSlashingSchema();

  BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema
      getBeaconBlocksByRootRequestMessageSchema();

  @NonSchema
  BeaconBlockBodyBuilder createBeaconBlockBodyBuilder();

  @NonSchema
  SchemaRegistry getSchemaRegistry();

  @NonSchema
  default Optional<SchemaDefinitionsAltair> toVersionAltair() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsBellatrix> toVersionBellatrix() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsCapella> toVersionCapella() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsDeneb> toVersionDeneb() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsElectra> toVersionElectra() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsFulu> toVersionFulu() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsGloas> toVersionGloas() {
    return Optional.empty();
  }

  @NonSchema
  default Optional<SchemaDefinitionsHeze> toVersionHeze() {
    return Optional.empty();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface NonSchema {}
}
