package com.cinebook.web;

import com.cinebook.model.Show;
import com.cinebook.service.Theatre;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller handling the home page and show listing.
 */
@Controller
public class HomeController {

    private final Theatre theatre;

    public HomeController(Theatre theatre) {
        this.theatre = theatre;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Show> shows = theatre.getAllShows();
        model.addAttribute("shows", shows);
        return "index";
    }
}

