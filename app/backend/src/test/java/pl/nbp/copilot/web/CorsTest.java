package pl.nbp.copilot.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS integration tests.
 * Verifies that the configured SPA origin is allowed and other origins are rejected.
 * ADR-001 §6; ADR-000 §8; TAC-001-06; TAC-010.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflight_allowedOrigin_isPermitted() throws Exception {
        mockMvc.perform(options("/api/metadata")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void preflight_otherOrigin_isRejected() throws Exception {
        // CORS: an origin other than the configured one must NOT receive Access-Control-Allow-Origin
        mockMvc.perform(options("/api/metadata")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void preflight_allowedOriginForCases_isPermitted() throws Exception {
        mockMvc.perform(options("/api/cases")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
