package com.camunda.simulator.http;

import com.camunda.simulator.model.InputField;
import com.camunda.simulator.model.SimulationInput;
import com.camunda.simulator.model.SimulationResult;
import com.camunda.simulator.model.TestScenario;
import com.camunda.simulator.service.BpmnInputAnalyzer;
import com.camunda.simulator.service.BpmnValidationRunner;
import com.camunda.simulator.service.DMNEvaluator;
import com.camunda.simulator.service.InputValidator;
import com.camunda.simulator.service.FileManager;
import com.camunda.simulator.service.ProcessEngine;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for simulator API endpoints.
 */
public class SimulatorHttpHandler implements HttpHandler {
    
    private final ProcessEngine processEngine;
    private final DMNEvaluator dmnEvaluator;
    private final FileManager fileManager;
    private final ObjectMapper objectMapper;
    
    public SimulatorHttpHandler() {
        this.fileManager = new FileManager();
        this.dmnEvaluator = new DMNEvaluator(fileManager);
        this.processEngine = new ProcessEngine(fileManager, dmnEvaluator);
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        // Enable CORS
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Content-Disposition");
        
        // Handle OPTIONS request (CORS preflight)
        if ("OPTIONS".equals(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        
        try {
            // Route requests
            if (path.equals("/api/") || path.equals("/api")) {
                handleRoot(exchange);
            } else if (path.equals("/api/simulate") && "POST".equals(method)) {
                handleSimulate(exchange);
            } else if (path.equals("/api/scenarios") && "GET".equals(method)) {
                handleGetScenarios(exchange);
            } else if (path.equals("/api/scenarios/validation") && "GET".equals(method)) {
                handleGetValidationScenarios(exchange);
            } else if (path.startsWith("/api/scenarios/") && path.endsWith("/run") && "POST".equals(method)) {
                handleRunScenario(exchange, path);
            } else if (path.equals("/api/upload/bpmn") && "POST".equals(method)) {
                handleUploadBpmn(exchange);
            } else if (path.equals("/api/upload/dmn") && "POST".equals(method)) {
                handleUploadDmn(exchange);
            } else if (path.equals("/api/files") && "GET".equals(method)) {
                handleGetFiles(exchange);
            } else if (path.equals("/api/inputs") && "GET".equals(method)) {
                handleGetInputs(exchange);
            } else if (path.equals("/api/validate") && "POST".equals(method)) {
                handleValidate(exchange);
            } else {
                sendError(exchange, 404, "Not Found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }
    
    private void handleRoot(HttpExchange exchange) throws IOException {
        Map<String, String> response = new HashMap<>();
        response.put("name", "Camunda Design-Time Process Simulator");
        response.put("version", "1.0.0");
        response.put("description", "MVP for Entry Level Camunda Exercise process validation");
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private void handleSimulate(HttpExchange exchange) throws IOException {
        // Read request body
        InputStream requestBody = exchange.getRequestBody();
        String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
        
        // Validate types: dealMarginPercent must be number, manualPriceCost must be boolean
        List<String> validationErrors = InputValidator.validate(body);
        if (!validationErrors.isEmpty()) {
            String message = String.join("; ", validationErrors);
            sendError(exchange, 400, "Invalid input: " + message);
            return;
        }
        
        // Parse JSON input (types already validated)
        SimulationInput input = objectMapper.readValue(body, SimulationInput.class);
        
        // Execute simulation
        SimulationResult result = processEngine.execute(input);
        
        // Send response
        sendJsonResponse(exchange, 200, result);
    }
    
    private void handleGetScenarios(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 200, BpmnValidationRunner.getTestScenarios());
    }

    private void handleGetValidationScenarios(HttpExchange exchange) throws IOException {
        sendJsonResponse(exchange, 200, BpmnValidationRunner.getInputValidationScenarios());
    }
    
    private void handleRunScenario(HttpExchange exchange, String path) throws IOException {
        // Extract scenario slug from path: /api/scenarios/{slug}/run
        String[] parts = path.split("/");
        if (parts.length < 4) {
            sendError(exchange, 400, "Invalid scenario path");
            return;
        }
        
        String requestedSlug = parts[3];
        Map<String, TestScenario> scenarioBySlug = new HashMap<>();
        for (TestScenario s : BpmnValidationRunner.getTestScenarios()) {
            String slug = toSlug(s.getName());
            scenarioBySlug.put(slug, s);
        }
        
        TestScenario scenario = scenarioBySlug.get(requestedSlug);
        if (scenario == null) {
            sendError(exchange, 404, "Scenario not found: " + requestedSlug);
            return;
        }
        
        SimulationResult result = processEngine.execute(scenario.getInputs());
        sendJsonResponse(exchange, 200, result);
    }
    
    private static String toSlug(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
    
    private void handleValidate(HttpExchange exchange) throws IOException {
        BpmnValidationRunner runner = new BpmnValidationRunner(processEngine);
        BpmnValidationRunner.BpmnValidationResult result = runner.runAll();
        sendJsonResponse(exchange, 200, result);
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object object) throws IOException {
        String json = objectMapper.writeValueAsString(object);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    private void handleUploadBpmn(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            
            String filename = "process.bpmn";
            byte[] fileContent = requestBody;
            
            // Handle multipart/form-data
            if (contentType != null && contentType.startsWith("multipart/form-data")) {
                String boundary = extractBoundary(contentType);
                if (boundary != null) {
                    Map<String, MultipartParser.ParsedFile> files = MultipartParser.parse(requestBody, boundary);
                    if (!files.isEmpty()) {
                        MultipartParser.ParsedFile file = files.values().iterator().next();
                        filename = file.getFilename();
                        fileContent = file.getContent();
                    }
                }
            }
            
            // Store the file
            fileManager.storeBpmnFile(filename, fileContent);
            
            // Reload BPMN in ProcessEngine
            processEngine.loadBpmnFile();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "BPMN file uploaded successfully");
            response.put("filename", filename);
            response.put("size", fileContent.length);
            
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Failed to upload BPMN file: " + e.getMessage());
        }
    }
    
    private void handleUploadDmn(HttpExchange exchange) throws IOException {
        try {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            
            String filename = "decision.dmn";
            byte[] fileContent = requestBody;
            
            // Handle multipart/form-data
            if (contentType != null && contentType.startsWith("multipart/form-data")) {
                String boundary = extractBoundary(contentType);
                if (boundary != null) {
                    Map<String, MultipartParser.ParsedFile> files = MultipartParser.parse(requestBody, boundary);
                    if (!files.isEmpty()) {
                        MultipartParser.ParsedFile file = files.values().iterator().next();
                        filename = file.getFilename();
                        fileContent = file.getContent();
                    }
                }
            }
            
            // Store the file
            fileManager.storeDmnFile(filename, fileContent);
            
            // Reload DMN in DMNEvaluator
            dmnEvaluator.loadDmnFile();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "DMN file uploaded successfully");
            response.put("filename", filename);
            response.put("size", fileContent.length);
            
            sendJsonResponse(exchange, 200, response);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(exchange, 500, "Failed to upload DMN file: " + e.getMessage());
        }
    }
    
    private String extractBoundary(String contentType) {
        int pos = contentType.indexOf("boundary=");
        if (pos == -1) return null;
        pos += 9;
        String boundary = contentType.substring(pos).trim();
        if (boundary.startsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }
        return boundary;
    }
    
    private void handleGetFiles(HttpExchange exchange) throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("bpmnFiles", fileManager.getBpmnFileNames());
        response.put("dmnFiles", fileManager.getDmnFileNames());
        response.put("hasBpmn", fileManager.hasBpmnFiles());
        response.put("hasDmn", fileManager.hasDmnFiles());
        response.put("dmnLoaded", dmnEvaluator.isDmnLoaded());
        
        sendJsonResponse(exchange, 200, response);
    }
    
    private void handleGetInputs(HttpExchange exchange) throws IOException {
        try {
            InputStream bpmnStream = fileManager.getDefaultBpmnFile();
            if (bpmnStream == null) {
                // Return default inputs if no BPMN file
                List<InputField> defaultFields = Arrays.asList(
                    new InputField("manualPriceCost", "boolean", "Manual Price Cost", false, true),
                    new InputField("dealMarginPercent", "number", "Deal Margin Percent", 25.0, true)
                );
                sendJsonResponse(exchange, 200, defaultFields);
                return;
            }
            
            // Read the stream into bytes first (since stream can only be read once)
            byte[] bpmnBytes = bpmnStream.readAllBytes();
            bpmnStream.close();
            
            // Parse BPMN and extract input fields
            BpmnModelInstance bpmnModel = Bpmn.readModelFromStream(new java.io.ByteArrayInputStream(bpmnBytes));
            BpmnInputAnalyzer analyzer = new BpmnInputAnalyzer(bpmnModel);
            List<InputField> inputFields = analyzer.extractInputFields();
            
            System.out.println("Extracted " + inputFields.size() + " input fields from BPMN");
            for (InputField field : inputFields) {
                System.out.println("  - " + field.getName() + " (" + field.getType() + "): " + field.getLabel());
            }
            
            sendJsonResponse(exchange, 200, inputFields);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error extracting inputs from BPMN: " + e.getMessage());
            // Return default inputs on error
            List<InputField> defaultFields = Arrays.asList(
                new InputField("manualPriceCost", "boolean", "Manual Price Cost", false, true),
                new InputField("dealMarginPercent", "number", "Deal Margin Percent", 25.0, true)
            );
            sendJsonResponse(exchange, 200, defaultFields);
        }
    }
    
    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, String> error = new HashMap<>();
        error.put("error", message);
        
        String json = objectMapper.writeValueAsString(error);
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}

