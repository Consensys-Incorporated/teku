/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.infrastructure.subscribers;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.FatalErrorHandler;

/**
 * A value holder class which notifies subscribers on value updates
 *
 * <p>The key feature of this class is that upon subscription a new {@link ValueObserver} is always
 * notified of the current value (if the value exists) and this is performed in a thread-safe manner
 * such that {@link #set(Object)} and {@link #subscribe(ValueObserver)} methods can be safely called
 * from different threads
 *
 * <p>All subscribers are guaranteed:
 *
 * <ul>
 *   <li>to be always notified on the latest value
 *   <li>to be notified on each value just once
 *   <li>to be notified in the right order if the {@link #set(Object)} is invoked on the same thread
 * </ul>
 *
 * Initially the holder has no value so added subscribers are not notified upon subscription
 *
 * @param <C> Value type
 */
public class ObservableValue<C> {
  private static final Logger LOG = LogManager.getLogger();

  private static final class Subscription<C> {
    private final ValueObserver<C> subscriber;
    private final long subscriptionId;

    public Subscription(final ValueObserver<C> subscriber, final long subscriptionId) {
      this.subscriber = subscriber;
      this.subscriptionId = subscriptionId;
    }

    public ValueObserver<C> getSubscriber() {
      return subscriber;
    }

    public long getSubscriptionId() {
      return subscriptionId;
    }
  }

  private long idCounter = 0;
  private final List<Subscription<C>> subscriptions = new CopyOnWriteArrayList<>();
  private C curValue;
  private final boolean suppressCallbackExceptions;

  /**
   * Creates instance
   *
   * @param suppressCallbackExceptions if true then any exceptions thrown from a subscriber update
   *     callback are just printed to the log
   */
  public ObservableValue(final boolean suppressCallbackExceptions) {
    this.suppressCallbackExceptions = suppressCallbackExceptions;
  }

  /**
   * Subscribe to value update notification
   *
   * <p>New subscriber is notified on the latest value if the value exist
   *
   * @return subscription ID to be used for {@link #unsubscribe(long)}
   */
  public synchronized long subscribe(final ValueObserver<C> subscriber) {
    Subscription<C> subscription = new Subscription<>(subscriber, idCounter++);
    subscriptions.add(subscription);
    if (curValue != null) {
      notify(subscription, curValue);
    }
    return subscription.getSubscriptionId();
  }

  /**
   * Cancels previously created subscription
   *
   * @param subscriptionId ID return by the {@link #subscribe(ValueObserver)} method
   */
  public synchronized void unsubscribe(final long subscriptionId) {
    subscriptions.removeIf(s -> s.getSubscriptionId() == subscriptionId);
  }

  /**
   * Sets the current value and notifies each subscriber
   *
   * @param c the non-null value
   */
  public void set(final C c) {
    Iterator<Subscription<C>> iterator;
    synchronized (this) {
      curValue = c;
      iterator = subscriptions.iterator();
    }

    iterator.forEachRemaining(l -> notify(l, c));
  }

  private void notify(final Subscription<C> subscription, final C value) {
    try {
      subscription.getSubscriber().onValueChanged(value);
    } catch (Throwable throwable) {
      if (suppressCallbackExceptions) {
        // A fatal error must shut the node down rather than just being logged
        if (!FatalErrorHandler.shutdownIfFatalError(throwable, "value change callback")) {
          LOG.error("Error in callback: ", throwable);
        }
      } else {
        throw throwable;
      }
    }
  }

  /** Returns the current value or {@link Optional#empty()} is the value was not set yet */
  public synchronized Optional<C> get() {
    return Optional.ofNullable(curValue);
  }
}
