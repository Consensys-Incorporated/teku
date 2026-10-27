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

package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import supranational.blst.P2;
import tech.pegasys.teku.bls.BatchSemiAggregate;
import tech.pegasys.teku.bls.impl.AbstractBLS12381Test;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.BlsException;

public class BlstTest extends AbstractBLS12381Test {
  private static final Random RANDOM = new Random(1);

  private static BLS12381 bls;

  static BlstSignature notInG2() {
    // A point on the curve but not in the G2 group
    return BlstSignature.fromBytes(
        Bytes.fromHexString(
            "0x"
                + "8000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000004"));
  }

  @BeforeAll
  static void setup() {
    bls = BlstLoader.INSTANCE.orElseThrow();
  }

  @Override
  protected BLS12381 getBls() {
    return bls;
  }

  @Test
  void testBatchVerifySingleSig() {
    Bytes msg = Bytes32.ZERO;

    BlstSecretKey blstSK = BlstSecretKey.generateNew(RANDOM);
    BlstPublicKey blstPK = blstSK.derivePublicKey();

    BlstSignature blstSignature = BlstBLS12381.sign(blstSK, msg);

    BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerify(0, List.of(blstPK), msg, blstSignature);

    boolean blstRes = bls.completeBatchVerify(List.of(semiAggregate));
    assertThat(blstRes).isTrue();
  }

  @Test
  void testBatchVerifyCoupleSigs() {
    Bytes msg1 = Bytes32.fromHexString("123456");

    BlstSecretKey blstSK1 = BlstSecretKey.generateNew(RANDOM);
    BlstPublicKey blstPK1 = blstSK1.derivePublicKey();
    BlstSignature blstSignature1 = BlstBLS12381.sign(blstSK1, msg1);

    Bytes msg2 = Bytes32.fromHexString("654321");

    BlstSecretKey blstSK2 = BlstSecretKey.generateNew(RANDOM);
    BlstPublicKey blstPK2 = blstSK2.derivePublicKey();
    BlstSignature blstSignature2 = BlstBLS12381.sign(blstSK2, msg2);

    BatchSemiAggregate semiAggregate1 =
        bls.prepareBatchVerify(0, List.of(blstPK1), msg1, blstSignature1);
    BatchSemiAggregate semiAggregate2 =
        bls.prepareBatchVerify(1, List.of(blstPK2), msg2, blstSignature2);

    boolean blstRes = bls.completeBatchVerify(List.of(semiAggregate1, semiAggregate2));
    assertThat(blstRes).isTrue();
  }

  @Test
  void succeedsWhenPrepareBatchVerifyNotInG2ThrowsException() {
    Bytes msg = Bytes32.fromHexString("123456");
    BlstSecretKey blstSK = BlstSecretKey.generateNew(RANDOM);
    BlstPublicKey blstPK = blstSK.derivePublicKey();
    BlstSignature blstSignature = notInG2();

    assertThrows(
        IllegalArgumentException.class,
        () -> bls.prepareBatchVerify(0, List.of(blstPK), msg, blstSignature));
  }

