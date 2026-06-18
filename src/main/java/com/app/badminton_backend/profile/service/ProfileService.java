package com.app.badminton_backend.profile.service;


import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.profile.dtos.ProfileCreateDto;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProfileService {

    private final CurrentUserService currentUserService;
    private final ProfileRepository profileRepository;
    private final ModelMapper modelMapper;
    private final EloPointsRepository eloPointsRepository;

    public Profile findByUserId(UUID userId){
        return profileRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Profile not found for this user"));
    }

    public void createProfile(ProfileCreateDto profileCreateDto){
            UUID userId = currentUserService.getCurrentUser().getId();
            Profile profile = modelMapper.map(profileCreateDto,Profile.class);
            profile.setId(userId);
            Profile savedProfile = profileRepository.save(profile);
            EloPoints eloPoints = EloPoints.builder()
                    .id(userId)
                    .build();
            EloPoints savedElo = eloPointsRepository.save(eloPoints);
    }
}
