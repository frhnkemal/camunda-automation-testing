package com.camunda.simulator;

import com.camunda.simulator.http.SimulatorHttpHandler;
import com.camunda.simulator.http.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Main application class for Camunda Design-Time Process Simulator.
 * Uses Java's built-in HTTP server (no Spring framework).
 */
public class SimulatorApplication {
    
    private static final int PORT = 8080;
    private static final String CONTEXT_PATH = "/api";
    
    public static void main(String[] args) {
        try {
            // Create HTTP server
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Create context for API endpoints
            server.createContext(CONTEXT_PATH, new SimulatorHttpHandler());
            
            // Create context for static files (UI)
            server.createContext("/", new StaticFileHandler());
            
            // Set executor
            server.setExecutor(Executors.newFixedThreadPool(10));
            
            // Start server
            server.start();
            
            System.out.println("=".repeat(60));
            System.out.println("Camunda Design-Time Process Simulator");
            System.out.println("=".repeat(60));
            System.out.println("Server started on http://localhost:" + PORT);
            System.out.println("API endpoints available at http://localhost:" + PORT + CONTEXT_PATH);
            System.out.println("Web UI available at http://localhost:" + PORT);
            System.out.println("=".repeat(60));
            System.out.println("Press Ctrl+C to stop the server");
            
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

