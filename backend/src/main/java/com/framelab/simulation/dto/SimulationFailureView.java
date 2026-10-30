package com.framelab.simulation.dto;

public record SimulationFailureView(String stage, int remaining, boolean retryable) {
}
