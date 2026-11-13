package com.djomkam.catalogsense.search;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A tiny HTTP server that answers like OpenSearch with canned JSON. It lets the tests exercise the
 * real client (serialization, typed aggregation keys, error mapping) without Docker.
 */
final class FakeOpenSearch implements AutoCloseable {

    record Request(String method, String uri, String body) {
    }

    record Response(int status, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Function<Request, Response> responder = r -> new Response(200, "{}");

    FakeOpenSearch() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(), body);
            requests.add(request);
            Response response = responder.apply(request);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            if ("HEAD".equals(request.method())) {
                exchange.sendResponseHeaders(response.status(), -1);
            } else {
                exchange.sendResponseHeaders(response.status(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respond(Function<Request, Response> responder) {
        this.responder = responder;
    }

    List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
