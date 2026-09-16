package de.bht.modulebook.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;

@Controller
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "redirect:/index.html";
    }
}
