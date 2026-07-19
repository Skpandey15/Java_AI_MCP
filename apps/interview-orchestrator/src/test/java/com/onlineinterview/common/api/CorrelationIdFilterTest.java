package com.onlineinterview.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesSafeRequestIdAndClearsMdcAfterRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "request-123");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) ->
                assertThat(MDC.get("requestId")).isEqualTo("request-123"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("request-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "unsafe request id");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> { });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .isNotBlank()
                .isNotEqualTo("unsafe request id");
    }
}
