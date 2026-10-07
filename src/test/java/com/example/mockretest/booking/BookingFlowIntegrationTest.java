package com.example.mockretest.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class BookingFlowIntegrationTest {

    private static final String DAY = "2030-01-15";
    private static final String NINE = DAY + "T09:00:00";

    private static final Instant START = Instant.parse("2030-01-14T12:00:00Z");

    @TestBean(name = "bookingClock", methodName = "controllableClock")
    private BookingClock bookingClock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    private long doctorId;

    static BookingClock controllableClock() {
        return new BookingClock(new BookingTestClock(START));
    }

    @BeforeEach
    void resetClockAndCreateDoctor() throws Exception {
        clock().set(START);
        doctorId = createDoctor("Dr. Who");
    }

    @Test
    void createsDoctorAndReturns201WithLocation() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dr. House\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Dr. House"));
    }

    @Test
    void returns400WhenDoctorNameBlank() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void listsAllWorkingHourSlotsForFreeDoctor() throws Exception {
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0]").value(NINE))
                .andExpect(jsonPath("$[15]").value(DAY + "T16:30:00"));
    }

    @Test
    void returns404ProblemForUnknownDoctorSlots() throws Exception {
        expectProblem(mockMvc.perform(get("/api/doctors/{id}/slots", 999_999).param("date", DAY)), 404);
    }

    @Test
    void returns400ProblemWhenDateMissing() throws Exception {
        expectProblem(mockMvc.perform(get("/api/doctors/{id}/slots", doctorId)), 400);
    }

    @Test
    void returns400ProblemWhenDateInvalid() throws Exception {
        expectProblem(mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", "15-01-2030")), 400);
    }

    @Test
    void holdReturns201WithHeldStatusAndExpiry() throws Exception {
        mockMvc.perform(hold(doctorId, "p1", NINE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNumber())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.start").value(NINE))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-14T12:05:00Z"));
    }

    @Test
    void expiryIsComputedFromCurrentClockEvenAfterOtherTestsAdvancedIt() throws Exception {
        clock().advance(Duration.ofHours(2));

        mockMvc.perform(hold(doctorId, "p1", NINE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2030-01-14T14:05:00Z"));
    }

    @Test
    void bookingIsNotReadableByIdAndHoldDoesNotLeakPatientId() throws Exception {
        String body = mockMvc.perform(hold(doctorId, "secret-patient", NINE))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.patientId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long bookingId = ((Number) JsonPath.read(body, "$.bookingId")).longValue();

        mockMvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString("secret-patient"))));
    }

    @Test
    void confirmAndCancelResponsesDoNotExposePatientId() throws Exception {
        long bookingId = holdSlot("p1", NINE);

        confirm(bookingId, "p1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").doesNotExist());
        cancel(bookingId, "p1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @Test
    void confirmedSlotStaysUnavailableAfterHoldTimeoutPasses() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());

        clock().advance(Duration.ofHours(1));

        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$", not(hasItem(NINE))));
    }

    @Test
    void holdReturns400WhenSlotIsInThePast() throws Exception {
        expectProblem(mockMvc.perform(hold(doctorId, "p1", "2030-01-14T09:00:00")), 400)
                .andExpect(jsonPath("$.type").value("urn:problem-type:booking:slot-in-past"));
        expectProblem(mockMvc.perform(hold(doctorId, "p1", "2030-01-14T11:30:00")), 400);
        mockMvc.perform(hold(doctorId, "p1", "2030-01-14T12:00:00")).andExpect(status().isCreated());
    }

    @Test
    void listingOmitsSlotsThatAlreadyStarted() throws Exception {
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", "2030-01-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0]").value("2030-01-14T12:00:00"));
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", "2030-01-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void heldSlotIsNotListedAsAvailable() throws Exception {
        mockMvc.perform(hold(doctorId, "p1", NINE)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$", not(hasItem(NINE))));
    }

    @Test
    void holdReturns400WhenStartNotAligned() throws Exception {
        expectProblem(mockMvc.perform(hold(doctorId, "p1", DAY + "T09:15:00")), 400);
    }

    @Test
    void holdReturns400WhenStartOutsideWorkingHours() throws Exception {
        expectProblem(mockMvc.perform(hold(doctorId, "p1", DAY + "T17:00:00")), 400);
        expectProblem(mockMvc.perform(hold(doctorId, "p1", DAY + "T08:30:00")), 400);
    }

    @Test
    void holdReturns400WhenBodyInvalid() throws Exception {
        expectProblem(
                        mockMvc.perform(post("/api/bookings/hold")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"doctorId\":" + doctorId + "}")),
                        400)
                .andExpect(jsonPath("$.errors.patientId").exists())
                .andExpect(jsonPath("$.errors.start").exists());
    }

    @Test
    void holdReturns404WhenDoctorUnknown() throws Exception {
        expectProblem(mockMvc.perform(hold(999_999, "p1", NINE)), 404);
    }

    @Test
    void holdReturns409WhenSlotAlreadyHeld() throws Exception {
        mockMvc.perform(hold(doctorId, "p1", NINE)).andExpect(status().isCreated());

        expectProblem(mockMvc.perform(hold(doctorId, "p2", NINE)), 409);
    }

    @Test
    void holdReturns409WhenSlotConfirmed() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());

        clock().advance(Duration.ofHours(1));

        expectProblem(mockMvc.perform(hold(doctorId, "p2", NINE)), 409);
    }

    @Test
    void confirmReturns200WithConfirmedStatus() throws Exception {
        long bookingId = holdSlot("p1", NINE);

        confirm(bookingId, "p1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmReturns403ForWrongPatient() throws Exception {
        long bookingId = holdSlot("p1", NINE);

        expectProblem(confirm(bookingId, "intruder"), 403);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
    }

    @Test
    void confirmReturns409WhenAlreadyConfirmed() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());

        expectProblem(confirm(bookingId, "p1"), 409);
    }

    @Test
    void confirmReturns404WhenBookingUnknown() throws Exception {
        expectProblem(confirm(999_999, "p1"), 404);
    }

    @Test
    void confirmingExpiredHoldReturns409AndDoesNotConfirm() throws Exception {
        long bookingId = holdSlot("p1", NINE);

        clock().advance(Duration.ofMinutes(5));

        expectProblem(confirm(bookingId, "p1"), 409);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isNotEqualTo(BookingStatus.CONFIRMED);
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(jsonPath("$", hasItem(NINE)));
    }

    @Test
    void expiredHoldFreesSlotForAnotherPatientWithoutCleanup() throws Exception {
        long first = holdSlot("p1", NINE);

        clock().advance(Duration.ofMinutes(4));
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(jsonPath("$", not(hasItem(NINE))));
        expectProblem(mockMvc.perform(hold(doctorId, "p2", NINE)), 409);

        clock().advance(Duration.ofMinutes(1));
        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(jsonPath("$", hasItem(NINE)));

        long second = holdSlot("p2", NINE);
        confirm(second, "p2").andExpect(status().isOk());
        expectProblem(confirm(first, "p1"), 409);
    }

    @Test
    void cancelConfirmedBookingFreesSlotAndAllowsNewHold() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());

        cancel(bookingId, "p1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/doctors/{id}/slots", doctorId).param("date", DAY))
                .andExpect(jsonPath("$", hasItem(NINE)));
        mockMvc.perform(hold(doctorId, "p2", NINE)).andExpect(status().isCreated());
    }

    @Test
    void cancelReturns403ForWrongPatient() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());

        expectProblem(cancel(bookingId, "intruder"), 403);
    }

    @Test
    void cancelReturns409WhenAlreadyCancelled() throws Exception {
        long bookingId = holdSlot("p1", NINE);
        confirm(bookingId, "p1").andExpect(status().isOk());
        cancel(bookingId, "p1").andExpect(status().isOk());

        expectProblem(cancel(bookingId, "p1"), 409);
    }

    private BookingTestClock clock() {
        return (BookingTestClock) bookingClock.delegate();
    }

    private long createDoctor(String name) throws Exception {
        String body = mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long holdSlot(String patientId, String start) throws Exception {
        String body = mockMvc.perform(hold(doctorId, patientId, start))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(body, "$.bookingId")).longValue();
    }

    private static RequestBuilder hold(long doctor, String patient, String start) {
        return post("/api/bookings/hold")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":" + doctor + ",\"patientId\":\"" + patient + "\",\"start\":\"" + start + "\"}");
    }

    private ResultActions confirm(long bookingId, String patientId) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/confirm", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patientId + "\"}"));
    }

    private ResultActions cancel(long bookingId, String patientId) throws Exception {
        return mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + patientId + "\"}"));
    }

    private static ResultActions expectProblem(ResultActions actions, int status) throws Exception {
        return actions.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").exists());
    }
}
