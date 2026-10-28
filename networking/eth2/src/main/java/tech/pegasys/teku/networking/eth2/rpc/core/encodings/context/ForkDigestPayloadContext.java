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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.context;

import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public interface ForkDigestPayloadContext<TPayload extends SszData> {

  ForkDigestPayloadContext<SignedBeaconBlock> SIGNED_BEACON_BLOCK =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedBeaconBlock responsePayload) {
          return responsePayload.getMessage().getSlot();
        }

        @Override
        public SszSchema<SignedBeaconBlock> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.getSignedBeaconBlockSchema();
        }
      };

  ForkDigestPayloadContext<BlobSidecar> BLOB_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final BlobSidecar responsePayload) {
          return responsePayload.getSlot();
        }

        @Override
        public SszSchema<BlobSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsDeneb.required(schemaDefinitions).getBlobSidecarSchema();
        }
      };

  ForkDigestPayloadContext<DataColumnSidecar> DATA_COLUMN_SIDECAR =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final DataColumnSidecar responsePayload) {
          return responsePayload.getSlot();
        }

        @Override
        public boolean rejectsContextSlotMismatch() {
          return true;
        }

        @Override
        public SszSchema<DataColumnSidecar> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsFulu.required(schemaDefinitions).getDataColumnSidecarSchema();
        }
      };

  ForkDigestPayloadContext<SignedExecutionPayloadEnvelope> SIGNED_EXECUTION_PAYLOAD_ENVELOPE =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedExecutionPayloadEnvelope responsePayload) {
          return responsePayload.getMessage().getSlot();
        }

        @Override
        public SszSchema<SignedExecutionPayloadEnvelope> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsGloas.required(schemaDefinitions)
              .getSignedExecutionPayloadEnvelopeSchema();
        }
      };

  ForkDigestPayloadContext<LightClientBootstrap> LIGHT_CLIENT_BOOTSTRAP =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final LightClientBootstrap bootstrap) {
          return bootstrap.getLightClientHeader().getBeacon().getSlot();
        }

        @Override
        public SszSchema<LightClientBootstrap> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsAltair.required(schemaDefinitions)
              .getLightClientBootstrapSchema();
        }
      };

  ForkDigestPayloadContext<LightClientUpdate> LIGHT_CLIENT_UPDATE =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final LightClientUpdate update) {
          return update.getAttestedHeader().getBeacon().getSlot();
        }

        @Override
        public SszSchema<LightClientUpdate> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsAltair.required(schemaDefinitions).getLightClientUpdateSchema();
        }
      };

  ForkDigestPayloadContext<LightClientFinalityUpdate> LIGHT_CLIENT_FINALITY_UPDATE =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final LightClientFinalityUpdate finalityUpdate) {
          return finalityUpdate.getAttestedHeader().getBeacon().getSlot();
        }

        @Override
        public SszSchema<LightClientFinalityUpdate> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsAltair.required(schemaDefinitions)
              .getLightClientFinalityUpdateSchema();
        }
      };

  ForkDigestPayloadContext<LightClientOptimisticUpdate> LIGHT_CLIENT_OPTIMISTIC_UPDATE =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final LightClientOptimisticUpdate optimisticUpdate) {
          return optimisticUpdate.getAttestedHeader().getBeacon().getSlot();
        }

        @Override
        public SszSchema<LightClientOptimisticUpdate> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return SchemaDefinitionsAltair.required(schemaDefinitions)
              .getLightClientOptimisticUpdateSchema();
        }
      };

  ForkDigestPayloadContext<SignedInclusionList> SIGNED_INCLUSION_LIST =
      new ForkDigestPayloadContext<>() {
        @Override
        public UInt64 getSlotFromPayload(final SignedInclusionList responsePayload) {
          return responsePayload.getMessage().getSlot();
        }

        @Override
        public SszSchema<SignedInclusionList> getSchemaFromSchemaDefinitions(
            final SchemaDefinitions schemaDefinitions) {
          return schemaDefinitions.toVersionHeze().orElseThrow().getSignedInclusionListSchema();
        }
      };

  UInt64 getSlotFromPayload(final TPayload responsePayload);

  SszSchema<TPayload> getSchemaFromSchemaDefinitions(final SchemaDefinitions schemaDefinitions);

  /**
   * Whether a chunk whose fork digest does not match the fork at the payload's slot is rejected.
   */
  default boolean rejectsContextSlotMismatch() {
    return false;
  }
}
