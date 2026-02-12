# Camunda Design-Time Process Simulator MVP

A design-time simulator for validating Camunda workflows before deployment, focusing on the Entry Level Camunda Exercise process.

## Overview

This MVP allows Business Analysts and Test Engineers to:
- Validate BPMN/DMN logic **before deployment**
- Test different input scenarios without needing the full Telflow environment
- Catch defects early (wrong variables, gateway conditions, DMN logic)

## Process Flow

The Entry Level Camunda Exercise process:
1. **Start** → Accept inputs: `manualPriceCost` (boolean) and `dealMarginPercent` (number)
2. **Prepare Values for DMN** → Mock service task that maps inputs to process variables
3. **Look-up Results** → DMN decision `entry_level_camunda_exercise_v1_0` evaluates `quoteValidity`
4. **Result / Decision Gateway** → Routes based on `quoteValidity`:
   - Default flow → `Set Status Valid` (cim_Status = "Valid")
   - Conditional flow (quoteValidity = "Invalid") → `Set Status Invalid` (cim_Status = "Invalid")
5. **End**

## DMN Logic

The decision `entry_level_camunda_exercise_v1_0`:
- If **margin < 25% OR manualPriceCost = true** → `quoteValidity = "Invalid"`
- Otherwise → `quoteValidity = "Valid"`

## Test Scenarios

1. `manualPriceCost = true`, `dealMarginPercent = 30` → **Invalid**
2. `manualPriceCost = false`, `dealMarginPercent = 24` → **Invalid**
3. `manualPriceCost = false`, `dealMarginPercent = 25` → **Valid**

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Setup

```bash
# Build the project
mvn clean install

# Run the simulator
mvn exec:java
```

Or compile and run directly:

```bash
# Compile
mvn compile

# Run (Linux/Mac)
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout) com.camunda.simulator.SimulatorApplication

# Run (Windows - PowerShell)
$classpath = mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout
java -cp "target/classes;$classpath" com.camunda.simulator.SimulatorApplication
```

The application will start on `http://localhost:8080`

## Usage

### Web UI

Open your browser and navigate to:
```
http://localhost:8080
```

The web interface allows you to:
- **Upload BPMN and DMN files** to test your own process definitions
- Run pre-defined test scenarios
- Create custom simulations with different input values
- View execution paths and process variables

### Uploading BPMN and DMN Files

1. **Upload BPMN File**: Click "Choose File" under "BPMN File", select your `.bpmn` file, and click "Upload BPMN"
2. **Upload DMN File**: Click "Choose File" under "DMN File", select your `.dmn` file, and click "Upload DMN"
3. The simulator will automatically parse and use the uploaded files for simulations
4. If no files are uploaded, the simulator falls back to hardcoded default logic

### API Endpoints

#### Simulate Process

```bash
POST http://localhost:8080/api/simulate
Content-Type: application/json

{
  "manualPriceCost": false,
  "dealMarginPercent": 25
}
```

**Response:**

```json
{
  "inputs": {
    "manualPriceCost": false,
    "dealMarginPercent": 25.0
  },
  "dmnResult": {
    "quoteValidity": "Valid"
  },
  "executionPath": [
    "Start",
    "Prepare Values for DMN",
    "Look-up Results",
    "Result / Decision Gateway",
    "Set Status Valid",
    "End"
  ],
  "finalStatus": "Valid",
  "processVariables": {
    "bi_dealMarginPercent": 25.0,
    "bi_manualPriceCost": false,
    "quoteValidity": "Valid",
    "cim_Status": "Valid"
  }
}
```

#### Get Test Scenarios

```bash
GET http://localhost:8080/api/scenarios
```

#### Run Pre-defined Scenario

```bash
POST http://localhost:8080/api/scenarios/{scenarioName}/run
```

Available scenarios:
- `manual-pricing-invalid`
- `low-margin-invalid`
- `valid-scenario`
- `high-margin-valid`

#### Upload BPMN File

```bash
POST http://localhost:8080/api/upload/bpmn
Content-Type: multipart/form-data

[file content]
```

**Response:**
```json
{
  "success": true,
  "message": "BPMN file uploaded successfully",
  "filename": "process.bpmn",
  "size": 12345
}
```

#### Upload DMN File

```bash
POST http://localhost:8080/api/upload/dmn
Content-Type: multipart/form-data

[file content]
```

**Response:**
```json
{
  "success": true,
  "message": "DMN file uploaded successfully",
  "filename": "decision.dmn",
  "size": 6789
}
```

#### Get Uploaded Files Status

```bash
GET http://localhost:8080/api/files
```

**Response:**
```json
{
  "bpmnFiles": ["process.bpmn"],
  "dmnFiles": ["decision.dmn"],
  "hasBpmn": true,
  "hasDmn": true,
  "dmnLoaded": true
}
```

## Running Tests

```bash
mvn test
```

## Project Structure

```
.
├── pom.xml                                    # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/com/camunda/simulator/
│   │   │   ├── SimulatorApplication.java     # Main application (Java HTTP Server)
│   │   │   ├── http/
│   │   │   │   ├── SimulatorHttpHandler.java # REST API handler
│   │   │   │   └── StaticFileHandler.java    # Static file handler
│   │   │   ├── service/
│   │   │   │   ├── ProcessEngine.java        # Process execution engine
│   │   │   │   └── DMNEvaluator.java         # DMN decision evaluator
│   │   │   └── model/
│   │   │       ├── SimulationInput.java      # Input model
│   │   │       ├── SimulationResult.java     # Result model
│   │   │       ├── DMNResult.java            # DMN result model
│   │   │       └── TestScenario.java         # Test scenario model
│   │   └── resources/
│   │       └── static/
│   │           └── index.html                # Web UI
│   └── test/
│       └── java/com/camunda/simulator/
│           └── ProcessEngineTest.java        # Unit tests
└── README.md
```

## Technology Stack

- **Java 17** - Programming language
- **Java HTTP Server** (com.sun.net.httpserver) - Built-in web server (no external framework)
- **Jackson** - JSON processing
- **Maven** - Build and dependency management
- **JUnit 5** - Testing framework

No Spring framework dependencies - pure Java implementation!

