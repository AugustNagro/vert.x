package examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.loom.core.VertxLoom;

import java.nio.charset.StandardCharsets;

public class AwaitBug {

  static final String FUT_VALUE = "hello world";

  public static Future<String> fut() {
    return Future.succeededFuture(FUT_VALUE);
  }

  public static void main(String[] args) throws Exception {
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
