package com.camunda.simulator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates simulation input JSON: ensures dealMarginPercent is a number
 * and manualPriceCost is a boolean. Used to reject non-numerical and non-boolean inputs.
 */
public class InputValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Validates the given JSON body for /api/simulate.
     *
     * @param jsonBody raw JSON string
     * @return list of error messages (empty if valid)
     */
    public static List<String> validate(String jsonBody) {
        List<String> errors = new ArrayList<>();
        if (jsonBody == null || jsonBody.isBlank()) {
            errors.add("Request body is required");
            return errors;
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(jsonBody);
        } catch (Exception e) {
            errors.add("Invalid JSON: " + e.getMessage());
            return errors;
        }

        if (root == null || !root.isObject()) {
            errors.add("Request body must be a JSON object");
            return errors;
        }

        // manualPriceCost must be present and boolean
        JsonNode manualPriceCost = root.get("manualPriceCost");
        if (manualPriceCost == null || manualPriceCost.isNull()) {
            errors.add("manualPriceCost is required");
        } else if (!manualPriceCost.isBoolean()) {
            errors.add("manualPriceCost must be a boolean (true or false)");
        }

        // dealMarginPercent must be present and a number
        JsonNode dealMarginPercent = root.get("dealMarginPercent");
        if (dealMarginPercent == null || dealMarginPercent.isNull()) {
            errors.add("dealMarginPercent is required");
        } else if (!dealMarginPercent.isNumber()) {
            errors.add("dealMarginPercent must be a number");
        } else {
            double value = dealMarginPercent.asDouble();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                errors.add("dealMarginPercent must be a finite number");
            }
        }

        return errors;
    }

    /**
     * Returns the first validation error message, or null if valid.
     */
    public static String validateAndGetFirstError(String jsonBody) {
        List<String> errors = validate(jsonBody);
        return errors.isEmpty() ? null : errors.get(0);
    }
}
