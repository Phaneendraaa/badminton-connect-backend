package com.app.badminton_backend.auth.service;

import com.app.badminton_backend.auth.dto.UserSearchDtoResponse;
import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.repository.UserRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.service.ProfileService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {

    private final ProfileService profileService;

    private final UserRepository userRepository;
    public UserSearchDtoResponse searchUser(String phoneNumber){
        System.out.println("recieved to search "+phoneNumber);
        UUID userId =  userRepository.findByPhoneNumber(phoneNumber).getId();
        System.out.println("found user->"+userId);
        if(userId==null){
            throw new EntityNotFoundException("User with this Mobile Number doesnt exist");
        }
        Profile profile = profileService.findByUserId(userId);
        System.out.println("found profile->"+profile);
        UserSearchDtoResponse userSearchDtoResponse = UserSearchDtoResponse.builder()
                .userId(userId)
                .name(profile.getFirstName()+" "+profile.getLastName())
                .phoneNumber(phoneNumber)
                .profilePictureUrl(profile.getProfilePictureUrl())
                .build();
        System.out.println(userSearchDtoResponse);
        return userSearchDtoResponse;
    }
}
