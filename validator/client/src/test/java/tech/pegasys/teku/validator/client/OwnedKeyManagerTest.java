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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner.NO_OP_SIGNER;
import static tech.pegasys.teku.spec.generator.signatures.NoOpRemoteSigner.NO_OP_REMOTE_SIGNER;

import com.google.common.io.Resources;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.data.SlashingProtectionIncrementalExporter;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ExternalValidator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

class OwnedKeyManagerTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalAltair());
  private final ValidatorLoader validatorLoader = mock(ValidatorLoader.class);
  private final OwnedValidators ownedValidators = mock(OwnedValidators.class);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final SlashingProtectionIncrementalExporter exporter =
      mock(SlashingProtectionIncrementalExporter.class);
  private final Signer signer = mock(Signer.class);
  private final ValidatorTimingChannel channel = mock(ValidatorTimingChannel.class);
  private final OwnedKeyManager keyManager = new OwnedKeyManager(validatorLoader, channel);
  private final DoppelgangerDetector doppelgangerDetector = mock(DoppelgangerDetector.class);
  private final SlashingRiskAction doppelgangerDetectionAction = mock(SlashingRiskAction.class);
  private final BLSPublicKey doppelgangerPublicKey =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "0x919cada9266988cb323502275b2226988cb32359c112bf88d6c5043cd41a833938b2c97a6ee4a11e82e171d8e71fd546"));

  private final BLSPublicKey validatorPublicKey =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "0x9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07"));

  private final LogCaptor logCaptor = LogCaptor.forClass(OwnedKeyManager.class);

  @Test
  void shouldReturnNotFoundIfSlashingProtectionNotFound() {
    when(exporter.haveSlashingProtectionData(publicKey)).thenReturn(false);

    final DeleteKeyResult result =
        keyManager.attemptToGetSlashingDataForDisabledValidator(publicKey, exporter);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.NOT_FOUND);
  }

  @Test
  void shouldCallValidatorsAddedOnSuccessfulImport() throws URISyntaxException, IOException {
    final String data = getKeystore("pbkdf2TestVector.json");

    when(validatorLoader.loadLocalMutableValidator(any(), any(), any(), anyBoolean()))
        .thenReturn(new LocalValidatorImportResult.Builder(PostKeyResult.success(), "").build());
    keyManager.importValidators(
        List.of(data),
        List.of("testpassword"),
        Optional.empty(),
        Optional.empty(),
        doppelgangerDetectionAction);
    verify(channel, times(1)).onValidatorsAdded();
  }

  @Test
  void shouldNotCallValidatorsAddedOnUnsuccessfulImport() throws URISyntaxException, IOException {
    final String data = getKeystore("pbkdf2TestVector.json");

    when(validatorLoader.loadLocalMutableValidator(any(), any(), any(), anyBoolean()))
        .thenReturn(new LocalValidatorImportResult.Builder(PostKeyResult.duplicate(), "").build());
    keyManager.importValidators(
        List.of(data),
        List.of("testpassword"),
        Optional.empty(),
        Optional.empty(),
        doppelgangerDetectionAction);
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void shouldReturnNotActiveIfSlashingProtectionFound() {
    when(exporter.haveSlashingProtectionData(publicKey)).thenReturn(true);

    final DeleteKeyResult result =
        keyManager.attemptToGetSlashingDataForDisabledValidator(publicKey, exporter);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.NOT_ACTIVE);
  }

  @Test
  void deleteValidator_shouldStopAndDeleteValidator() {
    final Validator validator = mock(Validator.class);

    when(validator.getPublicKey()).thenReturn(publicKey);
    when(validator.isReadOnly()).thenReturn(false);
    when(validator.getSigner()).thenReturn(signer);
    when(exporter.addPublicKeyToExport(eq(publicKey), any())).thenReturn(Optional.empty());
    when(validatorLoader.deleteLocalMutableValidator(publicKey))
        .thenReturn(DeleteKeyResult.success());

    final DeleteKeyResult result = keyManager.deleteLocalValidator(validator, exporter);
    verify(signer).delete();
    verify(validatorLoader).deleteLocalMutableValidator(publicKey);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.DELETED);
    assertThat(result.getMessage()).isEmpty();
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void deleteValidator_shouldReportSlashingExportError(@TempDir final Path tempDir)
      throws IOException {
    Files.createFile(
        tempDir.resolve(publicKey.toBytesCompressed().toUnprefixedHexString() + ".yml"));
    final SlashingProtectionIncrementalExporter exporter =
        new SlashingProtectionIncrementalExporter(tempDir);
    final Validator validator = mock(Validator.class);

    when(validator.getPublicKey()).thenReturn(publicKey);
    when(validator.isReadOnly()).thenReturn(false);
    when(validator.getSigner()).thenReturn(signer);
    when(validatorLoader.deleteLocalMutableValidator(publicKey))
        .thenReturn(DeleteKeyResult.success());

    final DeleteKeyResult result = keyManager.deleteLocalValidator(validator, exporter);
    verify(signer).delete();
    verify(validatorLoader).deleteLocalMutableValidator(publicKey);
    assertThat(result.getStatus()).isEqualTo(DeletionStatus.ERROR);
    assertThat(result.getMessage().orElse("")).startsWith("Failed to read from file");
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void deleteValidators_shouldStopAndDeleteValidator(@TempDir final Path tempDir) {
    final Validator validator = mock(Validator.class);

    when(validator.getPublicKey()).thenReturn(publicKey);
    when(validator.isReadOnly()).thenReturn(false);
    when(validator.getSigner()).thenReturn(signer);
    when(validatorLoader.getOwnedValidators())
        .thenReturn(new OwnedValidators(Map.of(publicKey, validator)));
    when(exporter.addPublicKeyToExport(eq(publicKey), any())).thenReturn(Optional.empty());
    when(validatorLoader.deleteLocalMutableValidator(publicKey))
        .thenReturn(DeleteKeyResult.success());

    final DeleteKeysResponse response = keyManager.deleteValidators(List.of(publicKey), tempDir);
    verify(signer).delete();
    verify(validatorLoader).deleteLocalMutableValidator(publicKey);

    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeletionStatus.DELETED);
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getSlashingProtection()).isNotEmpty();
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void deleteValidators_shouldRejectRequestToDeleteReadOnlyValidator(@TempDir final Path tempDir) {
    final Validator validator = mock(Validator.class);

    when(validator.isReadOnly()).thenReturn(true);
    when(validatorLoader.getOwnedValidators())
        .thenReturn(new OwnedValidators(Map.of(publicKey, validator)));
    when(validator.getPublicKey()).thenReturn(publicKey);

    final DeleteKeysResponse response = keyManager.deleteValidators(List.of(publicKey), tempDir);
    assertThat(response.getData()).hasSize(1);
    assertThat(response.getData().get(0).getStatus()).isEqualTo(DeletionStatus.ERROR);
    assertThat(response.getData().get(0).getMessage().orElse("")).contains("read-only");
    assertThat(response.getSlashingProtection()).isNotEmpty();
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void shouldReturnLocalValidatorsList() {
    final List<Validator> validatorsList = generateLocalValidatorsList();
    when(ownedValidators.getValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    final List<Validator> localValidatorList = keyManager.getLocalValidatorKeys();

    assertThat(localValidatorList).isEqualTo(validatorsList);
    verify(channel, never()).onValidatorsAdded();
  }

  @Test
  void shouldReturnEmptyKeyList() {
    final List<Validator> validatorsList = Collections.emptyList();
    when(ownedValidators.getValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    final List<Validator> localValidatorList = keyManager.getLocalValidatorKeys();

    assertThat(localValidatorList).isEmpty();
  }

  private List<Validator> generateLocalValidatorsList() {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_SIGNER, Optional::empty, true);
    final Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_SIGNER, Optional::empty, false);
    return Arrays.asList(validator1, validator2);
  }

  @Test
  void shouldReturnRemoteValidatorsList() throws MalformedURLException {
    final BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);
    final BLSKeyPair keyPair2 = BLSTestUtil.randomKeyPair(2);
    final BLSKeyPair keyPair3 = BLSTestUtil.randomKeyPair(3);
    final Validator validator1 =
        new Validator(keyPair1.getPublicKey(), NO_OP_REMOTE_SIGNER, Optional::empty, true);
    final Validator validator2 =
        new Validator(keyPair2.getPublicKey(), NO_OP_REMOTE_SIGNER, Optional::empty, false);
    final Validator validator3 =
        new Validator(keyPair3.getPublicKey(), NO_OP_SIGNER, Optional::empty, false);
    List<Validator> localValidators = Arrays.asList(validator1, validator2, validator3);

    when(ownedValidators.getValidators()).thenReturn(localValidators);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    final List<ExternalValidator> result = keyManager.getRemoteValidatorKeys();

    List<ExternalValidator> externalValidators =
        Arrays.asList(
            new ExternalValidator(
                keyPair1.getPublicKey(),
                Optional.of(URI.create("http://example.com/").toURL()),
                true),
            new ExternalValidator(
                keyPair2.getPublicKey(),
                Optional.of(URI.create("http://example.com/").toURL()),
                false));
    assertThat(result).isEqualTo(externalValidators);
  }

  @Test
  void shouldReturnEmptyRemoteKeyList() {
    final List<Validator> validatorsList = Collections.emptyList();
    when(ownedValidators.getValidators()).thenReturn(validatorsList);
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    final List<ExternalValidator> remoteValidatorList = keyManager.getRemoteValidatorKeys();

    assertThat(remoteValidatorList).isEmpty();
  }

  @Test
  void shouldDetectDoppelgangerAndIgnoreLocalKey() throws IOException, URISyntaxException {
    final String validatorPassword = "validatorPassword";
    final String doppelgangerPassword = "doppelgangerPassword";
    final KeyStoreData doppelgangerKeyStoreData = mock(KeyStoreData.class);
    LocalValidatorImportResult doppelgangerImportResult =
        new LocalValidatorImportResult.Builder(PostKeyResult.success(), doppelgangerPassword)
            .publicKey(Optional.of(doppelgangerPublicKey))
            .keyStoreData(Optional.of(doppelgangerKeyStoreData))
            .build();
    when(validatorLoader.loadLocalMutableValidator(
            eq(doppelgangerKeyStoreData), eq(doppelgangerPassword), any(), anyBoolean()))
        .thenReturn(doppelgangerImportResult);
    when(validatorLoader.addValidator(
            eq(doppelgangerKeyStoreData), eq(doppelgangerPassword), eq(doppelgangerPublicKey)))
        .thenReturn(doppelgangerImportResult);
    when(validatorLoader.loadLocalMutableValidator(
            any(), eq(doppelgangerPassword), any(), eq(false)))
        .thenReturn(doppelgangerImportResult);
    final KeyStoreData validatorKeyStoreData = mock(KeyStoreData.class);
    LocalValidatorImportResult validatorImportResult =
        new LocalValidatorImportResult.Builder(PostKeyResult.success(), validatorPassword)
            .publicKey(Optional.of(validatorPublicKey))
            .keyStoreData(Optional.of(validatorKeyStoreData))
            .build();
    when(validatorLoader.loadLocalMutableValidator(
            eq(validatorKeyStoreData), eq(validatorPassword), any(), anyBoolean()))
        .thenReturn(validatorImportResult);
    when(validatorLoader.addValidator(
            eq(validatorKeyStoreData), eq(validatorPassword), eq(validatorPublicKey)))
        .thenReturn(validatorImportResult);
    when(validatorLoader.loadLocalMutableValidator(any(), eq(validatorPassword), any(), eq(false)))
        .thenReturn(validatorImportResult);

    when(doppelgangerDetector.performDoppelgangerDetection(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Map.ofEntries(Map.entry(UInt64.valueOf(5), doppelgangerPublicKey))));

    final Validator doppelganger = mock(Validator.class);
    when(doppelganger.getPublicKey()).thenReturn(doppelgangerPublicKey);
    when(doppelganger.isReadOnly()).thenReturn(false);
    when(doppelganger.getSigner()).thenReturn(signer);
    when(exporter.addPublicKeyToExport(eq(doppelgangerPublicKey), any()))
        .thenReturn(Optional.empty());
    when(validatorLoader.getOwnedValidators()).thenReturn(ownedValidators);
    when(ownedValidators.getValidator(any())).thenReturn(Optional.of(doppelganger));

    final String validatorData = getKeystore("pbkdf2TestVector.json");
    final String doppelgangerData = getKeystore("doppelgangerKeyStore.json");

    keyManager.importValidators(
        List.of(validatorData, doppelgangerData),
        List.of(validatorPassword, doppelgangerPassword),
        Optional.empty(),
        Optional.of(doppelgangerDetector),
        doppelgangerDetectionAction);
    verify(validatorLoader, never()).addExternalValidator(any(), any());
    verify(validatorLoader, never())
        .addValidator(doppelgangerKeyStoreData, doppelgangerPassword, doppelgangerPublicKey);
    verify(validatorLoader, times(1))
        .addValidator(validatorKeyStoreData, validatorPassword, validatorPublicKey);
    verify(channel, times(1)).onValidatorsAdded();
    verify(doppelgangerDetector)
        .performDoppelgangerDetection(Set.of(validatorPublicKey, doppelgangerPublicKey));
    verify(doppelgangerDetectionAction).perform(List.of(doppelgangerPublicKey));
    verify(validatorLoader, times(1))
        .addValidator(validatorKeyStoreData, validatorPassword, validatorPublicKey);
    verify(validatorLoader, never())
        .addValidator(doppelgangerKeyStoreData, doppelgangerPassword, doppelgangerPublicKey);
  }

  @Test
  void shouldAddLocalKeysWhenDoppelgangerDetectionException()
      throws IOException, URISyntaxException {
    final String data = getKeystore("pbkdf2TestVector.json");
    final String doppelgangerPassword = "doppelgangerPassword";
    final KeyStoreData doppelgangerKeyStoreData = mock(KeyStoreData.class);
    final LocalValidatorImportResult doppelgangerImportResult =
        new LocalValidatorImportResult.Builder(PostKeyResult.success(), doppelgangerPassword)
            .publicKey(Optional.of(doppelgangerPublicKey))
            .keyStoreData(Optional.of(doppelgangerKeyStoreData))
            .build();
    when(validatorLoader.loadLocalMutableValidator(any(), any(), any(), anyBoolean()))
        .thenReturn(doppelgangerImportResult);
    when(validatorLoader.addValidator(
            eq(doppelgangerKeyStoreData), eq(doppelgangerPassword), eq(doppelgangerPublicKey)))
        .thenReturn(doppelgangerImportResult);
    when(doppelgangerDetector.performDoppelgangerDetection(any()))
        .thenReturn(SafeFuture.failedFuture(new Exception("Doppelganger Detection Exception")));

    keyManager.importValidators(
        List.of(data),
        List.of(doppelgangerPassword),
        Optional.empty(),
        Optional.of(doppelgangerDetector),
        doppelgangerDetectionAction);

    verify(channel).onValidatorsAdded();
    verify(validatorLoader, times(1))
        .addValidator(any(), eq(doppelgangerPassword), eq(doppelgangerPublicKey));
    verify(doppelgangerDetector).performDoppelgangerDetection(Set.of(doppelgangerPublicKey));
    verify(doppelgangerDetectionAction, never()).perform(anyList());
    logCaptor.assertErrorLog(
        String.format(
            "Failed to perform doppelganger detection for public keys %s",
            doppelgangerPublicKey.toAbbreviatedString()));
    verify(validatorLoader, times(1))
        .addValidator(doppelgangerKeyStoreData, doppelgangerPassword, doppelgangerPublicKey);
  }

  @Test
  void shouldDetectDoppelgangerAndIgnoreExternalKey() throws MalformedURLException {

    when(signer.getSigningServiceUrl())
        .thenReturn(Optional.of(URI.create("https://url.test").toURL()));

    ExternalValidatorImportResult externalDoppelgangerImportResult =
        new ExternalValidatorImportResult.Builder(
                PostKeyResult.success(), signer.getSigningServiceUrl())
            .publicKey(Optional.of(doppelgangerPublicKey))
            .build();
    when(validatorLoader.loadExternalMutableValidator(
            eq(doppelgangerPublicKey), any(), anyBoolean()))
        .thenReturn(externalDoppelgangerImportResult);

    ExternalValidatorImportResult externalValidatorImportResult =
        new ExternalValidatorImportResult.Builder(
                PostKeyResult.success(), signer.getSigningServiceUrl())
            .publicKey(Optional.of(validatorPublicKey))
            .build();

    when(validatorLoader.loadExternalMutableValidator(eq(validatorPublicKey), any(), anyBoolean()))
        .thenReturn(externalValidatorImportResult);

    when(validatorLoader.addExternalValidator(any(), eq(validatorPublicKey)))
        .thenReturn(externalDoppelgangerImportResult);

    when(doppelgangerDetector.performDoppelgangerDetection(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Map.ofEntries(Map.entry(UInt64.valueOf(5), doppelgangerPublicKey))));

    final ExternalValidator externalDoppelganger = mock(ExternalValidator.class);
    when(externalDoppelganger.getPublicKey()).thenReturn(doppelgangerPublicKey);
    Optional<URL> signerUrl = signer.getSigningServiceUrl();
    when(externalDoppelganger.getUrl()).thenReturn(signerUrl);

    final ExternalValidator externalValidator = mock(ExternalValidator.class);
    when(externalValidator.getPublicKey()).thenReturn(validatorPublicKey);
    when(externalValidator.getUrl()).thenReturn(signerUrl);

    keyManager.importExternalValidators(
        List.of(externalDoppelganger, externalValidator),
        Optional.of(doppelgangerDetector),
        doppelgangerDetectionAction);

    verify(channel, times(1)).onValidatorsAdded();
    verify(doppelgangerDetector)
        .performDoppelgangerDetection(Set.of(validatorPublicKey, doppelgangerPublicKey));
    verify(doppelgangerDetectionAction).perform(List.of(doppelgangerPublicKey));
    verify(validatorLoader, never()).addExternalValidator(signerUrl, doppelgangerPublicKey);
    verify(validatorLoader, times(1)).addExternalValidator(signerUrl, validatorPublicKey);
  }

  @Test
  void shouldAddExternalKeysWhenDoppelgangerDetectionException() throws MalformedURLException {
    when(signer.getSigningServiceUrl())
        .thenReturn(Optional.of(URI.create("https://url.test").toURL()));

    ExternalValidatorImportResult doppelgangerImportResult =
        new ExternalValidatorImportResult.Builder(
                PostKeyResult.success(), signer.getSigningServiceUrl())
            .publicKey(Optional.of(doppelgangerPublicKey))
            .build();

    Optional<URL> signerUrl = signer.getSigningServiceUrl();

    when(validatorLoader.loadExternalMutableValidator(
            eq(doppelgangerPublicKey), eq(signerUrl), eq(false)))
        .thenReturn(doppelgangerImportResult);

    when(validatorLoader.addExternalValidator(any(), eq(doppelgangerPublicKey)))
        .thenReturn(doppelgangerImportResult);

    when(doppelgangerDetector.performDoppelgangerDetection(any()))
        .thenReturn(SafeFuture.failedFuture(new Exception("Doppelganger Detection Exception")));

    final ExternalValidator doppelganger = mock(ExternalValidator.class);

    when(doppelganger.getPublicKey()).thenReturn(doppelgangerPublicKey);
    when(doppelganger.getUrl()).thenReturn(signerUrl);

    keyManager.importExternalValidators(
        List.of(doppelganger), Optional.of(doppelgangerDetector), doppelgangerDetectionAction);

    verify(channel).onValidatorsAdded();
    verify(doppelgangerDetector).performDoppelgangerDetection(Set.of(doppelgangerPublicKey));
    verify(doppelgangerDetectionAction, never()).perform(anyList());
    verify(validatorLoader, times(1)).addExternalValidator(signerUrl, doppelgangerPublicKey);
    logCaptor.assertErrorLog(
        String.format(
            "Failed to perform doppelganger detection for public keys %s",
            doppelgangerPublicKey.toAbbreviatedString()));
  }

  private String getKeystore(final String fileName) throws IOException, URISyntaxException {
    final URL resource = Resources.getResource(fileName);
    FileInputStream fis = new FileInputStream(new File(resource.toURI()));
    return IOUtils.toString(fis, StandardCharsets.UTF_8);
  }
}
