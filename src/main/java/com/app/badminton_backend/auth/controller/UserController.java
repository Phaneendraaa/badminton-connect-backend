package com.app.badminton_backend.auth.controller;


import com.app.badminton_backend.auth.dto.UserSearchDtoRequest;
import com.app.badminton_backend.auth.dto.UserSearchDtoResponse;
import com.app.badminton_backend.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/search")
    public ResponseEntity searchUser(@Valid @RequestBody UserSearchDtoRequest userSearchDtoRequest){
           UserSearchDtoResponse userSearchDtoResponse = userService.searchUser(userSearchDtoRequest.getPhoneNumber());
        System.out.println(userSearchDtoResponse);
           return ResponseEntity.status(HttpStatus.OK).body(userSearchDtoResponse);
    }

}
