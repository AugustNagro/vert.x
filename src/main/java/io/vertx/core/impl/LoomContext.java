package io.vertx.core.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.impl.future.FutureInternal;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * A fork a WorkerContext with a couple of changes.
 */
public class LoomContext extends ContextImpl {

  public static LoomContext create(
    Vertx vertx,
    EventLoop nettyEventLoop,
    ThreadFactory threadFactory
  ) {
    VertxImpl _vertx = (VertxImpl) vertx;
    LoomContext[] ref = new LoomContext[1];
    ExecutorService exec = Executors.newCachedThreadPool(threadFactory);
    LoomContext context = new LoomContext(
      _vertx,
      nettyEventLoop,
      _vertx.internalWorkerPool,
      new WorkerPool(exec, null),
      null,
      _vertx.closeFuture(),
      null,
      threadFactory
    );
    ref[0] = context;
    return context;
  }


  private final ThreadFactory threadFactory;

  LoomContext(
    VertxInternal vertx,
    EventLoop eventLoop,
    WorkerPool internalBlockingPool,
    WorkerPool workerPool,
    Deployment deployment,
    CloseFuture closeFuture,
    ClassLoader tccl,
    ThreadFactory threadFactory
  ) {
    super(vertx, eventLoop, internalBlockingPool, workerPool, deployment, closeFuture, tccl);

    this.threadFactory = threadFactory;
  }

  @Override
  protected void runOnContext(AbstractContext ctx, Handler<Void> action) {
    try {
      run(ctx, orderedTasks, null, action);
    } catch (RejectedExecutionException ignore) {
      // Pool is already shut down
    }
  }

  /**
   * <ul>
   *   <li>When the current thread is a worker thread of this context the implementation will
   *   execute the {@code task} directly</li>
   *   <li>Otherwise the task will be scheduled on the worker thread for execution</li>
   * </ul>
   */
  @Override
  <T> void execute(AbstractContext ctx, T argument, Handler<T> task) {
    execute2(orderedTasks, argument, task);
  }

  @Override
  <T> void emit(AbstractContext ctx, T argument, Handler<T> task) {
    execute2(orderedTasks, argument, arg -> {
      ctx.dispatch(arg, task);
    });
  }

  @Override
  protected void execute(AbstractContext ctx, Runnable task) {
    execute(this, task, Runnable::run);
  }

  @Override
  public boolean isEventLoopContext() {
    return false;
  }

  private <T> void run(ContextInternal ctx, TaskQueue queue, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    queue.execute(() -> {
      ctx.dispatch(value, task);
    }, workerPool.executor());
  }

  private <T> void execute2(TaskQueue queue, T argument, Handler<T> task) {
    if (Context.isOnWorkerThread()) {
      task.handle(argument);
    } else {
      queue.execute(() -> task.handle(argument), workerPool.executor());
    }
  }

  @Override
  public boolean inThread() {
    // Find something better
    return Thread.currentThread().isVirtual();
  }

  public <T> T await(FutureInternal<T> future) {
    CompletableFuture<T> cf = new CompletableFuture<>();
    Consumer<Runnable> back = orderedTasks.unschedule();
    future.onComplete(ar -> {
      back.accept(() -> {
        if (ar.succeeded()) {
          cf.complete(ar.result());
        } else {
          cf.completeExceptionally(ar.cause());
        }
      });
    });
    try {
      return cf.get(10, TimeUnit.MINUTES);
    } catch (ExecutionException | TimeoutException | InterruptedException e) {
      throw new VertxException(e);
    }
  }

}
