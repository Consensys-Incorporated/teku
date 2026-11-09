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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

public class FileKeyValueStoreTest {

  @TempDir Path kvDir;

  @Test
  void testSimple() {
    FileKeyValueStore store = new FileKeyValueStore(kvDir);

    store.put("aaa", Bytes.fromHexString("0x112233"));

    Optional<Bytes> v1 = store.get("aaa");
    assertThat(v1).contains(Bytes.fromHexString("0x112233"));

    store.remove("aaa");
    Optional<Bytes> v2 = store.get("aaa");
    assertThat(v2).isEmpty();
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void testConcurrent() {
    FileKeyValueStore store = new FileKeyValueStore(kvDir);

    int nThreads = 8;
    int iterations = 100;
    ExecutorService threadPool = Executors.newFixedThreadPool(nThreads);

    SafeFuture<Void>[] futs = new SafeFuture[nThreads];
    for (int i = 0; i < nThreads; i++) {
      byte[] bytes = new byte[1024];
      Arrays.fill(bytes, (byte) i);

      CompletableFuture<Void> future =
          SafeFuture.runAsync(
              () -> {
                for (int j = 0; j < iterations; j++) {
                  store.remove("aaa");
                  store.put("aaa", Bytes.wrap(bytes));
                  Optional<Bytes> val = store.get("aaa");
                  if (val.isPresent()) {
                    assertThat(val.get().size()).isEqualTo(bytes.length);
                    checkAllBytesEqual(val.get());
                  }
                }
              },
              threadPool);
      futs[i] = SafeFuture.of(future);
    }

    SafeFuture<Void> allFut = SafeFuture.allOfFailFast(futs);
    assertThatCode(() -> allFut.get(60, TimeUnit.SECONDS)).doesNotThrowAnyException();

    Optional<Bytes> finalVal = store.get("aaa");
    assertThat(finalVal).isNotEmpty();
    checkAllBytesEqual(finalVal.get());
  }

  private static void checkAllBytesEqual(final Bytes bb) {
    byte b = bb.get(0);
    for (int i = 1; i < bb.size(); i++) {
      if (bb.get(i) != b) {
        throw new IllegalArgumentException("Invalid bytes: " + bb);
      }
    }
  }
}
