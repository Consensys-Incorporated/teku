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

package tech.pegasys.teku.bls;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.logging.LoggingConfigurator;

// This test is disabled by default so that it doesn't slow down other tests
@Disabled
public class BLSPerformanceRunner {
  private static final Logger LOG = LogManager.getLogger();

  public BLSPerformanceRunner() {
    LoggingConfigurator.setAllLevels(Level.INFO);
  }

  private Long executeRun(final Runnable r, final Integer count) {
    long start = System.currentTimeMillis();
    for (int j = 0; j < count; j++) {
      Thread t = new Thread(r);
      t.start();
    }
    long end = System.currentTimeMillis();
    return end - start;
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void benchmarkVerifyAggregate128(final Integer i) {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));

    final List<BLSKeyPair> keyPairs = BLSKeyGenerator.generateKeyPairs(128);

    Long time =
        executeRun(
            () -> {
              try {
                List<BLSSignature> sigs =
                    keyPairs.stream()
                        .map(pk -> BLS.sign(pk.getSecretKey(), message))
                        .collect(Collectors.toList());

                List<BLSPublicKey> publicKeys =
                    keyPairs.stream().map(BLSKeyPair::getPublicKey).collect(Collectors.toList());
                BLSSignature aggregateSignature = BLS.aggregate(sigs);

                // Verify the aggregate signatures and keys
                BLS.fastAggregateVerify(publicKeys, message, aggregateSignature);
              } catch (RuntimeException e) {
                LOG.error("Failed", e);
              }
            },
            i);
    LOG.info("Time for 128: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void generateRandomSignature(final Integer i) {
    Long time = executeRun(BLSTestUtil::randomSignature, i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCount")
  void testAggregateManySignatures(final Integer i) {
    final BLSSignature signature = BLSTestUtil.randomSignature();
    final List<BLSSignature> sigs = Collections.nCopies(i, signature);

    Long time =
        executeRun(
            () -> {
              try {
                BLS.aggregate(sigs);
              } catch (RuntimeException e) {
                LOG.error("Aggregation failed", e);
              }
            },
            1);

    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void testSigning(final Integer i) {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));

    BLSKeyPair keyPair1 = BLSTestUtil.randomKeyPair(1);

    Long time = executeRun(() -> BLS.sign(keyPair1.getSecretKey(), message), i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCount")
  void testAggregationTime(final Integer i) {
    BLSSignature signature = BLSTestUtil.randomSignature();

    Long time =
        executeRun(
            () -> {
              try {
                BLS.aggregate(Collections.singletonList(signature));
              } catch (RuntimeException e) {
                LOG.error("Aggregation failed", e);
              }
            },
            i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void testBLSPubKeyDeserialize(final Integer i) {
    Bytes emptyBytesSsz = SSZ.encode(writer -> writer.writeFixedBytes(Bytes.wrap(new byte[48])));

    Long time = executeRun(() -> BLSPublicKey.fromSSZBytes(emptyBytesSsz), i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void testBLSPubKeySerialize(final Integer i) {
    BLSPublicKey emptyPublicKey = BLSPublicKey.empty();

    Long time = executeRun(() -> emptyPublicKey.toSSZBytes().toHexString(), i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void testSignatureSerialize(final Integer i) {
    BLSSignature signature1 = BLSTestUtil.randomSignature();

    Long time = executeRun(signature1::toSSZBytes, i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  @ParameterizedTest
  @MethodSource("singleAggregationCountOrder4")
  void testSignatureDeserialize(final Integer i) {
    BLSSignature signature1 = BLSTestUtil.randomSignature();
    Bytes bytes = signature1.toSSZBytes();

    Long time = executeRun(() -> BLSSignature.fromSSZBytes(bytes), i);
    LOG.info("Time for i: {}, time: {}", i, time);
  }

  public static Stream<Arguments> singleAggregationCount() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(10));
    args.add(Arguments.of(100));
    args.add(Arguments.of(1000));
    args.add(Arguments.of(10000));
    //    args.add(Arguments.of(100000));
    //    args.add(Arguments.of(1000000));
    return args.stream();
  }

  public static Stream<Arguments> singleAggregationCountOrder4() {
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(10));
    args.add(Arguments.of(100));
    args.add(Arguments.of(1000));
    //    args.add(Arguments.of(2000));
    return args.stream();
  }
}
