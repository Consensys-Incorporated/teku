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

package tech.pegasys.teku.networking.p2p;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.crypto.PrivKey;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.GenerationMode;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.networking.p2p.discovery.discv5.SecretKeyParser;

public class SecretKeyParserPropertyTest {

  @Property(tries = 100, generation = GenerationMode.RANDOMIZED)
  void parseRandomKey(@ForAll("randomKey") final PrivKey key) {
    final Bytes expectedPublicKey = Bytes.wrap(key.publicKey().raw());
    final SecretKey secretKey = SecretKeyParser.fromLibP2pPrivKey(Bytes.wrap(key.raw()));
    assertThat(Functions.deriveCompressedPublicKeyFromPrivate(secretKey))
        .isEqualTo(expectedPublicKey);
  }

  @Provide
  public Arbitrary<PrivKey> randomKey() {
    return Arbitraries.create(PrivateKeyGenerator::generate);
  }
}
