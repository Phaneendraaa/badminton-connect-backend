package com.app.badminton_backend.auth.controller;

import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.service.CurrentUserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/current")
@AllArgsConstructor
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    @GetMapping("/user")
    public User getCurrentUser() {
        return currentUserService.getCurrentUser();
    }
}
