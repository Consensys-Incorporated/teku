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

package tech.pegasys.teku.networking.p2p.discovery.discv5;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

class SecretKeyParserTest {
  @Test
  void shouldParse33BytePrivateKey() {
    final String priv = "0x008810c0cc99ec947b27f722e2394e0abe6d859620a3fca3e50768296556bc7152";
    final String pub = "0x03537407702e5203273229cd788a35d2bf3a533d4f952e677264b0f49ce60b8dd2";
    assertPrivateKeyParsedCorrectly(priv, pub);
  }

  @Test
  void shouldParse32BytePrivateKey() {
    assertPrivateKeyParsedCorrectly(
        "0x5794f6aeb9c58e6ee6406929f34c6753a2798ff2c3a4d55b98fb2acf430c9eeb",
        "0x0291ea40532745dff8d437a481b46a003dd0a08b1ba84cb8a3d88a6bae13204a59");
  }

  private static void assertPrivateKeyParsedCorrectly(
      final String privKeyHex, final String expectedPubKeyHex) {
    final Bytes privKey = Bytes.fromHexString(privKeyHex);
    final Bytes pubKey = Bytes.fromHexString(expectedPubKeyHex);
    final SecretKey secretKey = SecretKeyParser.fromLibP2pPrivKey(privKey);

    assertThat(Functions.deriveCompressedPublicKeyFromPrivate(secretKey)).isEqualTo(pubKey);
  }
}
