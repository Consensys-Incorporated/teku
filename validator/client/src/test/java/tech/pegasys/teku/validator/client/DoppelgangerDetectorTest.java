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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;

public class DoppelgangerDetectorTest {
  private final StatusLogger statusLog = mock(StatusLogger.class);
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(10);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final GenesisDataProvider genesisDataProvider = mock(GenesisDataProvider.class);
  private LogCaptor logCaptor;
  private final String doppelgangerDetectedLog = "Validator doppelganger detected...";

  private final Duration checkDelay = Duration.ofSeconds(2);
  private final int maxEpochs = 2;
  private final BLSPublicKey pubKey1 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubKey2 = dataStructureUtil.randomPublicKey();
  private final BLSPublicKey pubKey3 = dataStructureUtil.randomPublicKey();
  private DoppelgangerDetector doppelgangerDetector;

  @BeforeEach
  public void setup() {
    logCaptor = LogCaptor.forClass(DoppelgangerDetector.class);
    when(genesisDataProvider.getGenesisTime()).thenReturn(SafeFuture.completedFuture(UInt64.ZERO));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey1)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(pubKey1, 1))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey2)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(pubKey2, 2))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey3)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(pubKey3, 3))));

    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey1, pubKey2, pubKey3)))
        .thenReturn(
            SafeFuture.completedFuture(Optional.of(Map.of(pubKey1, 1, pubKey2, 2, pubKey3, 3))));

    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            maxEpochs);
  }

  @AfterEach
  public void tearDown() {
    logCaptor.close();
  }

  @Test
  public void shouldNotDetectDoppelganger() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false)))));

    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(90);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, pubKeysStrings);
    verify(statusLog).doppelgangerDetectionEnd(pubKeysStrings, new HashMap<>());
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldDetectDoppelgangers() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), true)))));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(200);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldNotDetectDoppelgangerSeparately() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false)))));

    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey2));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(150);
    asyncRunner.executeUntilDone();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey2.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, Set.of(pubKey2.toAbbreviatedString()));
    verify(statusLog)
        .doppelgangerDetectionEnd(Set.of(pubKey1.toAbbreviatedString()), new HashMap<>());
    verify(statusLog)
        .doppelgangerDetectionEnd(Set.of(pubKey2.toAbbreviatedString()), new HashMap<>());
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
    assertThat(secondDoppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldDetectedDoppelgangersSeparately() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), true)))));
    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey3));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey3.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, Set.of(pubKey3.toAbbreviatedString()));
    logCaptor.assertMessagesInOrder(Level.FATAL, doppelgangerDetectedLog, doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> firstlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            firstlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(firstlyDetectedDoppelgangers);
    Map<UInt64, BLSPublicKey> secondlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            secondlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(secondDoppelgangerDetectorFuture)
        .isCompletedWithValue(secondlyDetectedDoppelgangers);
  }

  @Test
  public void shouldDetectDoppelgangerAndKeepCheckingUnavailableValidatorSeparately() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), true)))));

    final Exception validatorIndicesException = new Exception("Validator Indices Exception");
    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey3)))
        .thenReturn(SafeFuture.failedFuture(validatorIndicesException));

    SafeFuture<Map<UInt64, BLSPublicKey>> firstDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));
    SafeFuture<Map<UInt64, BLSPublicKey>> secondDoppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey3));
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(50);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(50));
    asyncRunner.executeQueuedActions();
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey1.toAbbreviatedString()));
    verify(statusLog).doppelgangerDetectionStart(Set.of(pubKey3.toAbbreviatedString()));
    logCaptor.assertMessagesInOrder(Level.FATAL, doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> firstlyDetectedDoppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            firstlyDetectedDoppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(firstDoppelgangerDetectorFuture).isCompletedWithValue(firstlyDetectedDoppelgangers);

    verify(statusLog)
        .doppelgangerDetectionUnavailable(eq(Set.of(pubKey3.toAbbreviatedString())), anyString());
    assertThat(secondDoppelgangerDetectorFuture).isNotDone();
  }

  @Test
  public void shouldKeepCheckingWhenGenesisDataIsUnavailablePastFormerTimeout() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.failedFuture(new Exception("Genesis Time Exception")));
    Set<BLSPublicKey> pubKeys =
        Set.of(dataStructureUtil.randomPublicKey(), dataStructureUtil.randomPublicKey());
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    verify(statusLog)
        .doppelgangerDetectionStart(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    verify(statusLog)
        .doppelgangerDetectionUnavailable(
            eq(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet())), anyString());
    assertThat(doppelgangerDetectorFuture).isNotDone();
  }

  @Test
  public void shouldCheckCurrentEpochBeforeCompletingAfterBeaconNodeRecovery() {
    final AtomicBoolean beaconNodeAvailable = new AtomicBoolean(false);
    when(genesisDataProvider.getGenesisTime())
        .thenAnswer(
            __ ->
                beaconNodeAvailable.get()
                    ? SafeFuture.completedFuture(UInt64.ZERO)
                    : SafeFuture.failedFuture(new Exception("Genesis Time Exception")));
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(List.of(new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false)))))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(List.of(new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true)))));
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1);
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(
        (long) spec.getGenesisSpecConfig().getSlotsPerEpoch()
            * spec.getGenesisSpecConfig().getSecondsPerSlot()
            * maxEpochs);
    beaconNodeAvailable.set(true);
    asyncRunner.executeQueuedActions();

    final UInt64 currentEpoch =
        spec.computeEpochAtSlot(spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO));
    verify(validatorApiChannel)
        .getValidatorsLiveness(List.of(UInt64.ONE), currentEpoch.minus(UInt64.ONE));
    verify(validatorApiChannel).getValidatorsLiveness(List.of(UInt64.ONE), currentEpoch);
    assertThat(detection).isCompletedWithValue(Map.of(UInt64.ONE, pubKey1));
  }

  @Test
  public void shouldKeepCheckingWhenValidatorIndicesAreUnavailablePastFormerTimeout() {
    when(validatorApiChannel.getValidatorIndices(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, pubKeysStrings);
    verify(statusLog).doppelgangerDetectionUnavailable(eq(pubKeysStrings), anyString());
    assertThat(doppelgangerDetectorFuture).isNotDone();
  }

  @Test
  public void shouldKeepCheckingWhenValidatorIndicesRequestTimesOut() {
    when(validatorApiChannel.getValidatorIndices(any())).thenReturn(new SafeFuture<>());
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(4);
    asyncRunner.executeQueuedActions();

    verify(statusLog)
        .doppelgangerDetectionUnavailable(
            toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()),
            "Unable to get validator indices: Request timeout");
    assertThat(detection).isNotDone();
  }

  @Test
  public void shouldKeepCheckingWhenValidatorsLivenessIsUnavailablePastFormerTimeout() {
    final Exception validatorLivenessException = new Exception("Validator Liveness Exception");
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.failedFuture(validatorLivenessException));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, pubKeysStrings);
    verify(statusLog).doppelgangerDetectionUnavailable(eq(pubKeysStrings), anyString());
    assertThat(doppelgangerDetectorFuture).isNotDone();
  }

  @Test
  public void shouldKeepCheckingWhenValidatorsLivenessResponseIsIncompletePastFormerTimeout() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(new ArrayList<>())));
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    final SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBy(Duration.ofMinutes(25));
    asyncRunner.executeQueuedActions();

    assertThat(doppelgangerDetectorFuture).isNotDone();
  }

  @Test
  public void shouldResumeSameCycleWhenValidatorsLivenessBecomesAvailableAgain() {
    final AtomicBoolean beaconNodeAvailable = new AtomicBoolean(true);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenAnswer(
            __ ->
                beaconNodeAvailable.get()
                    ? SafeFuture.completedFuture(
                        Optional.of(
                            List.of(
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false))))
                    : SafeFuture.failedFuture(new Exception("Validator Liveness Exception")));
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    final SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    beaconNodeAvailable.set(false);
    timeProvider.advanceTimeBy(Duration.ofMinutes(5));
    asyncRunner.executeQueuedActions();
    assertThat(doppelgangerDetectorFuture).isNotDone();

    beaconNodeAvailable.set(true);
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();
    verify(statusLog)
        .doppelgangerDetectionResumed(toAbbreviatedKeys(pubKeys).collect(Collectors.toSet()));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldContinueSameDetectionCycleAcrossRepeatedBeaconNodeOutages() {
    final AtomicBoolean beaconNodeAvailable = new AtomicBoolean(true);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenAnswer(
            __ ->
                beaconNodeAvailable.get()
                    ? SafeFuture.completedFuture(
                        Optional.of(
                            List.of(
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                                new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false))))
                    : SafeFuture.failedFuture(new Exception("Validator Liveness Exception")));
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    final Set<String> abbreviatedPubKeys = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    beaconNodeAvailable.set(false);
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();

    beaconNodeAvailable.set(true);
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();

    beaconNodeAvailable.set(false);
    timeProvider.advanceTimeBySeconds(2);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(20);
    asyncRunner.executeQueuedActions();

    verify(statusLog).doppelgangerDetectionStart(abbreviatedPubKeys);
    verify(statusLog, times(2))
        .doppelgangerDetectionUnavailable(eq(abbreviatedPubKeys), anyString());
    verify(statusLog).doppelgangerDetectionResumed(abbreviatedPubKeys);
    assertThat(detection).isNotDone();
  }

  @Test
  public void shouldReportLivenessTimeoutAfterSlowGenesisResponse() {
    final Duration requestTimeout = Duration.ofMillis(500);
    final SafeFuture<UInt64> pendingGenesisTime = new SafeFuture<>();
    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> pendingLiveness = new SafeFuture<>();
    when(genesisDataProvider.getGenesisTime()).thenReturn(pendingGenesisTime);
    when(validatorApiChannel.getValidatorsLiveness(any(), any())).thenReturn(pendingLiveness);
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            requestTimeout,
            maxEpochs);
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1);
    final Set<String> abbreviatedPubKeys = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeByMillis(100);
    pendingGenesisTime.complete(UInt64.ZERO);
    timeProvider.advanceTimeBy(requestTimeout);
    asyncRunner.executeDueActionsRepeatedly();

    verify(statusLog)
        .doppelgangerDetectionUnavailable(
            abbreviatedPubKeys, "Unable to get validator liveness: Request timeout");
    assertThat(detection).isNotDone();
  }

  @Test
  public void shouldNotCompleteBasedOnlyOnElapsedTime() {
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false)))));
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            Integer.MAX_VALUE);
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(4);
    asyncRunner.executeQueuedActions();

    assertThat(detection).isNotDone();
  }

  @Test
  public void shouldCompleteWhenValidatorIndicesResponseIsEmpty() {
    final Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    when(validatorApiChannel.getValidatorIndices(pubKeys))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of())));
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            0);
    final SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);

    asyncRunner.executeQueuedActions();

    final Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionEnd(pubKeysStrings, Map.of());
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.of());
  }

  @Test
  public void shouldSkipKeysWithoutIndex() {
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    when(validatorApiChannel.getValidatorIndices(pubKeys))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(pubKey1, 1, pubKey2, 2))));
    when(validatorApiChannel.getValidatorIndices(Set.of(pubKey2, pubKey3)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(Map.of(pubKey2, 2))));
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false)))));
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    timeProvider.advanceTimeBySeconds(120);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog).doppelgangerCheck(UInt64.ZERO, pubKeysStrings);
    final String noIndex =
        String.format(
            "Skipping doppelganger check for public keys %s. No associated indices found. Public keys are inactive",
            pubKey3.toAbbreviatedString());
    logCaptor.assertInfoLog(noIndex);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers = Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldNotStartDoppelgangerDetectionWhenEmptyKeySet() {
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(new HashSet<>());
    logCaptor.assertInfoLog("Skipping doppelganger detection: No public keys provided to check");
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(Map.ofEntries());
  }

  @Test
  public void shouldNotCheckPreviousEpochAtFirstRunAtEpochZero() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(timeProvider.getTimeInSeconds()));
    UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO);
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            0);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), true)))));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog, times(1)).doppelgangerCheck(currentEpoch, pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldCheckPreviousAndCurrentEpochAtFirstRun() {
    when(genesisDataProvider.getGenesisTime())
        .thenReturn(SafeFuture.completedFuture(UInt64.valueOf(0)));
    timeProvider.advanceTimeBy(Duration.of(20, ChronoUnit.MINUTES));
    UInt64 currentSlot = spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO);
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    doppelgangerDetector =
        new DoppelgangerDetector(
            statusLog,
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            checkDelay,
            0);
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false)))));
    when(validatorApiChannel.getValidatorsLiveness(any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), false)))))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    List.of(
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(1), true),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(2), false),
                        new ValidatorLivenessAtEpoch(UInt64.valueOf(3), true)))));
    Set<BLSPublicKey> pubKeys = Set.of(pubKey1, pubKey2, pubKey3);
    SafeFuture<Map<UInt64, BLSPublicKey>> doppelgangerDetectorFuture =
        doppelgangerDetector.performDoppelgangerDetection(pubKeys);
    asyncRunner.executeQueuedActions();
    Set<String> pubKeysStrings = toAbbreviatedKeys(pubKeys).collect(Collectors.toSet());
    verify(statusLog).doppelgangerDetectionStart(pubKeysStrings);
    verify(statusLog, times(1)).doppelgangerCheck(currentEpoch.minus(1), pubKeysStrings);
    verify(statusLog, times(1)).doppelgangerCheck(currentEpoch, pubKeysStrings);
    logCaptor.assertFatalLog(doppelgangerDetectedLog);
    Map<UInt64, BLSPublicKey> doppelgangers =
        Map.ofEntries(Map.entry(UInt64.valueOf(1), pubKey1), Map.entry(UInt64.valueOf(3), pubKey3));
    verify(statusLog)
        .validatorsDoppelgangersDetected(
            doppelgangers.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
    assertThat(doppelgangerDetectorFuture).isCompletedWithValue(doppelgangers);
  }

  @Test
  public void shouldCheckCurrentEpochOnNextAttemptAfterInitialPreviousEpochCheckFails() {
    timeProvider.advanceTimeBy(Duration.of(20, ChronoUnit.MINUTES));
    final UInt64 currentEpoch =
        spec.computeEpochAtSlot(spec.getCurrentSlot(timeProvider.getTimeInSeconds(), UInt64.ZERO));
    when(validatorApiChannel.getValidatorsLiveness(any(), eq(currentEpoch.minus(UInt64.ONE))))
        .thenReturn(SafeFuture.failedFuture(new Exception("Previous epoch unavailable")));
    when(validatorApiChannel.getValidatorsLiveness(any(), eq(currentEpoch)))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(List.of(new ValidatorLivenessAtEpoch(UInt64.ONE, false)))));
    final SafeFuture<Map<UInt64, BLSPublicKey>> detection =
        doppelgangerDetector.performDoppelgangerDetection(Set.of(pubKey1));

    asyncRunner.executeQueuedActions();
    assertThat(detection).isNotDone();

    timeProvider.advanceTimeBy(checkDelay);
    asyncRunner.executeQueuedActions();

    verify(validatorApiChannel)
        .getValidatorsLiveness(List.of(UInt64.ONE), currentEpoch.minus(UInt64.ONE));
    verify(validatorApiChannel).getValidatorsLiveness(List.of(UInt64.ONE), currentEpoch);
    assertThat(detection).isNotDone();
  }

  private Stream<String> toAbbreviatedKeys(final Set<BLSPublicKey> pubKeys) {
    return pubKeys.stream().map(BLSPublicKey::toAbbreviatedString);
  }
}
