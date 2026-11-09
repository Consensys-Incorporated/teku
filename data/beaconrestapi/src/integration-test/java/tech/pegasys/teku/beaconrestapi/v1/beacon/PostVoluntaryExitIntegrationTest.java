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

package tech.pegasys.teku.beaconrestapi.v1.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

import java.io.IOException;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beaconrestapi.AbstractDataBackedRestAPIIntegrationTest;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostVoluntaryExit;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class PostVoluntaryExitIntegrationTest extends AbstractDataBackedRestAPIIntegrationTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @BeforeEach
  public void setup() {
    startRestAPIAtGenesis();
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsInvalid() throws Exception {
    final Response response = post(PostVoluntaryExit.ROUTE, "{\"foo\": \"bar\"}");
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
  }

  @Test
  public void shouldReturnBadRequestWhenRequestBodyIsEmpty() throws Exception {
    checkEmptyBodyToRoute(PostVoluntaryExit.ROUTE, SC_BAD_REQUEST);
  }

  @Test
  public void shouldIncludeRejectReasonWhenOperationValidatorRejectsOperation() throws Exception {
    final SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.ONE);
    when(voluntaryExitPool.addLocal(signedVoluntaryExit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("the reason")));

    final Response response = getResponse(signedVoluntaryExit);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).contains("the reason");
  }

  @Test
  public void shouldReturnServerErrorWhenUnexpectedErrorHappens() throws Exception {
    final SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.ZERO);
    doThrow(new RuntimeException()).when(voluntaryExitPool).addLocal(signedVoluntaryExit);

    final Response response = getResponse(signedVoluntaryExit);
    assertThat(response.code()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
  }

  @Test
  public void shouldReturnSuccessWhenRequestBodyIsValid() throws Exception {
    // needs to be an exit in range, since state is checked
    final SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.ONE);
    when(voluntaryExitPool.addLocal(signedVoluntaryExit))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));

    final Response response = getResponse(signedVoluntaryExit);
    verify(voluntaryExitPool).addLocal(signedVoluntaryExit);
    assertThat(response.code()).isEqualTo(SC_OK);
  }

  @Test
  public void shouldRejectIfAlreadyExiting() throws IOException {
    final ChainBuilder.BlockOptions blockOptions =
        ChainBuilder.BlockOptions.create()
            .addProposerSlashing(dataStructureUtil.randomProposerSlashing(4));
    final SignedBlockAndState blockAndState = chainBuilder.generateBlockAtSlot(3, blockOptions);
    chainUpdater.updateBestBlock(blockAndState);
    final ProposerSlashing slashing = blockOptions.getProposerSlashings().getFirst();
    final UInt64 slashedProposerIndex =
        ((SignedBeaconBlockHeader) slashing.get(0)).getMessage().getProposerIndex();

    final SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(slashedProposerIndex);
    final Response response = getResponse(signedVoluntaryExit);
    verify(voluntaryExitPool, never()).addLocal(signedVoluntaryExit);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string()).containsIgnoringCase("already exiting");
  }

  @Test
  public void shouldFailGracefullyToAddInvalidValidatorIndex() throws IOException {
    // needs to be an exit in range, since state is checked
    final SignedVoluntaryExit signedVoluntaryExit =
        dataStructureUtil.randomSignedVoluntaryExit(UInt64.valueOf(99999));
    final Response response = getResponse(signedVoluntaryExit);

    verify(voluntaryExitPool, never()).addLocal(signedVoluntaryExit);
    assertThat(response.code()).isEqualTo(SC_BAD_REQUEST);
    assertThat(response.body().string())
        .containsIgnoringCase("Validator index 99999 was not found");
  }

  final Response getResponse(final SignedVoluntaryExit signedVoluntaryExit) throws IOException {
    return post(
        PostVoluntaryExit.ROUTE,
        JsonUtil.serialize(
            signedVoluntaryExit, signedVoluntaryExit.getSchema().getJsonTypeDefinition()));
  }
}
