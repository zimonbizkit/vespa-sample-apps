// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.example.shopping.site;

import ai.vespa.example.shopping.site.data.Categories;
import ai.vespa.example.shopping.site.data.Item;
import ai.vespa.example.shopping.site.data.Promoted;
import ai.vespa.example.shopping.site.data.ReviewVote;
import ai.vespa.example.shopping.site.data.Reviews;
import ai.vespa.example.shopping.site.data.SearchResults;
import ai.vespa.example.shopping.site.data.SimpleHttpClient;
import ai.vespa.example.shopping.site.view.SimpleTemplate;
import ai.vespa.example.shopping.site.view.HomeRenderer;
import ai.vespa.example.shopping.site.view.ItemRenderer;
import ai.vespa.example.shopping.site.view.SearchRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SiteHandler extends ThreadedHttpRequestHandler {

    private static Logger log = Logger.getLogger(SiteHandler.class.getName());

    private static final String SITE_ROOT = "site";
    private static final String SEARCH_PATH = SITE_ROOT + "/search";
    private static final String ITEM_PATH = SITE_ROOT + "/item";
    private static final String REVIEW_VOTE_PATH = SITE_ROOT + "/vote";

    private final String vespaHostProtocol = "http";
    private final String vespaHostName;
    private final String vespaHostPort;

    public SiteHandler(Executor executor, SiteHandlerConfig config) {
        super(executor);
        this.vespaHostName = config.vespaHostName();
        this.vespaHostPort = String.valueOf(config.vespaHostPort());
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {
        Map<String, String> properties = httpRequest.propertyMap();
        String path = getPath(httpRequest);
        try {
            if (path == null || path.length() == 0 || ! path.startsWith(SITE_ROOT)) {
                return error("Invalid path", 404);
            }
            if (path.equals(SITE_ROOT) || path.equals(SITE_ROOT + "/")) {
                return homepage(properties);
            }
            if (path.startsWith(SEARCH_PATH)) {
                return search(properties);
            }
            if (path.startsWith(ITEM_PATH)) {
                return item(properties);
            }
            if (path.startsWith(REVIEW_VOTE_PATH)) {
                return vote(properties);
            }
            return file(path);

        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage(), e);
        }
        return error("Error handling search request", 400);
    }

    private HttpResponse homepage(Map<String, String> properties) throws IOException {
        Map<String, JsonNode> data = new HashMap<>();
        try (SimpleHttpClient client = new SimpleHttpClient(vespaHostProtocol, vespaHostName, vespaHostPort)) {
            data.putAll(Categories.data(client, properties));
            data.putAll(Promoted.data(client, properties));
        }
        SimpleTemplate view = HomeRenderer.render(data, properties);
        return response(200, view);
    }

    private HttpResponse search(Map<String, String> properties) throws IOException {
        Map<String, JsonNode> data = new HashMap<>();
        try (SimpleHttpClient client = new SimpleHttpClient(vespaHostProtocol, vespaHostName, vespaHostPort)) {
            data.putAll(SearchResults.data(client, properties));
        }
        SimpleTemplate view = SearchRenderer.render(data, properties);
        return response(200, view);
    }

    private HttpResponse item(Map<String, String> properties) throws IOException {
        Map<String, JsonNode> data = new HashMap<>();
        try (SimpleHttpClient client = new SimpleHttpClient(vespaHostProtocol, vespaHostName, vespaHostPort)) {
            data.putAll(Item.data(client, properties));
            data.putAll(Reviews.data(client, properties));
        }
        SimpleTemplate view = ItemRenderer.render(data, properties);
        return response(200, view);
    }

    private HttpResponse vote(Map<String, String> properties) throws IOException {
        try (SimpleHttpClient client = new SimpleHttpClient(vespaHostProtocol, vespaHostName, vespaHostPort)) {
            ReviewVote.vote(client, properties);
        }
        return item(properties);
    }

    private HttpResponse response(int code, SimpleTemplate template) {
        return new HttpResponse(code) {
            @Override
            public void render(OutputStream stream) {
                template.render(stream);
            }
            @Override
            public String getContentType() {
                return "text/html";
            }
        };
    }

    private HttpResponse file(String filename) {
        if (!resourceExists(filename)) {
            return error("Error retrieving " + filename, 404);
        }
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                try(InputStream inputStream = resourceAsStream(filename)) {
                    copy(inputStream, outputStream);
                }
            }
            @Override
            public String getContentType() {
                return contentType(filename);
            }
        };
    }

    private static void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
        }
    }

    private static String contentType(String fname) {
        switch (fname.substring(fname.lastIndexOf(".") + 1)) {
            case "html": return "text/html";
            case "css":  return "text/css";
            case "js":   return "application/javascript";
            case "json": return "application/json";
            case "png":  return "image/png";
            case "svg": return "image/svg+xml";
        }
        return "text/plain";
    }

    private boolean resourceExists(String filename) {
        return getClass().getClassLoader().getResource(filename) != null;
    }

    private InputStream resourceAsStream(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    private static String getPath(HttpRequest httpRequest) {
        String path = httpRequest.getUri().getPath();
        if (path == null || path.length() == 0) {
            return null;
        }
        path = path.toLowerCase();
        path = path.startsWith("/") ? path.substring(1) : path;
        return path;
    }

    static HttpResponse error(String msg, int status) {
        return new HttpResponse(status) {
            @Override
            public void render(OutputStream stream) {
                try (PrintWriter writer = new PrintWriter(stream)) {
                    writer.print(msg);
                }
            }
        };
    }


}
