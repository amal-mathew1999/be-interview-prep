package com.example.mockretest.booking;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.mockretest.booking.dto.BookingDoctorCreateRequest;
import com.example.mockretest.booking.dto.BookingDoctorResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingDoctorController.class)
class BookingDoctorControllerTest {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 15);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingDoctorService doctorService;

    @Test
    void createsDoctorAndReturns201WithLocation() throws Exception {
        given(doctorService.create(any(BookingDoctorCreateRequest.class)))
                .willReturn(new BookingDoctorResponse(42L, "Dr. Grey"));

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dr. Grey\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/doctors/42")))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Dr. Grey"));
    }

    @Test
    void returns400ProblemWhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(doctorService);
    }

    @Test
    void listsAvailableSlots() throws Exception {
        given(doctorService.availableSlots(7L, DAY)).willReturn(List.of(DAY.atTime(9, 0), DAY.atTime(10, 30)));

        mockMvc.perform(get("/api/doctors/7/slots").param("date", "2030-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("2030-01-15T09:00:00"))
                .andExpect(jsonPath("$[1]").value("2030-01-15T10:30:00"));
    }

    @Test
    void returns404ProblemForUnknownDoctor() throws Exception {
        given(doctorService.availableSlots(eq(9L), any(LocalDate.class)))
                .willThrow(new BookingDoctorNotFoundException(9L));

        mockMvc.perform(get("/api/doctors/9/slots").param("date", "2030-01-15"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:problem-type:booking:doctor-not-found"))
                .andExpect(jsonPath("$.instance").value("/api/doctors/9/slots"));
    }

    @Test
    void returns400ProblemWhenDateMissingOrInvalid() throws Exception {
        mockMvc.perform(get("/api/doctors/7/slots"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/doctors/7/slots").param("date", "2030-13-45"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(doctorService);
    }

    @Test
    void returnsDoctorById() throws Exception {
        given(doctorService.find(3L)).willReturn(new BookingDoctorResponse(3L, "Dr. Who"));

        mockMvc.perform(get("/api/doctors/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dr. Who"));
    }
}
