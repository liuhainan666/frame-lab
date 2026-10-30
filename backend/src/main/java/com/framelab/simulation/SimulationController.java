package com.framelab.simulation;

import com.framelab.common.security.SecurityUtils;
import com.framelab.simulation.dto.SimulationUpdateRequest;
import com.framelab.simulation.dto.SimulationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/simulation")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping
    public SimulationView get() {
        return simulationService.get();
    }

    @PutMapping
    public SimulationView update(@RequestBody SimulationUpdateRequest request) {
        return simulationService.update(SecurityUtils.currentUser().id(), request);
    }
}
