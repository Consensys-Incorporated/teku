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

package tech.pegasys.teku.networking.p2p.libp2p;

import static org.assertj.core.api.Assertions.assertThat;

import io.libp2p.core.crypto.KeyKt;
import io.libp2p.core.crypto.KeyType;
import io.libp2p.core.crypto.PrivKey;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.network.p2p.jvmlibp2p.PrivateKeyGenerator;
import tech.pegasys.teku.networking.p2p.network.config.PrivateKeySource;
import tech.pegasys.teku.storage.store.MemKeyValueStore;

public class LibP2PPrivateKeyLoaderTest {
  private final MemKeyValueStore<String, Bytes> store = new MemKeyValueStore<>();

  @Test
  void testPrivateKeyGeneration() {
    final LibP2PPrivateKeyLoader loader = new LibP2PPrivateKeyLoader(store, Optional.empty());

    // check that new key is generated
    final PrivKey generatedPK = loader.get();
    assertThat(generatedPK).isNotNull();
    assertRoundTrip(generatedPK);

    // check the same key loaded next time
    PrivKey loadedPK = loader.get();
    assertThat(loadedPK).isEqualTo(generatedPK);

    // If store is cleared, we should generate a new key
    store.remove(LibP2PPrivateKeyLoader.GENERATED_NODE_KEY_KEY);

    // check that another key is generated after old key is deleted
    PrivKey generatedAnotherPK = loader.get();
    assertThat(generatedAnotherPK).isNotNull();
    assertRoundTrip(generatedAnotherPK);
    assertThat(generatedAnotherPK).isNotEqualTo(generatedPK);
  }

  @Test
  void testPrivateKeyLoaded() {
    // check that user supplied private key file has precedence over generated file
    final PrivKey privKey = PrivateKeyGenerator.generate();
    final Bytes privKeyMarshalledBytes = Bytes.wrap(privKey.bytes());
    PrivateKeySource privKeySource = () -> privKeyMarshalledBytes;

    final LibP2PPrivateKeyLoader loader =
        new LibP2PPrivateKeyLoader(store, Optional.of(privKeySource));
    assertThat(loader.get()).isEqualTo(privKey);
  }

  @ParameterizedTest(name = "type = {0}")
  @MethodSource("getTypes")
  void testPrivateKeyTypedLoaded(final KeyType keyType, final PrivateKeySource.Type type) {
    final PrivKey privKey = KeyKt.generateKeyPair(keyType).component1();
    final Bytes privKeyOnlyBytes = Bytes.wrap(privKey.raw());
    PrivateKeySource privKeySource =
        new PrivateKeySource() {
          @Override
          public Bytes getPrivateKeyBytes() {
            return privKeyOnlyBytes;
          }

          @Override
          public Optional<Type> getType() {
            return Optional.of(type);
          }
        };

    final LibP2PPrivateKeyLoader loader =
        new LibP2PPrivateKeyLoader(store, Optional.of(privKeySource));
    assertThat(loader.get()).isEqualTo(privKey);
  }

  private static Stream<Arguments> getTypes() {
    return Stream.of(
        Arguments.of(KeyType.SECP256K1, PrivateKeySource.Type.SECP256K1),
        Arguments.of(KeyType.ECDSA, PrivateKeySource.Type.ECDSA));
  }

  private void assertRoundTrip(final PrivKey generatedPK) {
    final PrivKey reparsed = KeyKt.unmarshalPrivateKey(generatedPK.bytes());
    assertThat(reparsed).isEqualTo(generatedPK);
  }
}
