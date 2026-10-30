package com.framelab.common.error;

import java.net.URI;
import java.util.List;

import com.framelab.common.i18n.MessageResolver;
import com.framelab.common.util.Uuids;
import com.framelab.common.web.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemFactory {

    private final MessageResolver messages;

    public ProblemFactory(MessageResolver messages) {
        this.messages = messages;
    }

    public ProblemDetail create(ErrorCode code, Object[] args, List<FieldErrorView> fieldErrors,
                                HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), messages.get(code.detailKey(), args));
        problem.setTitle(messages.get(code.titleKey()));
        problem.setType(URI.create("https://framelab.local/problems/" + code.typeSlug()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", traceId(request));
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setProperty("errors", fieldErrors);
        }
        return problem;
    }

    public String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestAttributes.TRACE_ID);
        return value instanceof String traceId && !traceId.isBlank() ? traceId : Uuids.newTraceId();
    }
}
