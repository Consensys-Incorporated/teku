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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain;

import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_CONCURRENT_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS_PER_PEER;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_PENDING_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS_PER_PEER;
import static tech.pegasys.teku.spec.config.Constants.MAXIMUM_QUEUED_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BeaconBlocksByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.ExecutionPayloadEnvelopesByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.ExecutionPayloadEnvelopesByRootMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.ExecutionPayloadEnvelopesRequestLimiter;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.GoodbyeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.LightClientBootstrapMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.LightClientLatestUpdateMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.LightClientUpdatesByRangeMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.PingMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.ForkDigestPayloadContext;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.SingleProtocolEth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.VersionedEth2RpcMethod;
import tech.pegasys.teku.networking.p2p.rpc.RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage.BeaconBlocksByRangeRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage.EmptyMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage.ExecutionPayloadEnvelopesByRootRequestMessageSchema;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientBootstrapRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.LightClientUpdatesByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarArchiveReconstructor;
import tech.pegasys.teku.statetransition.datacolumns.log.rpc.DasReqRespLogger;
import tech.pegasys.teku.statetransition.lightclient.LightClientBootstrapProvider;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconChainMethods {

  private final Eth2RpcMethod<StatusMessage, StatusMessage> status;
  private final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye;
  private final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      beaconBlocksByRoot;
  private final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      beaconBlocksByRange;
  private final Optional<Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar>>
      blobSidecarsByRoot;
  private final Optional<Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar>>
      blobSidecarsByRange;
  private final Optional<Eth2RpcMethod<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>>
      dataColumnSidecarsByRoot;
  private final Optional<Eth2RpcMethod<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>>
      dataColumnSidecarsByRange;
  private final Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope>>
      executionPayloadEnvelopesByRoot;
  private final Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope>>
      executionPayloadEnvelopesByRange;
  private final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata;
  private final Eth2RpcMethod<PingMessage, PingMessage> ping;
  private final Optional<Eth2RpcMethod<EmptyMessage, LightClientFinalityUpdate>>
      lightClientFinalityUpdate;
  private final Optional<Eth2RpcMethod<EmptyMessage, LightClientOptimisticUpdate>>
      lightClientOptimisticUpdate;
  private final Optional<Eth2RpcMethod<LightClientUpdatesByRangeRequestMessage, LightClientUpdate>>
      lightClientUpdatesByRange;
  private final Optional<Eth2RpcMethod<LightClientBootstrapRequestMessage, LightClientBootstrap>>
      lightClientBootstrap;

  private final Collection<RpcMethod<?, ?, ?>> allMethods;

  private BeaconChainMethods(
      final Eth2RpcMethod<StatusMessage, StatusMessage> status,
      final Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye,
      final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot,
      final Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange,
      final Optional<Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar>>
          blobSidecarsByRoot,
      final Optional<Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar>>
          blobSidecarsByRange,
      final Optional<Eth2RpcMethod<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>>
          dataColumnSidecarsByRoot,
      final Optional<Eth2RpcMethod<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>>
          dataColumnSidecarsByRange,
      final Optional<
              Eth2RpcMethod<
                  ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope>>
          executionPayloadEnvelopesByRoot,
      final Optional<
              Eth2RpcMethod<
                  ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope>>
          executionPayloadEnvelopesByRange,
      final Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata,
      final Eth2RpcMethod<PingMessage, PingMessage> ping,
      final Optional<Eth2RpcMethod<EmptyMessage, LightClientFinalityUpdate>>
          lightClientFinalityUpdate,
      final Optional<Eth2RpcMethod<EmptyMessage, LightClientOptimisticUpdate>>
          lightClientOptimisticUpdate,
      final Optional<Eth2RpcMethod<LightClientUpdatesByRangeRequestMessage, LightClientUpdate>>
          lightClientUpdatesByRange,
      final Optional<Eth2RpcMethod<LightClientBootstrapRequestMessage, LightClientBootstrap>>
          lightClientBootstrap) {
    this.status = status;
    this.goodBye = goodBye;
    this.beaconBlocksByRoot = beaconBlocksByRoot;
    this.beaconBlocksByRange = beaconBlocksByRange;
    this.blobSidecarsByRoot = blobSidecarsByRoot;
    this.blobSidecarsByRange = blobSidecarsByRange;
    this.dataColumnSidecarsByRoot = dataColumnSidecarsByRoot;
    this.dataColumnSidecarsByRange = dataColumnSidecarsByRange;
    this.executionPayloadEnvelopesByRoot = executionPayloadEnvelopesByRoot;
    this.executionPayloadEnvelopesByRange = executionPayloadEnvelopesByRange;
    this.getMetadata = getMetadata;
    this.ping = ping;
    this.lightClientFinalityUpdate = lightClientFinalityUpdate;
    this.lightClientOptimisticUpdate = lightClientOptimisticUpdate;
    this.lightClientUpdatesByRange = lightClientUpdatesByRange;
    this.lightClientBootstrap = lightClientBootstrap;
    this.allMethods =
        new ArrayList<>(
            List.of(status, goodBye, beaconBlocksByRoot, beaconBlocksByRange, getMetadata, ping));
    // Deneb
    blobSidecarsByRoot.ifPresent(allMethods::add);
    blobSidecarsByRange.ifPresent(allMethods::add);
    // Fulu
    dataColumnSidecarsByRoot.ifPresent(allMethods::add);
    dataColumnSidecarsByRange.ifPresent(allMethods::add);
    // Gloas
    executionPayloadEnvelopesByRoot.ifPresent(allMethods::add);
    executionPayloadEnvelopesByRange.ifPresent(allMethods::add);
    // Light client
    lightClientFinalityUpdate.ifPresent(allMethods::add);
    lightClientOptimisticUpdate.ifPresent(allMethods::add);
    lightClientUpdatesByRange.ifPresent(allMethods::add);
    lightClientBootstrap.ifPresent(allMethods::add);
  }

  public static BeaconChainMethods create(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final PeerLookup peerLookup,
      final CombinedChainDataClient combinedChainDataClient,
      final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final RpcEncoding rpcEncoding,
      final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
      final DasReqRespLogger dasLogger,
      final Optional<LightClientUpdateStore> lightClientUpdateStore) {
    // Shared by both envelope methods
    final ExecutionPayloadEnvelopesRequestLimiter executionPayloadEnvelopesRequestLimiter =
        new ExecutionPayloadEnvelopesRequestLimiter(
            MAXIMUM_CONCURRENT_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS,
            MAXIMUM_CONCURRENT_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS_PER_PEER,
            MAXIMUM_QUEUED_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS,
            MAXIMUM_PENDING_EXECUTION_PAYLOAD_ENVELOPE_REQUESTS_PER_PEER);
    return new BeaconChainMethods(
        createStatus(spec, asyncRunner, statusMessageFactory, peerLookup, rpcEncoding),
        createGoodBye(asyncRunner, metricsSystem, peerLookup, rpcEncoding),
        createBeaconBlocksByRoot(
            spec, metricsSystem, asyncRunner, recentChainData, peerLookup, rpcEncoding),
        createBeaconBlocksByRange(
            spec,
            metricsSystem,
            asyncRunner,
            recentChainData,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding),
        createBlobSidecarsByRoot(
            spec,
            metricsSystem,
            asyncRunner,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding,
            recentChainData),
        createBlobSidecarsByRange(
            spec,
            metricsSystem,
            asyncRunner,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding,
            recentChainData),
        createDataColumnSidecarsByRoot(
            spec,
            metricsSystem,
            asyncRunner,
            combinedChainDataClient,
            custodyGroupCountManagerSupplier,
            peerLookup,
            rpcEncoding,
            recentChainData,
            dataColumnSidecarArchiveReconstructor,
            dasLogger),
        createDataColumnsSidecarsByRange(
            spec,
            metricsSystem,
            asyncRunner,
            combinedChainDataClient,
            peerLookup,
            rpcEncoding,
            recentChainData,
            dataColumnSidecarArchiveReconstructor,
            dasLogger),
        createExecutionPayloadEnvelopesByRoot(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            combinedChainDataClient,
            metricsSystem,
            executionPayloadEnvelopesRequestLimiter),
        createExecutionPayloadEnvelopesByRange(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            combinedChainDataClient,
            metricsSystem,
            executionPayloadEnvelopesRequestLimiter),
        createMetadata(spec, asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding),
        createPing(asyncRunner, metadataMessagesFactory, peerLookup, rpcEncoding),
        createLightClientMethod(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            recentChainData,
            lightClientUpdateStore,
            BeaconChainMethodIds.LIGHT_CLIENT_FINALITY_UPDATE,
            EmptyMessage.SSZ_SCHEMA,
            ForkDigestPayloadContext.LIGHT_CLIENT_FINALITY_UPDATE,
            store ->
                new LightClientLatestUpdateMessageHandler<>(
                    "light client finality update", store::getLatestFinalityUpdate)),
        createLightClientMethod(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            recentChainData,
            lightClientUpdateStore,
            BeaconChainMethodIds.LIGHT_CLIENT_OPTIMISTIC_UPDATE,
            EmptyMessage.SSZ_SCHEMA,
            ForkDigestPayloadContext.LIGHT_CLIENT_OPTIMISTIC_UPDATE,
            store ->
                new LightClientLatestUpdateMessageHandler<>(
                    "light client optimistic update", store::getLatestOptimisticUpdate)),
        createLightClientMethod(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            recentChainData,
            lightClientUpdateStore,
            BeaconChainMethodIds.LIGHT_CLIENT_UPDATES_BY_RANGE,
            LightClientUpdatesByRangeRequestMessage.SSZ_SCHEMA,
            ForkDigestPayloadContext.LIGHT_CLIENT_UPDATE,
            LightClientUpdatesByRangeMessageHandler::new),
        createLightClientMethod(
            spec,
            asyncRunner,
            peerLookup,
            rpcEncoding,
            recentChainData,
            lightClientUpdateStore,
            BeaconChainMethodIds.LIGHT_CLIENT_BOOTSTRAP,
            LightClientBootstrapRequestMessage.SSZ_SCHEMA,
            ForkDigestPayloadContext.LIGHT_CLIENT_BOOTSTRAP,
            __ ->
                new LightClientBootstrapMessageHandler(
                    new LightClientBootstrapProvider(spec, combinedChainDataClient))));
  }

  private static Eth2RpcMethod<StatusMessage, StatusMessage> createStatus(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final StatusMessageFactory statusMessageFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final StatusMessageHandler messageHandler =
        new StatusMessageHandler(spec, statusMessageFactory);
    final SszSchema<StatusMessage> phase0StatusSchema =
        SszSchema.as(
            StatusMessage.class,
            spec.forMilestone(SpecMilestone.PHASE0)
                .getSchemaDefinitions()
                .getStatusMessageSchema());
    final boolean expectResponse = true;
    final RpcContextCodec<?, StatusMessage> phase0ContextCodec =
        RpcContextCodec.noop(phase0StatusSchema);

    final SingleProtocolEth2RpcMethod<StatusMessage, StatusMessage> v1Method =
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.STATUS,
            1,
            rpcEncoding,
            phase0StatusSchema,
            expectResponse,
            phase0ContextCodec,
            messageHandler,
            peerLookup);

    final List<SingleProtocolEth2RpcMethod<StatusMessage, StatusMessage>> versionedMethods =
        new ArrayList<>();
    versionedMethods.add(v1Method);

    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      final SszSchema<StatusMessage> fuluStatusSchema =
          SszSchema.as(
              StatusMessage.class,
              spec.forMilestone(SpecMilestone.FULU)
                  .getSchemaDefinitions()
                  .getStatusMessageSchema());
      final RpcContextCodec<?, StatusMessage> fuluContextCodec =
          RpcContextCodec.noop(fuluStatusSchema);
      final SingleProtocolEth2RpcMethod<StatusMessage, StatusMessage> v2Method =
          new SingleProtocolEth2RpcMethod<>(
              asyncRunner,
              BeaconChainMethodIds.STATUS,
              2,
              rpcEncoding,
              fuluStatusSchema,
              expectResponse,
              fuluContextCodec,
              messageHandler,
              peerLookup);
      versionedMethods.add(v2Method);

      return VersionedEth2RpcMethod.create(
          rpcEncoding, phase0StatusSchema, expectResponse, versionedMethods);
    } else {
      return v1Method;
    }
  }

  private static Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> createGoodBye(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final GoodbyeMessageHandler goodbyeHandler = new GoodbyeMessageHandler(metricsSystem);
    final RpcContextCodec<?, GoodbyeMessage> contextCodec =
        RpcContextCodec.noop(GoodbyeMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BeaconChainMethodIds.GOODBYE,
        1,
        rpcEncoding,
        GoodbyeMessage.SSZ_SCHEMA,
        false,
        contextCodec,
        goodbyeHandler,
        peerLookup);
  }

  private static Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRoot(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    final BeaconBlocksByRootMessageHandler beaconBlocksByRootHandler =
        new BeaconBlocksByRootMessageHandler(spec, metricsSystem, recentChainData);

    final BeaconBlocksByRootRequestMessageSchema requestType =
        spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema();
    final boolean expectResponseToRequest = true;

    final RpcContextCodec<Bytes4, SignedBeaconBlock> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK);

    final SingleProtocolEth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock>
        v2Method =
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                BeaconChainMethodIds.BEACON_BLOCKS_BY_ROOT,
                2,
                rpcEncoding,
                requestType,
                expectResponseToRequest,
                forkDigestContextCodec,
                beaconBlocksByRootHandler,
                peerLookup);

    return VersionedEth2RpcMethod.create(
        rpcEncoding, requestType, expectResponseToRequest, List.of(v2Method));
  }

  private static Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
      createBeaconBlocksByRange(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final RecentChainData recentChainData,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding) {
    final BeaconBlocksByRangeMessageHandler beaconBlocksByRangeHandler =
        new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    final BeaconBlocksByRangeRequestMessageSchema requestType =
        BeaconBlocksByRangeRequestMessage.SSZ_SCHEMA;
    final boolean expectResponseToRequest = true;

    final RpcContextCodec<Bytes4, SignedBeaconBlock> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec, recentChainData, ForkDigestPayloadContext.SIGNED_BEACON_BLOCK);

    final SingleProtocolEth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock>
        v2Method =
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                BeaconChainMethodIds.BEACON_BLOCKS_BY_RANGE,
                2,
                rpcEncoding,
                requestType,
                expectResponseToRequest,
                forkDigestContextCodec,
                beaconBlocksByRangeHandler,
                peerLookup);

    return VersionedEth2RpcMethod.create(
        rpcEncoding, requestType, expectResponseToRequest, List.of(v2Method));
  }

  private static Optional<Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar>>
      createBlobSidecarsByRoot(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData) {
    if (!spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      return Optional.empty();
    }

    final RpcContextCodec<Bytes4, BlobSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(spec, recentChainData, ForkDigestPayloadContext.BLOB_SIDECAR);

    final BlobSidecarsByRootMessageHandler blobSidecarsByRootHandler =
        new BlobSidecarsByRootMessageHandler(spec, metricsSystem, combinedChainDataClient);
    final BlobSidecarsByRootRequestMessageSchema blobSidecarsByRootRequestMessageSchema =
        SchemaDefinitionsDeneb.required(
                spec.forMilestone(SpecMilestone.DENEB).getSchemaDefinitions())
            .getBlobSidecarsByRootRequestMessageSchema();

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.BLOB_SIDECARS_BY_ROOT,
            1,
            rpcEncoding,
            blobSidecarsByRootRequestMessageSchema,
            true,
            forkDigestContextCodec,
            blobSidecarsByRootHandler,
            peerLookup));
  }

  private static Optional<Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar>>
      createBlobSidecarsByRange(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData) {

    if (!spec.isMilestoneSupported(SpecMilestone.DENEB)) {
      return Optional.empty();
    }

    final BlobSidecarsByRangeRequestMessage.BlobSidecarsByRangeRequestMessageSchema requestType =
        BlobSidecarsByRangeRequestMessage.SSZ_SCHEMA;

    final RpcContextCodec<Bytes4, BlobSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(spec, recentChainData, ForkDigestPayloadContext.BLOB_SIDECAR);

    final BlobSidecarsByRangeMessageHandler blobSidecarsByRangeHandler =
        new BlobSidecarsByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.BLOB_SIDECARS_BY_RANGE,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            blobSidecarsByRangeHandler,
            peerLookup));
  }

  private static Optional<Eth2RpcMethod<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>>
      createDataColumnSidecarsByRoot(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final Supplier<CustodyGroupCountManager> custodyGroupCountManagerSupplier,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData,
          final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
          final DasReqRespLogger dasLogger) {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return Optional.empty();
    }

    final RpcContextCodec<Bytes4, DataColumnSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec, recentChainData, ForkDigestPayloadContext.DATA_COLUMN_SIDECAR);

    final DataColumnSidecarsByRootMessageHandler dataColumnSidecarsByRootMessageHandler =
        new DataColumnSidecarsByRootMessageHandler(
            spec,
            metricsSystem,
            combinedChainDataClient,
            custodyGroupCountManagerSupplier,
            dataColumnSidecarArchiveReconstructor,
            dasLogger);
    final DataColumnSidecarsByRootRequestMessageSchema
        dataColumnSidecarsByRootRequestMessageSchema =
            SchemaDefinitionsFulu.required(
                    spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
                .getDataColumnSidecarsByRootRequestMessageSchema();

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.DATA_COLUMN_SIDECARS_BY_ROOT,
            1,
            rpcEncoding,
            dataColumnSidecarsByRootRequestMessageSchema,
            true,
            forkDigestContextCodec,
            dataColumnSidecarsByRootMessageHandler,
            peerLookup));
  }

  private static Optional<Eth2RpcMethod<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>>
      createDataColumnsSidecarsByRange(
          final Spec spec,
          final MetricsSystem metricsSystem,
          final AsyncRunner asyncRunner,
          final CombinedChainDataClient combinedChainDataClient,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData,
          final DataColumnSidecarArchiveReconstructor dataColumnSidecarArchiveReconstructor,
          final DasReqRespLogger dasLogger) {

    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return Optional.empty();
    }

    final DataColumnSidecarsByRangeRequestMessage.DataColumnSidecarsByRangeRequestMessageSchema
        requestType =
            SchemaDefinitionsFulu.required(
                    spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
                .getDataColumnSidecarsByRangeRequestMessageSchema();

    final RpcContextCodec<Bytes4, DataColumnSidecar> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec, recentChainData, ForkDigestPayloadContext.DATA_COLUMN_SIDECAR);

    final DataColumnSidecarsByRangeMessageHandler dataColumnSidecarsByRangeMessageHandler =
        new DataColumnSidecarsByRangeMessageHandler(
            spec,
            metricsSystem,
            combinedChainDataClient,
            dataColumnSidecarArchiveReconstructor,
            dasLogger);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.DATA_COLUMN_SIDECARS_BY_RANGE,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            dataColumnSidecarsByRangeMessageHandler,
            peerLookup));
  }

  private static Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope>>
      createExecutionPayloadEnvelopesByRoot(
          final Spec spec,
          final AsyncRunner asyncRunner,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final CombinedChainDataClient combinedChainDataClient,
          final MetricsSystem metricsSystem,
          final ExecutionPayloadEnvelopesRequestLimiter requestLimiter) {

    if (!spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }

    final ExecutionPayloadEnvelopesByRootRequestMessageSchema requestType =
        SchemaDefinitionsGloas.required(
                spec.forMilestone(SpecMilestone.GLOAS).getSchemaDefinitions())
            .getExecutionPayloadEnvelopesByRootRequestMessageSchema();

    final RpcContextCodec<Bytes4, SignedExecutionPayloadEnvelope> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec,
            combinedChainDataClient.getRecentChainData(),
            ForkDigestPayloadContext.SIGNED_EXECUTION_PAYLOAD_ENVELOPE);

    final ExecutionPayloadEnvelopesByRootMessageHandler
        executionPayloadEnvelopesByRootMessageHandler =
            new ExecutionPayloadEnvelopesByRootMessageHandler(
                spec, combinedChainDataClient, metricsSystem, requestLimiter);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.EXECUTION_PAYLOAD_ENVELOPES_BY_ROOT,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            executionPayloadEnvelopesByRootMessageHandler,
            peerLookup));
  }

  private static Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope>>
      createExecutionPayloadEnvelopesByRange(
          final Spec spec,
          final AsyncRunner asyncRunner,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final CombinedChainDataClient combinedChainDataClient,
          final MetricsSystem metricsSystem,
          final ExecutionPayloadEnvelopesRequestLimiter requestLimiter) {

    if (!spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      return Optional.empty();
    }

    final ExecutionPayloadEnvelopesByRangeRequestMessage
            .ExecutionPayloadEnvelopesByRangeRequestMessageSchema
        requestType = ExecutionPayloadEnvelopesByRangeRequestMessage.SSZ_SCHEMA;

    final RpcContextCodec<Bytes4, SignedExecutionPayloadEnvelope> forkDigestContextCodec =
        RpcContextCodec.forkDigest(
            spec,
            combinedChainDataClient.getRecentChainData(),
            ForkDigestPayloadContext.SIGNED_EXECUTION_PAYLOAD_ENVELOPE);

    final ExecutionPayloadEnvelopesByRangeMessageHandler
        executionPayloadEnvelopesByRangeMessageHandler =
            new ExecutionPayloadEnvelopesByRangeMessageHandler(
                spec,
                getSpecConfigGloas(spec),
                metricsSystem,
                combinedChainDataClient,
                requestLimiter);

    return Optional.of(
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.EXECUTION_PAYLOAD_ENVELOPES_BY_RANGE,
            1,
            rpcEncoding,
            requestType,
            true,
            forkDigestContextCodec,
            executionPayloadEnvelopesByRangeMessageHandler,
            peerLookup));
  }

  private static Eth2RpcMethod<EmptyMessage, MetadataMessage> createMetadata(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final MetadataMessageHandler messageHandler =
        new MetadataMessageHandler(spec, metadataMessagesFactory);
    final EmptyMessageSchema requestType = EmptyMessage.SSZ_SCHEMA;
    final boolean expectResponse = true;
    final SszSchema<MetadataMessage> phase0MetadataSchema =
        SszSchema.as(
            MetadataMessage.class,
            spec.forMilestone(SpecMilestone.PHASE0)
                .getSchemaDefinitions()
                .getMetadataMessageSchema());
    final RpcContextCodec<?, MetadataMessage> phase0ContextCodec =
        RpcContextCodec.noop(phase0MetadataSchema);

    final SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage> v1Method =
        new SingleProtocolEth2RpcMethod<>(
            asyncRunner,
            BeaconChainMethodIds.GET_METADATA,
            1,
            rpcEncoding,
            requestType,
            expectResponse,
            phase0ContextCodec,
            messageHandler,
            peerLookup);

    final List<SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage>> versionedMethods =
        new ArrayList<>();

    if (spec.isMilestoneSupported(SpecMilestone.FULU)) {
      final SszSchema<MetadataMessage> fuluMetadataSchema =
          SszSchema.as(
              MetadataMessage.class,
              SchemaDefinitionsFulu.required(
                      spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions())
                  .getMetadataMessageSchema());
      final RpcContextCodec<?, MetadataMessage> fuluContextCodec =
          RpcContextCodec.noop(fuluMetadataSchema);

      final SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage> v3Method =
          new SingleProtocolEth2RpcMethod<>(
              asyncRunner,
              BeaconChainMethodIds.GET_METADATA,
              3,
              rpcEncoding,
              requestType,
              expectResponse,
              fuluContextCodec,
              messageHandler,
              peerLookup);
      versionedMethods.add(v3Method);
    }

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final SszSchema<MetadataMessage> altairMetadataSchema =
          SszSchema.as(
              MetadataMessage.class,
              spec.forMilestone(SpecMilestone.ALTAIR)
                  .getSchemaDefinitions()
                  .getMetadataMessageSchema());
      final RpcContextCodec<?, MetadataMessage> altairContextCodec =
          RpcContextCodec.noop(altairMetadataSchema);

      final SingleProtocolEth2RpcMethod<EmptyMessage, MetadataMessage> v2Method =
          new SingleProtocolEth2RpcMethod<>(
              asyncRunner,
              BeaconChainMethodIds.GET_METADATA,
              2,
              rpcEncoding,
              requestType,
              expectResponse,
              altairContextCodec,
              messageHandler,
              peerLookup);
      versionedMethods.add(v2Method);
      versionedMethods.add(v1Method);
      return VersionedEth2RpcMethod.create(
          rpcEncoding, requestType, expectResponse, versionedMethods);
    } else {
      return v1Method;
    }
  }

  private static Eth2RpcMethod<PingMessage, PingMessage> createPing(
      final AsyncRunner asyncRunner,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerLookup peerLookup,
      final RpcEncoding rpcEncoding) {
    final PingMessageHandler statusHandler = new PingMessageHandler(metadataMessagesFactory);
    final RpcContextCodec<?, PingMessage> contextCodec =
        RpcContextCodec.noop(PingMessage.SSZ_SCHEMA);
    return new SingleProtocolEth2RpcMethod<>(
        asyncRunner,
        BeaconChainMethodIds.PING,
        1,
        rpcEncoding,
        PingMessage.SSZ_SCHEMA,
        true,
        contextCodec,
        statusHandler,
        peerLookup);
  }

  private static <I extends RpcRequest, O extends SszData>
      Optional<Eth2RpcMethod<I, O>> createLightClientMethod(
          final Spec spec,
          final AsyncRunner asyncRunner,
          final PeerLookup peerLookup,
          final RpcEncoding rpcEncoding,
          final RecentChainData recentChainData,
          final Optional<LightClientUpdateStore> lightClientUpdateStore,
          final String methodId,
          final SszSchema<I> requestSchema,
          final ForkDigestPayloadContext<O> payloadContext,
          final Function<LightClientUpdateStore, LocalMessageHandler<I, O>> handlerFactory) {
    if (!spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      return Optional.empty();
    }
    return lightClientUpdateStore.map(
        store ->
            new SingleProtocolEth2RpcMethod<>(
                asyncRunner,
                methodId,
                1,
                rpcEncoding,
                requestSchema,
                true,
                RpcContextCodec.forkDigest(spec, recentChainData, payloadContext),
                handlerFactory.apply(store),
                peerLookup));
  }

  private static SpecConfigGloas getSpecConfigGloas(final Spec spec) {
    return SpecConfigGloas.required(spec.forMilestone(SpecMilestone.GLOAS).getConfig());
  }

  public Collection<RpcMethod<?, ?, ?>> all() {
    return Collections.unmodifiableCollection(allMethods);
  }

  public Eth2RpcMethod<StatusMessage, StatusMessage> status() {
    return status;
  }

  public Eth2RpcMethod<GoodbyeMessage, GoodbyeMessage> goodBye() {
    return goodBye;
  }

  public Eth2RpcMethod<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> beaconBlocksByRoot() {
    return beaconBlocksByRoot;
  }

  public Eth2RpcMethod<BeaconBlocksByRangeRequestMessage, SignedBeaconBlock> beaconBlocksByRange() {
    return beaconBlocksByRange;
  }

  public Optional<Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar>>
      blobSidecarsByRoot() {
    return blobSidecarsByRoot;
  }

  public Optional<Eth2RpcMethod<DataColumnSidecarsByRootRequestMessage, DataColumnSidecar>>
      dataColumnSidecarsByRoot() {
    return dataColumnSidecarsByRoot;
  }

  public Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRootRequestMessage, SignedExecutionPayloadEnvelope>>
      executionPayloadEnvelopesByRoot() {
    return executionPayloadEnvelopesByRoot;
  }

  public Optional<Eth2RpcMethod<DataColumnSidecarsByRangeRequestMessage, DataColumnSidecar>>
      getDataColumnSidecarsByRange() {
    return dataColumnSidecarsByRange;
  }

  public Optional<Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar>>
      blobSidecarsByRange() {
    return blobSidecarsByRange;
  }

  public Optional<
          Eth2RpcMethod<
              ExecutionPayloadEnvelopesByRangeRequestMessage, SignedExecutionPayloadEnvelope>>
      executionPayloadEnvelopesByRange() {
    return executionPayloadEnvelopesByRange;
  }

  public Eth2RpcMethod<EmptyMessage, MetadataMessage> getMetadata() {
    return getMetadata;
  }

  public Eth2RpcMethod<PingMessage, PingMessage> ping() {
    return ping;
  }

  public Optional<Eth2RpcMethod<EmptyMessage, LightClientFinalityUpdate>>
      lightClientFinalityUpdate() {
    return lightClientFinalityUpdate;
  }

  public Optional<Eth2RpcMethod<EmptyMessage, LightClientOptimisticUpdate>>
      lightClientOptimisticUpdate() {
    return lightClientOptimisticUpdate;
  }

  public Optional<Eth2RpcMethod<LightClientUpdatesByRangeRequestMessage, LightClientUpdate>>
      lightClientUpdatesByRange() {
    return lightClientUpdatesByRange;
  }

  public Optional<Eth2RpcMethod<LightClientBootstrapRequestMessage, LightClientBootstrap>>
      lightClientBootstrap() {
    return lightClientBootstrap;
  }
}
