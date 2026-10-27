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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import com.google.common.io.Resources;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.techu.service.serviceutils.layout.SimpleDataDirLayout;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.data.SlashingProtectionImporter;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtector;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtectorConcurrentAccess;
import tech.pegasys.teku.spec.signatures.SlashableConditionException;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.SlashingProtectionLogger;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;

/** Keymanager import and delete flows against a real slashing protector and record files. */
class OwnedKeyManagerSlashingProtectionTest {
  private static final String PASSWORD = "testpassword";
  private static final BLSPublicKey PUBLIC_KEY =
      BLSPublicKey.fromSSZBytes(
          Bytes.fromHexString(
              "0x9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07"));

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final Bytes32 genesisValidatorsRoot = forkInfo.getGenesisValidatorsRoot();
  private final String keystore = loadKeystore();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubAsyncRunner loggerAsyncRunner = new StubAsyncRunner();

  private Path slashingProtectionPath;
  private ValidatorLoader validatorLoader;
  private SlashingProtectionLogger slashingProtectionLogger;
  private OwnedKeyManager keyManager;

  private void setup(final Path tempDir, final boolean concurrentProtector) {
    final DataDirLayout dataDirLayout = new SimpleDataDirLayout(tempDir);
    slashingProtectionPath = ValidatorClientService.getSlashingProtectionPath(dataDirLayout);
    final SyncDataAccessor dataAccessor = SyncDataAccessor.create(slashingProtectionPath);
    final SlashingProtector slashingProtector =
        concurrentProtector
            ? new LocalSlashingProtectorConcurrentAccess(dataAccessor, slashingProtectionPath)
            : new LocalSlashingProtector(dataAccessor, slashingProtectionPath);
    slashingProtectionLogger =
        new SlashingProtectionLogger(
            slashingProtector, spec, loggerAsyncRunner, mock(ValidatorLogger.class));
    validatorLoader =
        ValidatorLoader.create(
            spec,
            ValidatorConfig.builder().build(),
            InteropConfig.builder().specProvider(spec).build(),
            () -> null,
            slashingProtector,
            slashingProtectionLogger,
            mock(PublicKeyLoader.class),
            asyncRunner,
            new StubMetricsSystem(),
            Optional.of(dataDirLayout),
            (publicKey) -> Optional.empty());
    validatorLoader.loadValidators();
    keyManager = new OwnedKeyManager(validatorLoader, mock(ValidatorTimingChannel.class));
  }

