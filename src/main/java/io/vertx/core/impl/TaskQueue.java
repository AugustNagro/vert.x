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

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * A task queue that always run all tasks in order. The executor to run the tasks is passed
 * when the tasks are executed, this executor is not guaranteed to be used, as if several
 * tasks are queued, the original thread will be used.
 *
 * More specifically, any call B to the {@link #execute(Runnable, Executor)} method that happens-after another call A to the
 * same method, will result in B's task running after A's.
 *
 * @author <a href="david.lloyd@jboss.com">David Lloyd</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TaskQueue {

  static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

  private static class Task {

    private final Runnable runnable;
    private final Executor exec;
    private boolean completed;

    public Task(Runnable runnable, Executor exec) {
      if (runnable == null) {
        throw new NullPointerException();
      }
      if (exec == null) {
        throw new NullPointerException();
      }
      this.runnable = runnable;
      this.exec = exec;
    }
  }

  // @protectedby tasks
  private final LinkedList<Task> tasks = new LinkedList<>();

  // @protectedby tasks
  private Task current;

  private final Runnable runner;

  public TaskQueue() {
    runner = this::run;
  }

  private void run() {
    for (; ; ) {
      final Task task;
      synchronized (tasks) {
        task = current;
      }
      try {
        // System.out.println(task.runnable);
        Runnable runnable = task.runnable;
        runnable.run();
      } catch (Throwable t) {
        log.error("Caught unexpected Throwable", t);
      }
      synchronized (tasks) {
        task.completed = true;
        if (current != task) {
          break;
        }
        Task next = tasks.poll();
        if (next == null) {
          current = null;
          break;
        }
        current = next;
        if (task.exec != next.exec) {
          task.exec.execute(runner);
          break;
        }
      }
    }
  }

  /**
   * Run a task.
   *
   * @param runnable the task to run.
   */
  public void execute(Runnable runnable, Executor executor) {
    Task task = new Task(runnable, executor);
    synchronized (tasks) {
      if (current == null) {
        current = task;
        executor.execute(runner);
      } else {
        tasks.add(task);
      }
    }
  }

  Consumer<Runnable> unschedule() {
    Task task;
    synchronized (tasks) {
      task = current;
      if (task == null) {
        throw new IllegalStateException();
      }
      current = tasks.poll();
      if (current != null) {
        current.exec.execute(runner);
      }
    }
    return t -> {
      synchronized (tasks) {
        if (!task.completed) {
          if (current == null) {
            current = task;
            t.run();
          } else {
            tasks.addFirst(new Task(new Runnable() {
              @Override
              public void run() {
                synchronized (tasks) {
                  current = task;
                }
                t.run();
              }
              @Override
              public String toString() {
                return t.toString();
              }
            }, current.exec));
          }
        } else {
          tasks.addFirst(new Task(t, current.exec));
        }
      }
    };
  }

}
