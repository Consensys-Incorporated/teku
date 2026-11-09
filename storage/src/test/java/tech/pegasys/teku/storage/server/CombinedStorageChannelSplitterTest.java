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

package tech.pegasys.teku.storage.server;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;

class CombinedStorageChannelSplitterTest {
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StorageQueryChannel storageQueryChannel = mock(StorageQueryChannel.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  private final CombinedStorageChannelSplitter splitter =
      new CombinedStorageChannelSplitter(asyncRunner, storageUpdateChannel, storageQueryChannel);

  @ParameterizedTest
  @MethodSource("updateChannelMethods")
  void shouldApplyUpdateMethodsSynchronously(final Method method) throws Exception {
    final Object[] args = prepareArgs(method);

    method.invoke(splitter, args);

    final StorageUpdateChannel preppedMock = verify(storageUpdateChannel);
    method.invoke(preppedMock, args);
  }

  @ParameterizedTest
  @MethodSource("queryChannelMethods")
  void shouldApplyQueryMethodsAsynchronously(final Method method) throws Exception {
    final Object[] args = prepareArgs(method);

    method.invoke(splitter, args);

    method.invoke(verify(storageQueryChannel, never()), args);

    asyncRunner.executeQueuedActions();

    method.invoke(verify(storageQueryChannel), args);
  }

  private Object[] prepareArgs(final Method method) {
    return Arrays.stream(method.getParameters())
        .map(
            parameter -> {
              if (parameter.getType().isPrimitive()) {
                final String type = parameter.getType().getName();
                return switch (type) {
                  case "int" -> 0;
                  case "long" -> 0L;
                  default -> throw new RuntimeException("unsupported primitive type: " + type);
                };
              } else {
                return null;
              }
            })
        .toArray();
  }

  static Stream<Arguments> updateChannelMethods() {
    return channelMethods(StorageUpdateChannel.class);
  }

  static Stream<Arguments> queryChannelMethods() {
    final Class<StorageQueryChannel> channelClass = StorageQueryChannel.class;
    return channelMethods(channelClass);
  }

  private static Stream<Arguments> channelMethods(final Class<?> channelClass) {
    return Stream.of(channelClass.getDeclaredMethods())
        .map(method -> Named.named(method.getName(), method))
        .map(Arguments::of);
  }
}
