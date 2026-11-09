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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.ThrottlingTaskQueueWithPriority;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.generator.signatures.NoOpLocalSigner;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.GraffitiProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.signer.ExternalSigner;

public class SlashingProtectionLoggerTest {
  private static final Duration TIMEOUT = Duration.ofMillis(500);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private StubAsyncRunner asyncRunner;
  private ValidatorLogger validatorLogger;
  private SlashingProtectionLogger slashingProtectionLogger;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  @BeforeEach
  public void beforeEach() throws Exception {
    when(slashingProtector.getSigningRecord(any())).thenReturn(Optional.empty());
    this.asyncRunner = new StubAsyncRunner();
    this.validatorLogger = mock(ValidatorLogger.class);
    this.slashingProtectionLogger =
        new SlashingProtectionLogger(slashingProtector, spec, asyncRunner, validatorLogger);
    this.slashingProtectionLogger.onSlot(UInt64.ONE);
  }

  @Test
  public void shouldLogNothingOnEmptyList() {
    slashingProtectionLogger.protectionSummary(new ArrayList<>());
    asyncRunner.executeQueuedActions();
    verify(validatorLogger, never()).loadedSlashingProtection(any());
    verify(validatorLogger, never()).notLoadedSlashingProtection(any());
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogNotLoadedProtectionValidator() {
    Validator unProtectedValidator = createUnProtectedValidator(publicKey);
    List<Validator> unProtectedValidators = new ArrayList<>();
    unProtectedValidators.add(unProtectedValidator);
    slashingProtectionLogger.protectionSummary(unProtectedValidators);
    asyncRunner.executeQueuedActions();
    verify(validatorLogger, never()).loadedSlashingProtection(any());
    Set<String> unProtectedValidatorKeys = new HashSet<>();
    unProtectedValidatorKeys.add(unProtectedValidator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).notLoadedSlashingProtection(unProtectedValidatorKeys);
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogLoadedProtectionValidator() throws Exception {
    Validator validator = createProtectedValidator(publicKey);
    List<Validator> protectedValidators = new ArrayList<>();
    protectedValidators.add(validator);
    when(slashingProtector.getSigningRecord(validator.getPublicKey()))
        .thenReturn(
            Optional.of(
                new ValidatorSigningRecord(Optional.of(Bytes32.ZERO), UInt64.ZERO, null, null)));
    slashingProtectionLogger.protectionSummary(protectedValidators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys = new HashSet<>();
    protectedValidatorKeys.add(validator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).loadedSlashingProtection(protectedValidatorKeys);
    verify(validatorLogger, never()).notLoadedSlashingProtection(any());
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  @Test
  public void shouldLogLoadedButOutdatedProtectionValidator() throws Exception {
    Validator validator = createProtectedValidator(publicKey);
    List<Validator> protectedValidators = new ArrayList<>();
    protectedValidators.add(validator);
    when(slashingProtector.getSigningRecord(validator.getPublicKey()))
        .thenReturn(
            Optional.of(
                new ValidatorSigningRecord(Optional.of(Bytes32.ZERO), UInt64.ZERO, null, null)));
    slashingProtectionLogger.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(1000)));
    slashingProtectionLogger.protectionSummary(protectedValidators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys = new HashSet<>();
    protectedValidatorKeys.add(validator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).loadedSlashingProtection(protectedValidatorKeys);
    verify(validatorLogger, never()).notLoadedSlashingProtection(any());
    verify(validatorLogger, times(1)).outdatedSlashingProtection(eq(protectedValidatorKeys), any());
  }

  @Test
  public void shouldNotLogObsoleteProtectionForValidatorWithNoBlockRecord() throws Exception {
    Validator validator = createProtectedValidator(publicKey);
    when(slashingProtector.getSigningRecord(validator.getPublicKey()))
        .thenReturn(
            Optional.of(
                new ValidatorSigningRecord(
                    Optional.of(Bytes32.ZERO),
                    UInt64.ZERO,
                    UInt64.valueOf(900),
                    UInt64.valueOf(950))));

    List<Validator> validators = List.of(validator);
    slashingProtectionLogger.onSlot(spec.computeStartSlotAtEpoch(UInt64.valueOf(1000)));
    slashingProtectionLogger.protectionSummary(validators);
    asyncRunner.executeQueuedActions();
    Set<String> protectedValidatorKeys = Set.of(validator.getPublicKey().toAbbreviatedString());
    verify(validatorLogger, times(1)).loadedSlashingProtection(protectedValidatorKeys);
    // We don't expect to produce blocks regularly so having old/no block signing record doesn't
    // make the validator outdated, only when the attestation record is old.
    verify(validatorLogger, never()).outdatedSlashingProtection(any(), any());
  }

  private Validator createProtectedValidator(final BLSPublicKey publicKey) {
    Signer localSigner = new NoOpLocalSigner();
    Signer signer = new SlashingProtectedSigner(publicKey, slashingProtector, localSigner);
    return new Validator(publicKey, signer, mock(GraffitiProvider.class));
  }

  private Validator createUnProtectedValidator(final BLSPublicKey publicKey) {
    ExternalSigner externalSigner;
    try {
      externalSigner =
          new ExternalSigner(
              spec,
              mock(HttpClient.class),
              URI.create("http://127.0.0.1/").toURL(),
              publicKey,
              TIMEOUT,
              mock(ThrottlingTaskQueueWithPriority.class),
              metricsSystem);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    return new Validator(publicKey, externalSigner, mock(GraffitiProvider.class));
  }
}
