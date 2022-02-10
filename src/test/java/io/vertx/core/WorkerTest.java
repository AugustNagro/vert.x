package io.vertx.core;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.WorkerContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerTest extends VertxTestBase {

  @Test
  public void testAwait() {
    VertxInternal vertx = (VertxInternal) this.vertx;
    WorkerContext worker = vertx.createWorkerContext();
    PromiseInternal<String> promise = worker.promise();
    AtomicInteger seq = new AtomicInteger();
    worker.runOnContext(v1 -> {
      assertEquals(0, seq.getAndIncrement());
      try {
        String res = worker.await(promise);
        assertEquals("data", res);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      assertEquals(2, seq.getAndIncrement());
      testComplete();
    });
    worker.runOnContext(v -> {
      assertEquals(1, seq.getAndIncrement());
      promise.complete("data");
    });
    await();
  }

  @Test
  public void testHttpClient() throws Exception {
    VertxInternal vertx = (VertxInternal) this.vertx;
    vertx
      .createHttpServer()
      .requestHandler(req -> {
        req.response().setChunked(true).write("Hello");
        vertx.setTimer(100, id -> {
          req.response().end(" World");
        });
      })
      .listen(8080, "localhost")
      .toCompletionStage()
      .toCompletableFuture()
      .get(10, TimeUnit.SECONDS);
    HttpClient client = vertx.createHttpClient();
    WorkerContext workerCtx = vertx.createWorkerContext();
    workerCtx.runOnContext(v -> {
      Future<HttpClientRequest> fut = client.request(HttpMethod.GET, 8080, "localhost", "/");
      try {
        HttpClientRequest req = workerCtx.await(fut);
        HttpClientResponse resp = workerCtx.await(req.send());
        Buffer body = workerCtx.await(resp.body());
        assertEquals("Hello World", body.toString());
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      testComplete();
    });
    await();
  }
}
