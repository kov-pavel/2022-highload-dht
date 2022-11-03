package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import static ok.dht.test.kovalenko.utils.HttpUtils.CLIENT;

public class Node {

    private final String selfUrl;
    private boolean isIll = false;

    public Node(String selfUrl) {
        this.selfUrl = selfUrl;
    }

    public HttpResponse<byte[]> proxyRequest(String id, Request request, MyHttpSession session)
            throws ExecutionException, InterruptedException, IllegalAccessException, IOException {
        if (isIll()) {
            throw new IllegalAccessException("Node is ill!");
        }

        HttpUtils.CLIENT.setUrl(selfUrl);

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> HttpUtils.CLIENT.get(id, request.getBody(), session);
            case Request.METHOD_PUT -> HttpUtils.CLIENT.put(id, request.getBody(), session);
            case Request.METHOD_DELETE -> HttpUtils.CLIENT.delete(id, request.getBody(), session);
            default -> throw new IllegalArgumentException("Unexpected request method to be proxied: "
                    + request.getMethod());
        };
    }

    public boolean isIll() {
        return isIll;
    }

    public void setIll(boolean isIll) {
        this.isIll = isIll;
    }

    public String selfUrl() {
        return this.selfUrl;
    }
}