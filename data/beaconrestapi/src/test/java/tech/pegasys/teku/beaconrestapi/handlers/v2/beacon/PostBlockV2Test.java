/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.beacon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.HEADER_BUILDER_URL;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beaconrestapi.handlers.v1.beacon.PostBlockTest;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;

public class PostBlockV2Test extends PostBlockTest {
  @Override
  public RestApiEndpoint getHandler() {
    return new PostBlockV2(validatorDataProvider, syncDataProvider, spec, schemaDefinitionCache);
  }

  @Test
  void shouldPerformGossipValidationWhenNoValidationIsSpecified() throws Exception {

    final SignedBeaconBlock block = getRandomSignedBeaconBlock();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(block);

    setupValidatorDataProviderSubmit(
        SafeFuture.completedFuture(SendSignedBlockResult.success(block.getRoot())));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();

    verify(validatorDataProvider)
        .submitSignedBlock(eq(block), eq(BroadcastValidationLevel.GOSSIP), eq(Optional.empty()));
  }

  @Test
  void shouldPerformValidationWhenValidationIsSpecified() throws Exception {

    final SignedBeaconBlock block = getRandomSignedBeaconBlock();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(block);
    request.setOptionalQueryParameter("broadcast_validation", "consensus");

    setupValidatorDataProviderSubmit(
        SafeFuture.completedFuture(SendSignedBlockResult.success(block.getRoot())));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();

    verify(validatorDataProvider)
        .submitSignedBlock(eq(block), eq(BroadcastValidationLevel.CONSENSUS), eq(Optional.empty()));
  }

  @Test
  void shouldGetTheBuilderUrlFromTheHeaderAndPassItWhenSubmittingTheBlock() throws Exception {

    final SignedBeaconBlock block = getRandomSignedBeaconBlock();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(block);
    request.setRequestHeader(HEADER_BUILDER_URL, "https://foobar.com");

    setupValidatorDataProviderSubmit(
        SafeFuture.completedFuture(SendSignedBlockResult.success(block.getRoot())));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_OK);
    assertThat(request.getResponseBody()).isNull();

    verify(validatorDataProvider)
        .submitSignedBlock(
            eq(block), eq(BroadcastValidationLevel.GOSSIP), eq(Optional.of("https://foobar.com")));
  }

  @Test
  void shouldThrowIllegalArgumentExceptionWhenValidationIsWrong() {
    final SignedBeaconBlock block = getRandomSignedBeaconBlock();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(block);
    request.setOptionalQueryParameter("broadcast_validation", "wrong");

    assertThatThrownBy(() -> handler.handleRequest(request))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldReturnBadRequestWhenBroadcastValidationFails() throws Exception {
    final SignedBeaconBlock block = getRandomSignedBeaconBlock();
    when(syncService.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    request.setRequestBody(block);

    setupValidatorDataProviderSubmit(
        SafeFuture.completedFuture(
            SendSignedBlockResult.rejected(
                FailureReason.FAILED_BROADCAST_VALIDATION.name() + ": whatever")));

    handler.handleRequest(request);

    assertThat(request.getResponseCode()).isEqualTo(SC_BAD_REQUEST);
  }
}
