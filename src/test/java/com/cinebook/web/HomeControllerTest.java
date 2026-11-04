package com.cinebook.web;

import com.cinebook.model.Show;
import com.cinebook.service.Theatre;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Theatre theatre;

    @Test
    @DisplayName("GET / returns index page with show list")
    void testHomePage() throws Exception {
        Show s1 = new Show(1L, "Interstellar", LocalTime.of(19, 30));
        Show s2 = new Show(2L, "Inception", LocalTime.of(16, 0));
        Show s3 = new Show(3L, "The Dark Knight", LocalTime.of(22, 0));

        when(theatre.getAllShows()).thenReturn(List.of(s1, s2, s3));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("shows"));
    }
}

