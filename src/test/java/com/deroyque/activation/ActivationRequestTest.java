package com.deroyque.activation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ActivationRequestTest {
    @Test
    void completedRequestsCannotBeProcessedAgain() {
        ActivationRequest request = new ActivationRequest("EXT-1", "CUST-1", "INTERNET-500");
        assertThatThrownBy(request::complete).isInstanceOf(IllegalStateException.class);
        request.startProcessing();
        request.complete();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThatThrownBy(request::startProcessing).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::fail).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedRequestsAreTerminal() {
        ActivationRequest request = new ActivationRequest("EXT-1", "CUST-1", "INTERNET-500");
        request.startProcessing();
        request.fail();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.FAILED);
        assertThatThrownBy(request::startProcessing).isInstanceOf(IllegalStateException.class);
    }
}
