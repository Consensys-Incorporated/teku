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

package tech.pegasys.teku.validator.client.loader;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtectorConcurrentAccess;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class SlashingProtectedValidatorSourceTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorSource delegate = mock(ValidatorSource.class);
  private final Path baseDir = Path.of("/data");
  private final SyncDataAccessor dataWriter = mock(SyncDataAccessor.class);
  private final SlashingProtector slashingProtector =
      new LocalSlashingProtectorConcurrentAccess(dataWriter, baseDir);
  private final ValidatorSource validatorSource =
      new SlashingProtectedValidatorSource(delegate, slashingProtector);

  @Test
  void shouldDelegateCanAddValidator() {
    validatorSource.canUpdateValidators();
    verify(delegate).canUpdateValidators();
  }

  @Test
  void shouldDelegateDeleteValidator() {
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    validatorSource.deleteValidator(publicKey);
    verify(delegate).deleteValidator(publicKey);
  }

  @Test
  void availableValidators_shouldBeReadOnly() {
    final ValidatorSource.ValidatorProvider provider =
        mock(ValidatorSource.ValidatorProvider.class);
    when(provider.isReadOnly()).thenReturn(true);
    doReturn(List.of(provider)).when(delegate).getAvailableValidators();
    assertThat(validatorSource.getAvailableValidators().get(0).isReadOnly()).isTrue();
  }

  @Test
  void addValidatorWithSignerUrl_shouldWrapWithSlashingProtectedSigner() {
    final Signer signer = mock(Signer.class);
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    when(signer.signBlock(any(), any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(delegate.addValidator(publicKey, Optional.<URL>empty()))
        .thenReturn(new AddValidatorResult(PostKeyResult.success(), Optional.of(signer)));

    final AddValidatorResult result = validatorSource.addValidator(publicKey, Optional.empty());
    final Signer slashingSigner = result.getSigner().orElseThrow();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1234);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    assertThat(slashingSigner.signBlock(block, forkInfo)).isCompleted();
    assertThat(slashingSigner.signBlock(block, forkInfo)).isCompletedExceptionally();
  }

  @Test
  void addValidator_shouldDemonstrateSlashingProtectedSigner() {
    final KeyStoreData keyStoreData = mock(KeyStoreData.class);
    final Signer signer = mock(Signer.class);
    final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
    when(signer.signBlock(any(), any()))
        .thenReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()));
    when(keyStoreData.getPubkey()).thenReturn(publicKey.toSSZBytes());
    when(delegate.addValidator(any(), any(), any()))
        .thenReturn(new AddValidatorResult(PostKeyResult.success(), Optional.of(signer)));

    final AddValidatorResult result = validatorSource.addValidator(keyStoreData, "pass", publicKey);
    final Signer slashingSigner = result.getSigner().orElseThrow();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(1234);
    final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
    assertThat(slashingSigner.signBlock(block, forkInfo)).isCompleted();
    assertThat(slashingSigner.signBlock(block, forkInfo)).isCompletedExceptionally();
  }
}
