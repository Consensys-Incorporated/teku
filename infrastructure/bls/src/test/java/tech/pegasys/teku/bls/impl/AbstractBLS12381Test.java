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

package tech.pegasys.teku.bls.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BatchSemiAggregate;

public abstract class AbstractBLS12381Test {

  private final List<KeyPair> keys =
      IntStream.range(0, 8).mapToObj(getBls()::generateKeyPair).toList();
  private final List<List<PublicKey>> pubKeys =
      keys.stream()
          .map(k -> Collections.singletonList(k.getPublicKey()))
          .collect(Collectors.toList());
  private final List<Bytes> messages =
      IntStream.range(0, 8)
          .mapToObj(i -> Bytes.wrap(("Hey " + i).getBytes(UTF_8)))
          .collect(Collectors.toList());
  private final List<Signature> signatures =
      Streams.zip(keys.stream(), messages.stream(), (k, m) -> k.getSecretKey().sign(m))
          .collect(Collectors.toList());
  private final Bytes aggrSigMsg = messages.get(0);
  private final Signature aggrSig =
      getBls()
          .aggregateSignatures(
              keys.stream()
                  .limit(3)
                  .map(k -> k.getSecretKey().sign(aggrSigMsg))
                  .collect(Collectors.toList()));
  private final List<PublicKey> aggrSigPubkeys =
      keys.stream().limit(3).map(KeyPair::getPublicKey).collect(Collectors.toList());
  private final int aggrIndex;
  private final int invalidIndex;

  protected AbstractBLS12381Test() {
    aggrIndex = pubKeys.size();
    pubKeys.add(aggrSigPubkeys);
    messages.add(aggrSigMsg);
    signatures.add(aggrSig);

    invalidIndex = pubKeys.size();
    pubKeys.add(pubKeys.get(0));
    messages.add(messages.get(0));
    signatures.add(getBls().randomSignature(0));
  }

  protected abstract BLS12381 getBls();

  @Test
  void signAndVerify() {
    KeyPair keyPair = getBls().generateKeyPair(42);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = keyPair.getSecretKey().sign(message);
    assertTrue(keyPair.getPublicKey().verifySignature(signature, message));
  }

  @Test
  void signAndVerifyDifferentMessage() {
    KeyPair keyPair = getBls().generateKeyPair(117);
    Bytes message1 = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Bytes message2 = Bytes.wrap("Hello, world?".getBytes(UTF_8));
    Signature signature = keyPair.getSecretKey().sign(message1);
    assertFalse(keyPair.getPublicKey().verifySignature(signature, message2));
  }

  @Test
  void signAndVerifyDifferentKeys() {
    KeyPair keyPair1 = getBls().generateKeyPair(129);
    KeyPair keyPair2 = getBls().generateKeyPair(257);
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    Signature signature = keyPair1.getSecretKey().sign(message);
    assertFalse(keyPair2.getPublicKey().verifySignature(signature, message));
  }

  @Test
  void fastAggregateVerify() {
    Bytes message = Bytes.wrap("Hello, world!".getBytes(UTF_8));
    KeyPair keyPair1 = getBls().generateKeyPair(1);
    KeyPair keyPair2 = getBls().generateKeyPair(2);
    KeyPair keyPair3 = getBls().generateKeyPair(3);

    List<PublicKey> publicKeys =
        Arrays.asList(keyPair1.getPublicKey(), keyPair2.getPublicKey(), keyPair3.getPublicKey());
    List<Signature> signatures =
        Arrays.asList(
            keyPair1.getSecretKey().sign(message),
            keyPair2.getSecretKey().sign(message),
            keyPair3.getSecretKey().sign(message));
    Signature aggregatedSignature = getBls().aggregateSignatures(signatures);

    assertTrue(aggregatedSignature.verify(publicKeys, message));
  }

  @Test
  void batchVerifyPositiveSimpleTest() {
    // positive simple case
    for (int sigCnt = 0; sigCnt < 6; sigCnt++) {
      List<BatchSemiAggregate> semiAggr =
          IntStream.range(0, sigCnt)
              .mapToObj(
                  i ->
                      getBls()
                          .prepareBatchVerify(
                              i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(getBls().completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerifyPositiveSimpleWithAggrSigTest() {
    // positive simple case with aggr sig
    for (int aggrPos = 0; aggrPos < 4; aggrPos++) {
      int finalAggrPos = aggrPos;
      List<BatchSemiAggregate> semiAggr =
          IntStream.range(0, 4)
              .map(i -> i == finalAggrPos ? aggrIndex : i)
              .mapToObj(
                  i ->
                      getBls()
                          .prepareBatchVerify(
                              i, pubKeys.get(i), messages.get(i), signatures.get(i)))
              .collect(Collectors.toList());
      assertTrue(getBls().completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerify2PositiveTest() {
    // positive prepareBatchVerify2 test
    for (int sigCnt = 0; sigCnt < 8; sigCnt++) {
      Stream<List<Integer>> pairsStream =
          Lists.partition(IntStream.range(0, sigCnt).boxed().collect(Collectors.toList()), 2)
              .stream();
      List<BatchSemiAggregate> semiAggr =
          pairsStream
              .map(
                  l -> {
                    if (l.size() == 1) {
                      return getBls()
                          .prepareBatchVerify(
                              l.get(0),
                              pubKeys.get(l.get(0)),
                              messages.get(l.get(0)),
                              signatures.get(l.get(0)));
                    } else {
                      return getBls()
                          .prepareBatchVerify2(
                              l.get(0),
                              pubKeys.get(l.get(0)),
                              messages.get(l.get(0)),
                              signatures.get(l.get(0)),
                              pubKeys.get(l.get(1)),
                              messages.get(l.get(1)),
                              signatures.get(l.get(1)));
                    }
                  })
              .collect(Collectors.toList());

      assertTrue(getBls().completeBatchVerify(semiAggr));
    }
  }

  @Test
  void batchVerify2NegativeTest() {
    // negative prepareBatchVerify2 test
    for (int sigCnt = 0; sigCnt < 5; sigCnt++) {
      int finalSigCnt = sigCnt;
      Stream<List<Integer>> pairsStream =
          Lists.partition(
              IntStream.range(0, 5)
                  .map(i -> i == finalSigCnt ? invalidIndex : i)
                  .boxed()
                  .collect(Collectors.toList()),
              2)
              .stream();
      List<BatchSemiAggregate> semiAggr =
          pairsStream
              .map(
                  l -> {
                    if (l.size() == 1) {
                      return getBls()
                          .prepareBatchVerify(
                              l.get(0),
                              pubKeys.get(l.get(0)),
                              messages.get(l.get(0)),
                              signatures.get(l.get(0)));
                    } else {
                      return getBls()
                          .prepareBatchVerify2(
                              l.get(0),
                              pubKeys.get(l.get(0)),
                              messages.get(l.get(0)),
                              signatures.get(l.get(0)),
                              pubKeys.get(l.get(1)),
                              messages.get(l.get(1)),
                              signatures.get(l.get(1)));
                    }
                  })
              .collect(Collectors.toList());

      assertFalse(getBls().completeBatchVerify(semiAggr));
    }
  }
}
