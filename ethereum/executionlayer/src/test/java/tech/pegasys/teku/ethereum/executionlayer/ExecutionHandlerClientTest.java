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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.schema.ClientVersionV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class ExecutionHandlerClientTest {

  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;
  protected final ExecutionEngineClient executionEngineClient = mock(ExecutionEngineClient.class);

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void eth1GetPowBlock_shouldCallExecutionClient() {
    final ExecutionClientHandler handler = getHandler();
    final Bytes32 blockHash = Bytes32.random();
    final PowBlock block = createPowBlock(blockHash);

    when(executionEngineClient.getPowBlock(blockHash))
        .thenReturn(SafeFuture.completedFuture(block));

    final SafeFuture<Optional<PowBlock>> result = handler.eth1GetPowBlock(blockHash);
    assertThat(result).isCompletedWithValue(Optional.of(block));
  }

  private PowBlock createPowBlock(final Bytes32 blockHash) {
    return new PowBlock(
        blockHash, dataStructureUtil.randomBytes32(), dataStructureUtil.randomUInt64());
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test
  void eth1GetPowChainHead_shouldCallExecutionClient() {
    final ExecutionClientHandler handler = getHandler();
    final PowBlock block = createPowBlock(Bytes32.random());

    when(executionEngineClient.getPowChainHead()).thenReturn(SafeFuture.completedFuture(block));

    final SafeFuture<PowBlock> result = handler.eth1GetPowChainHead();
    assertThat(result).isCompletedWithValue(block);
  }

  @Test
  void engineGetClientVersion_shouldCallEngineGetClientVersionV1() {
    final ExecutionClientHandler handler = getHandler();

    final ClientVersion consensusClientVersion = dataStructureUtil.randomClientVersion();
    final ClientVersion executionClientVersion = dataStructureUtil.randomClientVersion();

    when(executionEngineClient.getClientVersionV1(
            ClientVersionV1.fromInternalClientVersion(consensusClientVersion)))
        .thenReturn(
            SafeFuture.completedFuture(
                Response.fromPayloadReceivedAsJson(
                    List.of(ClientVersionV1.fromInternalClientVersion(executionClientVersion)))));

    final SafeFuture<List<ClientVersion>> result =
        handler.engineGetClientVersion(consensusClientVersion);

    assertThat(result).isCompletedWithValue(List.of(executionClientVersion));
  }

  final ExecutionClientHandler getHandler() {
    return new ExecutionClientHandlerImpl(
        spec,
        executionEngineClient,
        new MilestoneBasedEngineJsonRpcMethodsResolver(spec, executionEngineClient));
  }
}
