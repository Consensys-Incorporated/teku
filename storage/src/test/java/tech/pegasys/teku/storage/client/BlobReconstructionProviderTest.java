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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;

public class BlobReconstructionProviderTest extends BlobReconstructionAbstractTest {
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);

  private final ExtensionBlobReconstructor extensionBlobReconstructor =
      mock(ExtensionBlobReconstructor.class);
  private final CryptoBlobReconstructor cryptoBlobReconstructor =
      mock(CryptoBlobReconstructor.class);
  private final NetworkBlobReconstructor networkBlobReconstructor =
      mock(NetworkBlobReconstructor.class);

  private final BlobReconstructionProvider blobReconstructionProvider =
      new BlobReconstructionProvider(
          client,
          spec,
          extensionBlobReconstructor,
          cryptoBlobReconstructor,
          networkBlobReconstructor);

  private final SlotAndBlockRoot slotAndBlockRoot = dataStructureUtil.randomSlotAndBlockRoot();

  @Test
  public void shouldReturnBlobsWithExtensionReconstructor() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    initializedClientMock(blobsAndMatrix);

    when(extensionBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobsAndMatrix.blobs())));

    final SafeFuture<List<Blob>> result =
        blobReconstructionProvider.reconstructBlobs(
            slotAndBlockRoot, Collections.emptyList(), true);
    assertThat(result).isCompletedWithValueMatching(list -> list.equals(blobsAndMatrix.blobs()));

    verify(client, times(numberOfColumns / 2)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(extensionBlobReconstructor).reconstructBlobs(any(), any(), any());
    verifyNoInteractions(cryptoBlobReconstructor);
    verifyNoInteractions(networkBlobReconstructor);
  }

  @Test
  public void shouldReturnBlobsWithCryptoReconstructor() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    initializedClientMock(blobsAndMatrix);

    when(extensionBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(cryptoBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobsAndMatrix.blobs())));

    final SafeFuture<List<Blob>> result =
        blobReconstructionProvider.reconstructBlobs(
            slotAndBlockRoot, Collections.emptyList(), true);
    assertThat(result).isCompletedWithValueMatching(list -> list.equals(blobsAndMatrix.blobs()));

    verify(client, times(numberOfColumns)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(extensionBlobReconstructor).reconstructBlobs(any(), any(), any());
    verify(cryptoBlobReconstructor).reconstructBlobs(any(), any(), any());
    verifyNoInteractions(networkBlobReconstructor);
  }

  @Test
  public void shouldReturnBlobsWithNetworkReconstructor() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    initializedClientMock(blobsAndMatrix);

    when(extensionBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(cryptoBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(networkBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobsAndMatrix.blobs())));

    final SafeFuture<List<Blob>> result =
        blobReconstructionProvider.reconstructBlobs(
            slotAndBlockRoot, Collections.emptyList(), true);
    assertThat(result).isCompletedWithValueMatching(list -> list.equals(blobsAndMatrix.blobs()));

    verify(client, times(numberOfColumns)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(extensionBlobReconstructor).reconstructBlobs(any(), any(), any());
    verify(cryptoBlobReconstructor).reconstructBlobs(any(), any(), any());
    verify(networkBlobReconstructor).reconstructBlobs(any(), any(), any());
  }

  @Test
  public void shouldReturnEmptyListWhenAllReconstructorsFail() {
    final int numberOfColumns = spec.getNumberOfDataColumns().orElseThrow();

    final BlobsAndMatrix blobsAndMatrix = loadBlobsAndMatrixFixture();
    initializedClientMock(blobsAndMatrix);

    when(extensionBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(cryptoBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(networkBlobReconstructor.reconstructBlobs(any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<List<Blob>> result =
        blobReconstructionProvider.reconstructBlobs(
            slotAndBlockRoot, Collections.emptyList(), true);

    assertThat(result).isCompletedWithValueMatching(List::isEmpty);

    // Verify all three were tried in order
    verify(client, times(numberOfColumns)).getSidecar(any(DataColumnSlotAndIdentifier.class));
    verify(extensionBlobReconstructor).reconstructBlobs(any(), any(), any());
    verify(cryptoBlobReconstructor).reconstructBlobs(any(), any(), any());
    verify(networkBlobReconstructor).reconstructBlobs(any(), any(), any());
  }

  private void initializedClientMock(final BlobsAndMatrix blobsAndMatrix) {
    final BeaconBlockBody beaconBlockBody =
        dataStructureUtil.randomBeaconBlockBodyWithCommitments(2);
    final SignedBeaconBlock block =
        dataStructureUtil.signedBlock(
            dataStructureUtil.randomBeaconBlock(slotAndBlockRoot.getSlot(), beaconBlockBody));

    final MiscHelpersFulu miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());

    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            block.getMessage(), block.asHeader(), blobsAndMatrix.extendedMatrix());

    when(client.getSidecar(any(DataColumnSlotAndIdentifier.class)))
        .thenAnswer(
            invocationOnMock -> {
              final DataColumnSlotAndIdentifier identifier = invocationOnMock.getArgument(0);
              return SafeFuture.completedFuture(
                  Optional.of(dataColumnSidecars.get(identifier.columnIndex().intValue())));
            });
    when(client.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
  }
}
