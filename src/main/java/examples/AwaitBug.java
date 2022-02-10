package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.LoomContext;
import io.vertx.loom.core.VertxLoom;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

public class AwaitBug {

  static final String FUT_VALUE = "hello world";

  public static Future<String> fut() {
    return Future.succeededFuture(FUT_VALUE);
  }

  public static void main(String[] args) throws Exception {
    new AwaitBug().contextSwitchIssue();
  }

  public void contextSwitchIssue() {
    Vertx vertx = Vertx.vertx()
      .exceptionHandler(Throwable::printStackTrace);
    VertxLoom vertxLoom = new VertxLoom(vertx);

    vertxLoom.virtual(() -> {

      CountDownLatch latch = new CountDownLatch(1);

      Vertx.currentContext().runOnContext(v -> {
        latch.countDown();
      });

      try {
        boolean finished = latch.await(3, TimeUnit.SECONDS);
        if (!finished) throw new RuntimeException("didn't finish");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    });

  }

  public void clientAndServerTest() {
    Vertx vertx = Vertx.vertx()
      .exceptionHandler(Throwable::printStackTrace);
    VertxLoom vertxLoom = new VertxLoom(vertx);

    vertxLoom.virtual(() -> {
      HttpServer server = vertx.createHttpServer();
      server.requestHandler(req -> {
        String futVal = vertxLoom.await(fut());
        req.response().end(futVal);
      });
      vertxLoom.await(server.listen(8088, "localhost"));

      HttpClient client = vertx.createHttpClient();
      System.out.println("If 100 lines are printed the test passed:");
      for (int i = 0; i < 100; ++i) {
        System.out.println("Attempt #" + i);
        HttpClientRequest req = vertxLoom.await(client.request(HttpMethod.GET, 8088, "localhost", "/"));
        HttpClientResponse resp = vertxLoom.await(req.send());
        Buffer body = vertxLoom.await(resp.body());
        String bodyString = body.toString(StandardCharsets.UTF_8);
        if (!FUT_VALUE.equals(bodyString)) throw new RuntimeException("Failed");
      }
      System.out.println("It worked!");
    });
  }
}
