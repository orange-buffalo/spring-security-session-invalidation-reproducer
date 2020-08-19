package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;

@RestController
public class ApiController {

    @GetMapping
    public String execute(WebSession session) {
        Object currentValue = session.getAttribute("sessionKey");

        session.getAttributes().put("sessionKey", new Object());

        return currentValue == null ? "empty" : "value";
    }
}
