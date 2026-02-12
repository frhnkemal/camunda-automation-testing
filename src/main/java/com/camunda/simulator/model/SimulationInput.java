package com.camunda.simulator.model;

/**
 * Input parameters for process simulation.
 */
public class SimulationInput {
    private Boolean manualPriceCost;
    private Double dealMarginPercent;
    
    public SimulationInput() {
    }
    
    public SimulationInput(Boolean manualPriceCost, Double dealMarginPercent) {
        this.manualPriceCost = manualPriceCost;
        this.dealMarginPercent = dealMarginPercent;
    }
    
    public Boolean getManualPriceCost() {
        return manualPriceCost;
    }
    
    public void setManualPriceCost(Boolean manualPriceCost) {
        this.manualPriceCost = manualPriceCost;
    }
    
    public Double getDealMarginPercent() {
        return dealMarginPercent;
    }
    
    public void setDealMarginPercent(Double dealMarginPercent) {
        this.dealMarginPercent = dealMarginPercent;
    }
}

