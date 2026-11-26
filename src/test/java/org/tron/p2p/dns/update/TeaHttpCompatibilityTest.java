package org.tron.p2p.dns.update;

import com.aliyun.tea.Tea;
import com.aliyun.tea.TeaRequest;
import com.aliyun.tea.TeaResponse;
import com.aliyun.tea.TeaRetryableException;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

public class TeaHttpCompatibilityTest {

  @Test(timeout = 10000)
  public void teaCanSendRequestBodiesAndReadResponses() throws Exception {
    AtomicReference<String> received = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/dns", exchange -> {
      try {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream input = exchange.getRequestBody()) {
          byte[] buffer = new byte[1024];
          int length;
          while ((length = input.read(buffer)) != -1) {
            body.write(buffer, 0, length);
          }
        }
        received.set(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
            + exchange.getRequestHeaders().getFirst("Authorization") + " "
            + new String(body.toByteArray(), StandardCharsets.UTF_8));
        byte[] response = "{\"RequestId\":\"local-test\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
      } finally {
        exchange.close();
      }
    });
    server.start();
    try {
      TeaRequest request = new TeaRequest();
      request.method = "POST";
      request.pathname = "/dns";
      request.query.put("Action", "DescribeDomainRecords");
      request.headers.put("host", "127.0.0.1:" + server.getAddress().getPort());
      request.headers.put("content-type", "application/json");
      request.headers.put("Authorization", "test-signature");
      request.body = Tea.toReadable("{\"DomainName\":\"example.invalid\"}");

      TeaResponse response = Tea.doAction(request);
      try {
        Assert.assertEquals(200, response.statusCode);
        Assert.assertEquals("{\"RequestId\":\"local-test\"}", response.getResponseBody());
        Assert.assertEquals("POST /dns?Action=DescribeDomainRecords test-signature "
            + "{\"DomainName\":\"example.invalid\"}", received.get());
      } finally {
        response.response.close();
      }
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void invalidSensitiveHeadersDoNotLeakThroughTeaExceptions() {
    for (String header : new String[]{"Authorization", "Proxy-Authorization"}) {
      TeaRequest request = new TeaRequest();
      request.pathname = "/";
      request.headers.put("host", "127.0.0.1:1");
      request.headers.put(header, "test-secret\ninvalid");

      TeaRetryableException failure = Assert.assertThrows(TeaRetryableException.class,
          () -> Tea.doAction(request));
      Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        Assert.assertFalse(String.valueOf(cause.getMessage()).contains("test-secret"));
      }
    }
  }
}
