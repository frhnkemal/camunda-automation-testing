package com.camunda.simulator;

import com.camunda.simulator.service.InputValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    @Test
    void validInputReturnsNoErrors() {
        String json = "{\"manualPriceCost\": false, \"dealMarginPercent\": 25.0}";
        List<String> errors = InputValidator.validate(json);
        assertTrue(errors.isEmpty());
    }

    @Test
    void dealMarginPercentAsStringIsRejected() {
        String json = "{\"manualPriceCost\": false, \"dealMarginPercent\": \"abc\"}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("number")),
            "Expected error message to mention 'number': " + errors);
    }

    @Test
    void dealMarginPercentAsBooleanIsRejected() {
        String json = "{\"manualPriceCost\": false, \"dealMarginPercent\": true}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("number")),
            "Expected error message to mention 'number': " + errors);
    }

    @Test
    void manualPriceCostAsStringIsRejected() {
        String json = "{\"manualPriceCost\": \"yes\", \"dealMarginPercent\": 25}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("boolean")),
            "Expected error message to mention 'boolean': " + errors);
    }

    @Test
    void manualPriceCostAsNumberIsRejected() {
        String json = "{\"manualPriceCost\": 1, \"dealMarginPercent\": 25}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("boolean")),
            "Expected error message to mention 'boolean': " + errors);
    }

    @Test
    void missingManualPriceCostIsRejected() {
        String json = "{\"dealMarginPercent\": 25}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("manualpricecost")),
            "Expected error message to mention 'manualPriceCost': " + errors);
    }

    @Test
    void missingDealMarginPercentIsRejected() {
        String json = "{\"manualPriceCost\": false}";
        List<String> errors = InputValidator.validate(json);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("dealmarginpercent")),
            "Expected error message to mention 'dealMarginPercent': " + errors);
    }

    @Test
    void validateAndGetFirstErrorReturnsNullForValid() {
        String json = "{\"manualPriceCost\": true, \"dealMarginPercent\": 30}";
        assertNull(InputValidator.validateAndGetFirstError(json));
    }

    @Test
    void validateAndGetFirstErrorReturnsMessageForInvalid() {
        String json = "{\"manualPriceCost\": false, \"dealMarginPercent\": \"x\"}";
        String error = InputValidator.validateAndGetFirstError(json);
        assertNotNull(error);
        assertTrue(error.toLowerCase().contains("number"));
    }

    @Test
    void emptyBodyIsRejected() {
        List<String> errors = InputValidator.validate("");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("required") || e.toLowerCase().contains("body")));
    }

    @Test
    void invalidJsonIsRejected() {
        List<String> errors = InputValidator.validate("not json at all");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.toLowerCase().contains("json") || e.toLowerCase().contains("invalid")));
    }
}
