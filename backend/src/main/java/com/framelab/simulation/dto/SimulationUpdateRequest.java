package com.framelab.simulation.dto;

public record SimulationUpdateRequest(SimulationDelaysView delays, SimulationFailureView failure) {
}
