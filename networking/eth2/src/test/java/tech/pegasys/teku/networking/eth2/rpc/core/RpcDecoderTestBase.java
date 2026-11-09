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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.mockito.Mockito.mock;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.ProtobufEncoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.SingleProtocolEth2RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;

public class RpcDecoderTestBase {

  protected static final String ERROR_MESSAGE = "Bad request";
  protected static final Bytes ERROR_MESSAGE_PLAIN_DATA =
      Bytes.wrap(ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8));

  protected final Spec spec = TestSpecFactory.createDefault();

  // Message long enough to require a three byte length prefix.
  protected final BeaconBlocksByRootRequestMessage message = createRequestMessage(600);
  protected final Compressor compressor = SnappyFramedCompressor.AIRCOMPRESSOR;
  protected final RpcEncoding encoding =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize(), compressor);
  protected final Bytes messagePlainData = message.sszSerialize();
  protected final Bytes messageData = compressor.compress(messagePlainData);
  protected final Bytes lengthPrefix = getLengthPrefix(messagePlainData.size());
  protected final Bytes errorMessageData = compressor.compress(ERROR_MESSAGE_PLAIN_DATA);
  protected final Bytes errorMessageLengthPrefix = getLengthPrefix(ERROR_MESSAGE_PLAIN_DATA.size());

  protected final AsyncRunner asyncRunner = new StubAsyncRunner();
  protected final PeerLookup peerLookup = mock(PeerLookup.class);

  protected final RpcContextCodec<Bytes, BeaconBlocksByRootRequestMessage> contextEncoder =
      RpcContextCodec.noop(
          spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema());
  protected final RpcResponseDecoder<BeaconBlocksByRootRequestMessage, Bytes> responseDecoder =
      RpcResponseDecoder.create(encoding, contextEncoder);

  @SuppressWarnings("unchecked")
  protected final Eth2RpcMethod<BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>
      method =
          new SingleProtocolEth2RpcMethod<
              BeaconBlocksByRootRequestMessage, BeaconBlocksByRootRequestMessage>(
              asyncRunner,
              "",
              1,
              encoding,
              spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(),
              false,
              contextEncoder,
              mock(LocalMessageHandler.class),
              peerLookup);

  protected List<List<ByteBuf>> testByteBufSlices(final Bytes... bytes) {
    List<List<ByteBuf>> ret = Utils.generateTestSlices(bytes);

    return ret;
  }

  protected BeaconBlocksByRootRequestMessage createRequestMessage(final int blocksRequested) {
    final List<Bytes32> roots = new ArrayList<>();
    for (int i = 0; i < blocksRequested; i++) {
      roots.add(Bytes32.leftPad(Bytes.ofUnsignedInt(i)));
    }
    return new BeaconBlocksByRootRequestMessage(
        spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(), roots);
  }

  protected static Bytes getLengthPrefix(final int size) {
    return ProtobufEncoder.encodeVarInt(size);
  }
}
