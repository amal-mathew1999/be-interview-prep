package com.example.mockretest.booking;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mockretest.booking.dto.BookingHoldRequest;
import com.example.mockretest.booking.dto.BookingPatientRequest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void returns409ProblemWhenConfirmLosesConcurrentUpdate() throws Exception {
        given(bookingService.confirm(eq(7L), any(BookingPatientRequest.class)))
                .willThrow(new ObjectOptimisticLockingFailureException(BookingSlotLock.class, 1L));

        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"p1\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/api/bookings/7/confirm"));
    }

    @Test
    void returns409ProblemWhenSlotUnavailable() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 1, 15, 9, 0);
        given(bookingService.hold(any(BookingHoldRequest.class)))
                .willThrow(new BookingSlotUnavailableException(1L, start));

        mockMvc.perform(post("/api/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\":1,\"patientId\":\"p1\",\"start\":\"2030-01-15T09:00:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem-type:booking:slot-unavailable"))
                .andExpect(jsonPath("$.title").value("Slot unavailable"));
    }

    @Test
    void returns500ProblemWithoutInternalDetails() throws Exception {
        given(bookingService.confirm(eq(7L), any(BookingPatientRequest.class)))
                .willThrow(new IllegalStateException("secret internal failure"));

        mockMvc.perform(post("/api/bookings/7/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":\"p1\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))));
    }

    @Test
    void returns400ProblemForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/bookings/hold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.instance").value("/api/bookings/hold"));
    }
}
