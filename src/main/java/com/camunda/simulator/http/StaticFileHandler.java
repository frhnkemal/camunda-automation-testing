package com.camunda.simulator.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;

/**
 * Handles static file requests (HTML, CSS, JS).
 */
public class StaticFileHandler implements HttpHandler {
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        // Default to index.html for root
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        
        // Try to load from resources
        InputStream resourceStream = getClass().getResourceAsStream("/static" + path);
        
        if (resourceStream != null) {
            // Determine content type
            String contentType = getContentType(path);
            
            // Read file content
            byte[] content = resourceStream.readAllBytes();
            resourceStream.close();
            
            // Send response
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        } else {
            // File not found
            String notFound = "404 - File not found: " + path;
            exchange.sendResponseHeaders(404, notFound.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound.getBytes());
            }
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (path.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (path.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (path.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        } else if (path.endsWith(".png")) {
            return "image/png";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}