  @ParameterizedTest(name = "concurrentProtector={0}")
  @ValueSource(booleans = {false, true})
  void shouldUseHistoryImportedWhenKeyIsReimportedAfterDelete(
      final boolean concurrentProtector, @TempDir final Path tempDir) throws Exception {
    setup(tempDir, concurrentProtector);
    assertThat(importKey(Optional.empty()).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertAttestationSigned(0, 5);

    assertThat(keyManager.deleteValidators(List.of(PUBLIC_KEY), slashingProtectionPath).getData())
        .extracting(DeleteKeyResult::getStatus)
        .containsExactly(DeletionStatus.DELETED);

    // the key signed elsewhere while it was deleted, and comes back with that history
    assertThat(importKey(Optional.of(interchange(5000, 905, 906))).getImportStatus())
        .isEqualTo(ImportStatus.IMPORTED);

    assertAttestationRefused(0, 9);
    assertThat(readRecordFile())
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.of(genesisValidatorsRoot),
                UInt64.valueOf(5000),
                UInt64.valueOf(905),
                UInt64.valueOf(906)));
  }

  @ParameterizedTest(name = "concurrentProtector={0}")
  @ValueSource(booleans = {false, true})
  void shouldUseHistoryImportedAfterDeleteWhenProtectionSummaryRunsInBetween(
      final boolean concurrentProtector, @TempDir final Path tempDir) throws Exception {
    setup(tempDir, concurrentProtector);
    assertThat(importKey(Optional.empty()).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertAttestationSigned(0, 5);
    // the summary captures the loaded validators and runs later, on the first slot
    slashingProtectionLogger.protectionSummary(
        validatorLoader.getOwnedValidators().getValidators());
    slashingProtectionLogger.onSlot(UInt64.valueOf(48));

    assertThat(keyManager.deleteValidators(List.of(PUBLIC_KEY), slashingProtectionPath).getData())
        .extracting(DeleteKeyResult::getStatus)
        .containsExactly(DeletionStatus.DELETED);
    assertThat(loggerAsyncRunner.hasDelayedActions()).isTrue();
    loggerAsyncRunner.executeQueuedActions();

    assertThat(importKey(Optional.of(interchange(5000, 905, 906))).getImportStatus())
        .isEqualTo(ImportStatus.IMPORTED);

    assertAttestationRefused(0, 9);
    assertThat(readRecordFile())
        .isEqualTo(
            new ValidatorSigningRecord(
                Optional.of(genesisValidatorsRoot),
                UInt64.valueOf(5000),
                UInt64.valueOf(905),
                UInt64.valueOf(906)));
  }

  @ParameterizedTest(name = "concurrentProtector={0}")
  @ValueSource(booleans = {false, true})
  void shouldNotApplyHistoryImportedWithKeyThatIsAlreadyLoaded(
      final boolean concurrentProtector, @TempDir final Path tempDir) throws Exception {
    setup(tempDir, concurrentProtector);
    assertThat(importKey(Optional.empty()).getImportStatus()).isEqualTo(ImportStatus.IMPORTED);
    assertAttestationSigned(0, 5);
    final ValidatorSigningRecord recordBeforeImport = readRecordFile();

    assertThat(importKey(Optional.of(interchange(5000, 905, 906))).getImportStatus())
        .isEqualTo(ImportStatus.DUPLICATE);

    assertThat(readRecordFile()).isEqualTo(recordBeforeImport);
    assertAttestationSigned(5, 6);
  }

  private PostKeyResult importKey(final Optional<String> slashingProtection) throws IOException {
    final Optional<SlashingProtectionImporter> importer;
    if (slashingProtection.isPresent()) {
      final SlashingProtectionImporter slashingProtectionImporter =
          new SlashingProtectionImporter(slashingProtectionPath);
      assertThat(
              slashingProtectionImporter.initialise(
                  new ByteArrayInputStream(
                      slashingProtection.get().getBytes(StandardCharsets.UTF_8))))
          .isEmpty();
      importer = Optional.of(slashingProtectionImporter);
    } else {
      importer = Optional.empty();
    }
    final List<PostKeyResult> results =
        keyManager.importValidators(
            List.of(keystore),
            List.of(PASSWORD),
            importer,
            Optional.empty(),
            mock(SlashingRiskAction.class));
    assertThat(results).hasSize(1);
    return results.getFirst();
  }

  private void assertAttestationSigned(final int sourceEpoch, final int targetEpoch) {
    final SafeFuture<?> result = signAttestation(sourceEpoch, targetEpoch);
    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompleted();
  }

  private void assertAttestationRefused(final int sourceEpoch, final int targetEpoch) {
    assertThatSafeFuture(signAttestation(sourceEpoch, targetEpoch))
        .isCompletedExceptionallyWith(SlashableConditionException.class);
  }

  private SafeFuture<?> signAttestation(final int sourceEpoch, final int targetEpoch) {
    final Validator validator =
        validatorLoader.getOwnedValidators().getValidator(PUBLIC_KEY).orElseThrow();
    final AttestationData attestationData =
        new AttestationData(
            UInt64.valueOf(targetEpoch * 8L),
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            new Checkpoint(UInt64.valueOf(sourceEpoch), dataStructureUtil.randomBytes32()),
            new Checkpoint(UInt64.valueOf(targetEpoch), dataStructureUtil.randomBytes32()));
    return validator.getSigner().signAttestationData(attestationData, forkInfo);
  }

  private ValidatorSigningRecord readRecordFile() throws IOException {
    return ValidatorSigningRecord.fromBytes(
        Bytes.wrap(
            Files.readAllBytes(
                slashingProtectionPath.resolve(
                    PUBLIC_KEY.toBytesCompressed().toUnprefixedHexString() + ".yml"))));
  }

  private String interchange(final int blockSlot, final int sourceEpoch, final int targetEpoch) {
    return String.format(
        """
        {"metadata":{"interchange_format_version":"5","genesis_validators_root":"%s"},
         "data":[{"pubkey":"%s",
                  "signed_blocks":[{"slot":"%d"}],
                  "signed_attestations":[{"source_epoch":"%d","target_epoch":"%d"}]}]}
        """,
        genesisValidatorsRoot, PUBLIC_KEY, blockSlot, sourceEpoch, targetEpoch);
  }

  private static String loadKeystore() {
    try {
      return Resources.toString(
          Resources.getResource("pbkdf2TestVector.json"), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