  @Test
  void batchVerifySameMessageSucceedsForValidSignatures() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(5);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        secretKeys.stream().map(sk -> BlstBLS12381.sign(sk, msg)).toList();

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isTrue();
  }

  @Test
  void batchVerifySameMessageSucceedsWhenParallel() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(64);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        secretKeys.stream().map(sk -> BlstBLS12381.sign(sk, msg)).toList();

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, true);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isTrue();
  }

  @Test
  void batchVerifySameMessageFailsWhenOneSignatureIsForAnotherMessage() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final Bytes otherMsg = Bytes32.fromHexString("fedcba");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(4);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        List.of(
            BlstBLS12381.sign(secretKeys.get(0), msg),
            BlstBLS12381.sign(secretKeys.get(1), msg),
            BlstBLS12381.sign(secretKeys.get(2), otherMsg),
            BlstBLS12381.sign(secretKeys.get(3), msg));

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @Test
  void batchVerifySameMessageFailsWhenSignaturesAreOnlyValidInAggregate() {
    // sig1 + D and sig2 - D sum to a valid aggregate but are individually invalid.
    // Plain aggregation would accept them; randomised batch verification must not.
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(2);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final BlstSignature sig1 = BlstBLS12381.sign(secretKeys.get(0), msg);
    final BlstSignature sig2 = BlstBLS12381.sign(secretKeys.get(1), msg);
    final P2 delta = new P2().hash_to(Bytes32.fromHexString("1234").toArrayUnsafe(), "delta");
    final BlstSignature tampered1 = new BlstSignature(new P2(sig1.ec2Point).add(delta).to_affine());
    final BlstSignature tampered2 =
        new BlstSignature(new P2(sig2.ec2Point).add(delta.neg()).to_affine());

    // Sanity check: the tampered signatures do aggregate to the valid aggregate signature
    assertThat(BlstSignature.aggregate(List.of(tampered1, tampered2)).toBytesCompressed())
        .isEqualTo(BlstSignature.aggregate(List.of(sig1, sig2)).toBytesCompressed());

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, List.of(tampered1, tampered2), false);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @Test
  void batchVerifySameMessageThrowsForSignatureNotInG2() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(2);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        List.of(BlstBLS12381.sign(secretKeys.get(0), msg), notInG2());

    assertThrows(
        BlsException.class,
        () -> bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false));
  }

  @Test
  void batchVerifySameMessageIsInvalidForInfinityPublicKey() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final BlstSecretKey secretKey = BlstSecretKey.generateNew(RANDOM);
    final BlstPublicKey infinityKey = BlstSecretKey.fromBytes(Bytes32.ZERO).derivePublicKey();
    assertThat(infinityKey.isInfinity()).isTrue();
    final BlstSignature signature = BlstBLS12381.sign(secretKey, msg);

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(
            List.of(List.of(secretKey.derivePublicKey()), List.of(infinityKey)),
            msg,
            List.of(signature, signature),
            false);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @Test
  void batchVerifySameMessageSupportsAggregateSignatures() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(6);
    final List<List<BlstPublicKey>> publicKeys =
        List.of(
            List.of(secretKeys.get(0).derivePublicKey(), secretKeys.get(1).derivePublicKey()),
            List.of(secretKeys.get(2).derivePublicKey()),
            List.of(
                secretKeys.get(3).derivePublicKey(),
                secretKeys.get(4).derivePublicKey(),
                secretKeys.get(5).derivePublicKey()));
    final List<BlstSignature> signatures =
        List.of(
            BlstSignature.aggregate(
                List.of(
                    BlstBLS12381.sign(secretKeys.get(0), msg),
                    BlstBLS12381.sign(secretKeys.get(1), msg))),
            BlstBLS12381.sign(secretKeys.get(2), msg),
            BlstSignature.aggregate(
                List.of(
                    BlstBLS12381.sign(secretKeys.get(3), msg),
                    BlstBLS12381.sign(secretKeys.get(4), msg),
                    BlstBLS12381.sign(secretKeys.get(5), msg))));

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isTrue();
  }

  @Test
  void batchVerifySameMessageCanBeCompletedTogetherWithOtherSemiAggregates() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final Bytes otherMsg = Bytes32.fromHexString("fedcba");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(4);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.subList(0, 3).stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        secretKeys.subList(0, 3).stream().map(sk -> BlstBLS12381.sign(sk, msg)).toList();

    // completeBatchVerify merges into the first semi-aggregate, so build one per call
    final BatchSemiAggregate sameMessageAggregate1 =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false);
    final BatchSemiAggregate sameMessageAggregate2 =
        bls.prepareBatchVerifySameMessage(publicKeys, msg, signatures, false);
    final BatchSemiAggregate otherAggregate =
        bls.prepareBatchVerify(
            3,
            List.of(secretKeys.get(3).derivePublicKey()),
            otherMsg,
            BlstBLS12381.sign(secretKeys.get(3), otherMsg));
    final BatchSemiAggregate invalidOtherAggregate =
        bls.prepareBatchVerify(
            3,
            List.of(secretKeys.get(3).derivePublicKey()),
            otherMsg,
            BlstBLS12381.sign(secretKeys.get(3), msg));

    assertThat(bls.completeBatchVerify(List.of(sameMessageAggregate1, otherAggregate))).isTrue();
    assertThat(bls.completeBatchVerify(List.of(sameMessageAggregate2, invalidOtherAggregate)))
        .isFalse();
  }

  @Test
  void prepareBatchVerifyChunkSucceedsForValidSignatures() {
    final Chunk chunk = validChunk(5);

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifyChunk(chunk.publicKeys(), chunk.messages(), chunk.signatures());

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2, 4})
  void prepareBatchVerifyChunkFailsWhenAMemberIsInvalid(final int invalidPosition) {
    final List<BlstSecretKey> secretKeys = randomSecretKeys(5);
    final List<Bytes> messages = distinctMessages(5);
    final List<List<BlstPublicKey>> publicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> signatures =
        IntStream.range(0, 5)
            .mapToObj(
                i ->
                    BlstBLS12381.sign(
                        secretKeys.get(i),
                        i == invalidPosition
                            ? Bytes32.fromHexString("0xdeadbeef")
                            : messages.get(i)))
            .toList();

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifyChunk(publicKeys, messages, signatures);

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 7, 8, 9})
  void prepareBatchVerifyChunkHandlesPartialAndFullChunks(final int size) {
    final Chunk chunk = validChunk(size);

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifyChunk(chunk.publicKeys(), chunk.messages(), chunk.signatures());

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isTrue();
  }

  @Test
  void prepareBatchVerifyChunkIsInvalidForInfinityPublicKey() {
    final Chunk chunk = validChunk(3);
    final BlstPublicKey infinityKey = BlstSecretKey.fromBytes(Bytes32.ZERO).derivePublicKey();
    assertThat(infinityKey.isInfinity()).isTrue();
    final List<List<BlstPublicKey>> publicKeys =
        List.of(chunk.publicKeys().get(0), List.of(infinityKey), chunk.publicKeys().get(2));

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifyChunk(publicKeys, chunk.messages(), chunk.signatures());

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @Test
  void prepareBatchVerifyChunkIsInvalidWhenEveryMemberIsInfinity() {
    final Chunk chunk = validChunk(3);
    final BlstPublicKey infinityKey = BlstSecretKey.fromBytes(Bytes32.ZERO).derivePublicKey();
    assertThat(infinityKey.isInfinity()).isTrue();
    final List<List<BlstPublicKey>> publicKeys =
        List.of(List.of(infinityKey), List.of(infinityKey), List.of(infinityKey));

    final BatchSemiAggregate semiAggregate =
        bls.prepareBatchVerifyChunk(publicKeys, chunk.messages(), chunk.signatures());

    assertThat(bls.completeBatchVerify(List.of(semiAggregate))).isFalse();
  }

  @Test
  void prepareBatchVerifyChunkThrowsForSignatureNotInG2() {
    final Chunk chunk = validChunk(3);
    final List<BlstSignature> signatures =
        List.of(chunk.signatures().get(0), notInG2(), chunk.signatures().get(2));

    assertThrows(
        BlsException.class,
        () -> bls.prepareBatchVerifyChunk(chunk.publicKeys(), chunk.messages(), signatures));
  }

  @Test
  void prepareBatchVerifyChunkCanBeCompletedTogetherWithSameMessageGroups() {
    final Bytes msg = Bytes32.fromHexString("abcdef");
    final List<BlstSecretKey> secretKeys = randomSecretKeys(3);
    final List<List<BlstPublicKey>> groupPublicKeys =
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList();
    final List<BlstSignature> groupSignatures =
        secretKeys.stream().map(sk -> BlstBLS12381.sign(sk, msg)).toList();
    final List<BlstSignature> invalidGroupSignatures =
        List.of(
            groupSignatures.get(0),
            BlstBLS12381.sign(secretKeys.get(1), Bytes32.fromHexString("fedcba")),
            groupSignatures.get(2));

    // completeBatchVerify merges into the first semi-aggregate, so build one per call
    final BatchSemiAggregate chunkAggregate1 = prepareChunk(validChunk(5));
    final BatchSemiAggregate chunkAggregate2 = prepareChunk(validChunk(5));
    final BatchSemiAggregate groupAggregate =
        bls.prepareBatchVerifySameMessage(groupPublicKeys, msg, groupSignatures, false);
    final BatchSemiAggregate invalidGroupAggregate =
        bls.prepareBatchVerifySameMessage(groupPublicKeys, msg, invalidGroupSignatures, false);

    assertThat(bls.completeBatchVerify(List.of(chunkAggregate1, groupAggregate))).isTrue();
    assertThat(bls.completeBatchVerify(List.of(chunkAggregate2, invalidGroupAggregate))).isFalse();
  }

  private record Chunk(
      List<List<BlstPublicKey>> publicKeys, List<Bytes> messages, List<BlstSignature> signatures) {}

  private static Chunk validChunk(final int size) {
    final List<BlstSecretKey> secretKeys = randomSecretKeys(size);
    final List<Bytes> messages = distinctMessages(size);
    return new Chunk(
        secretKeys.stream().map(sk -> List.of(sk.derivePublicKey())).toList(),
        messages,
        IntStream.range(0, size)
            .mapToObj(i -> BlstBLS12381.sign(secretKeys.get(i), messages.get(i)))
            .toList());
  }

  private static BatchSemiAggregate prepareChunk(final Chunk chunk) {
    return bls.prepareBatchVerifyChunk(chunk.publicKeys(), chunk.messages(), chunk.signatures());
  }

  private static List<Bytes> distinctMessages(final int count) {
    return IntStream.range(0, count)
        .<Bytes>mapToObj(i -> Bytes32.fromHexString(Integer.toHexString(0x1000 + i)))
        .toList();
  }

  private static List<BlstSecretKey> randomSecretKeys(final int count) {
    return IntStream.range(0, count).mapToObj(__ -> BlstSecretKey.generateNew(RANDOM)).toList();
  }
}
