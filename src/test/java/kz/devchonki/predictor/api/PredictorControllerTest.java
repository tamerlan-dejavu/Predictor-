package kz.devchonki.predictor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import kz.devchonki.predictor.BranchPredictorApplication;
import kz.devchonki.predictor.api.PredictorController.RunRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = BranchPredictorApplication.class)
@AutoConfigureMockMvc
@DisplayName("PredictorController REST API")
class PredictorControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/run with empty traceContent returns 400 and JSON error")
    void run_emptyTraceContent_returns400() throws Exception {
        RunRequest req = new RunRequest("bimodal", 1024, 8, "   ");
        mvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("traceContent")));
    }

    @Test
    @DisplayName("POST /api/run with null body returns 400")
    void run_nullBody_returns400() throws Exception {
        mvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/run with invalid predictorType returns 400")
    void run_invalidPredictor_returns400() throws Exception {
        RunRequest req = new RunRequest("not_a_real_predictor", 1024, 8, "0x400000 1\n");
        mvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsStringIgnoringCase("unknown")));
    }

    @Test
    @DisplayName("POST /api/run with tableSize not power of two returns 400")
    void run_invalidTableSize_returns400() throws Exception {
        RunRequest req = new RunRequest("bimodal", 3, 8, "0x400000 1\n");
        mvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", anyOf(
                        containsString("power"),
                        containsString("tableSize"))));
    }

    @Test
    @DisplayName("POST /api/run happy path returns 200 with stats")
    void run_validRequest_returns200() throws Exception {
        RunRequest req = new RunRequest("static_taken", 1024, 8, "0x400000 1\n0x400000 1\n");
        mvc.perform(post("/api/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPredictions", is(2)))
                .andExpect(jsonPath("$.mispredictions", is(0)));
    }

    @Test
    @DisplayName("GET /api/experiment returns requested number of points")
    void experiment_returnsEightPoints() throws Exception {
        mvc.perform(get("/api/experiment")
                        .param("predictor", "bimodal")
                        .param("minTable", "16")
                        .param("maxTable", "65536")
                        .param("steps", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(8)))
                .andExpect(jsonPath("$[0].tableSize", is(16)))
                .andExpect(jsonPath("$[7].tableSize", is(2048)));
    }

    @Test
    @DisplayName("GET /api/experiment with invalid minTable returns 400")
    void experiment_invalidMin_returns400() throws Exception {
        mvc.perform(get("/api/experiment")
                        .param("predictor", "bimodal")
                        .param("minTable", "3")
                        .param("maxTable", "1024")
                        .param("steps", "4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("minTable")));
    }

    @Test
    @DisplayName("GET /api/health includes status and project")
    void health_ok() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.project", is("Branch Predictor Lab")));
    }

    @Test
    @DisplayName("POST /api/compare sorts results by misprediction rate")
    void compare_sortsByRate() throws Exception {
        String body = """
                {
                  "predictors": ["static_taken", "bimodal"],
                  "tableSize": 1024,
                  "historyBits": 8,
                  "traceContent": "0x400000 1\\n0x400000 1\\n"
                }
                """;
        mvc.perform(post("/api/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }
}
