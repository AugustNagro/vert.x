/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl;

import io.vertx.test.core.AsyncTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class TaskQueueTest extends AsyncTestBase {

  private static Runnable action(String name, Runnable runnable) {
    return new Runnable() {
      @Override
      public void run() {
        runnable.run();
      }
      @Override
      public String toString() {
        return "Action[" + name + "]";
      }
    };
  }

  final Object monitor = new Object();
  Consumer<Runnable> consumer;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    disableThreadChecks();
  }

  @Test
  public void testUnschedule1() {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    AtomicInteger seq = new AtomicInteger();
    TaskQueue queue = new TaskQueue();
    CountDownLatch latch1 = new CountDownLatch(1);
    queue.execute(action("1", () -> {
      try {
        latch1.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      assertEquals(0, seq.getAndIncrement());
      synchronized (monitor) {
        consumer = queue.unschedule();
      }
    }), executor);
    queue.execute(action("2", () -> {
      assertEquals(1, seq.getAndIncrement());
      Consumer<Runnable> c;
      synchronized (monitor) {
        c = consumer;
      }
      c.accept(action("3", () -> {
        assertEquals(2, seq.getAndIncrement());
      }));
    }), executor);
    queue.execute(action("4", () -> {
      assertEquals(3, seq.getAndIncrement());
      testComplete();
    }), executor);
    latch1.countDown();
    await();
  }

  @Test
  public void testUnschedule2() {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicInteger seq = new AtomicInteger();
    TaskQueue queue = new TaskQueue();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    queue.execute(action("1", () -> {
      try {
        latch1.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      assertEquals(0, seq.getAndIncrement());
      synchronized (monitor) {
        consumer = queue.unschedule();
      }
      try {
        latch2.await();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      assertEquals(2, seq.getAndIncrement());
    }), executor);
    queue.execute(action("2", () -> {
      assertEquals(1, seq.getAndIncrement());
      Consumer<Runnable> c;
      synchronized (monitor) {
        c = consumer;
      }
      c.accept(action("3", latch2::countDown));
    }), executor);
    queue.execute(action("4", () -> {
      assertEquals(3, seq.getAndIncrement());
      testComplete();
    }), executor);
    latch1.countDown();
    await();
  }

  @Test
  public void testUnschedule3() {
    ExecutorService executor = Executors.newFixedThreadPool(1);
    AtomicInteger seq = new AtomicInteger();
    TaskQueue queue = new TaskQueue();
    queue.execute(action("1", () -> {
      assertEquals(0, seq.getAndIncrement());
      Consumer<Runnable> consumer = queue.unschedule();
      consumer.accept(() -> {
        assertEquals(1, seq.getAndIncrement());
      });
      assertEquals(2, seq.getAndIncrement());
      testComplete();
    }), executor);
    await();
  }
}
