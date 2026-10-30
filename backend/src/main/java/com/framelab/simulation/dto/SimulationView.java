package com.framelab.simulation.dto;

import java.time.Instant;

public record SimulationView(SimulationDelaysView delays,
                             SimulationFailureView failure,
                             Instant updatedAt,
                             Long updatedBy) {
}
