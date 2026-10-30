package com.framelab.simulation;

import java.util.ArrayList;
import java.util.List;

import com.framelab.common.error.ApiException;
import com.framelab.common.error.ErrorCode;
import com.framelab.common.error.FieldErrorView;
import com.framelab.common.i18n.MessageResolver;
import com.framelab.common.time.TimeFormat;
import com.framelab.simulation.dto.SimulationDelaysView;
import com.framelab.simulation.dto.SimulationFailureView;
import com.framelab.simulation.dto.SimulationUpdateRequest;
import com.framelab.simulation.dto.SimulationView;
import com.framelab.task.domain.TaskStage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {

    private static final int MAX_DELAY_MS = 60_000;
    private static final int MAX_FAIL_TIMES = 50;

    private final SimulationMapper simulationMapper;
    private final MessageResolver messages;

    public SimulationService(SimulationMapper simulationMapper, MessageResolver messages) {
        this.simulationMapper = simulationMapper;
        this.messages = messages;
    }

    public SimulationView get() {
        return view(requireRow());
    }

    @Transactional
    public SimulationView update(long actorId, SimulationUpdateRequest request) {
        validate(request);
        SimulationDelaysView delays = request.delays();
        SimulationFailureView failure = request.failure();
        boolean inject = failure.remaining() > 0;
        SimulationRow row = new SimulationRow();
        row.setMaterialCheckDelayMs(delays.materialCheckMs());
        row.setGenerateDelayMs(delays.generateMs());
        row.setFinalizeDelayMs(delays.finalizeMs());
        row.setFailStage(inject ? TaskStage.valueOf(failure.stage()) : null);
        row.setFailTimes(failure.remaining());
        row.setFailRetryable(inject && failure.retryable());
        row.setUpdatedBy(actorId);
        simulationMapper.update(row);
        return view(requireRow());
    }

    public SimulationRow current() {
        return requireRow();
    }

    @Transactional
    public FailureInjection consumeFailure(TaskStage stage) {
        SimulationRow row = simulationMapper.lock();
        if (row == null || row.getFailStage() != stage || row.getFailTimes() <= 0) {
            return null;
        }
        boolean retryable = row.isFailRetryable();
        simulationMapper.consume(row.getFailTimes() == 1);
        return new FailureInjection(retryable);
    }

    private void validate(SimulationUpdateRequest request) {
        List<FieldErrorView> errors = new ArrayList<>();
        if (request == null || request.delays() == null) {
            errors.add(new FieldErrorView("delays", messages.get("validation.simulation.delay")));
        } else {
            SimulationDelaysView delays = request.delays();
            checkDelay("delays.materialCheckMs", delays.materialCheckMs(), errors);
            checkDelay("delays.generateMs", delays.generateMs(), errors);
            checkDelay("delays.finalizeMs", delays.finalizeMs(), errors);
        }
        if (request == null || request.failure() == null) {
            errors.add(new FieldErrorView("failure", messages.get("validation.simulation.failure")));
        } else {
            SimulationFailureView failure = request.failure();
            if (failure.remaining() < 0 || failure.remaining() > MAX_FAIL_TIMES) {
                errors.add(new FieldErrorView("failure.remaining",
                        messages.get("validation.simulation.remaining")));
            }
            if (failure.stage() != null) {
                try {
                    TaskStage.valueOf(failure.stage());
                } catch (IllegalArgumentException ex) {
                    errors.add(new FieldErrorView("failure.stage",
                            messages.get("validation.simulation.stage")));
                }
            } else if (failure.remaining() > 0) {
                errors.add(new FieldErrorView("failure.stage",
                        messages.get("validation.simulation.stage")));
            }
            if (failure.retryable() && failure.remaining() == 0) {
                errors.add(new FieldErrorView("failure.retryable",
                        messages.get("validation.simulation.retryable")));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private void checkDelay(String field, int value, List<FieldErrorView> errors) {
        if (value < 0 || value > MAX_DELAY_MS) {
            errors.add(new FieldErrorView(field, messages.get("validation.simulation.delay")));
        }
    }

    private SimulationRow requireRow() {
        SimulationRow row = simulationMapper.find();
        if (row == null) {
            throw ApiException.of(ErrorCode.INTERNAL_ERROR);
        }
        return row;
    }

    private SimulationView view(SimulationRow row) {
        return new SimulationView(
                new SimulationDelaysView(row.getMaterialCheckDelayMs(), row.getGenerateDelayMs(),
                        row.getFinalizeDelayMs()),
                new SimulationFailureView(
                        row.getFailStage() == null ? null : row.getFailStage().name(),
                        row.getFailTimes(),
                        row.isFailRetryable()),
                TimeFormat.toInstant(row.getUpdatedAt()),
                row.getUpdatedBy());
    }
}
