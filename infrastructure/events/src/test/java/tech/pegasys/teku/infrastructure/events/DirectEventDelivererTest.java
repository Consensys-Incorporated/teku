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

package tech.pegasys.teku.infrastructure.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Method;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;

class DirectEventDelivererTest {

  private final ChannelExceptionHandler exceptionHandler = mock(ChannelExceptionHandler.class);
  private final Runnable target = mock(Runnable.class);

  private final DirectEventDeliverer<Runnable> deliverer =
      new DirectEventDeliverer<>(exceptionHandler, new NoOpMetricsSystem());
  public static final Object[] NO_ARGS = new Object[0];

  @Test
  void shouldInvokeMethod() throws Exception {
    deliverer.deliverTo(target, Runnable.class.getMethod("run"), new Object[0]);

    verify(target).run();
  }

  @Test
  void shouldNotifyExceptionHandlerWhenMethodThrowsException() throws Exception {
    final RuntimeException error = new RuntimeException("Nope");
    doThrow(error).when(target).run();

    final Method method = Runnable.class.getMethod("run");
    deliverer.deliverTo(target, method, NO_ARGS);

    verify(target).run();
    verify(exceptionHandler).handleException(error, target, method, NO_ARGS);
    verifyNoMoreInteractions(exceptionHandler);
  }

  @Test
  void shouldNotifyExceptionHandlerWhenIllegalAccessExceptionOccurs() throws Exception {
    final ClassWithPrivateMethod target = new ClassWithPrivateMethod();
    final Method method = ClassWithPrivateMethod.class.getDeclaredMethod("run");
    final DirectEventDeliverer<ClassWithPrivateMethod> deliverer =
        new DirectEventDeliverer<>(exceptionHandler, new NoOpMetricsSystem());
    deliverer.deliverTo(target, method, NO_ARGS);

    verify(exceptionHandler)
        .handleException(any(IllegalAccessException.class), eq(target), eq(method), eq(NO_ARGS));
    verifyNoMoreInteractions(exceptionHandler);
  }

  public static class ClassWithPrivateMethod {
    @SuppressWarnings("unused")
    private void run() {}
  }
}
