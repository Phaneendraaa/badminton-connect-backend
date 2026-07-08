package com.app.badminton_backend.profile.service;


import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.profile.dtos.ProfileCreateDto;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.entity.PlayerTrustScore;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ProfileService {

    private final CurrentUserService currentUserService;
    private final ProfileRepository profileRepository;
    private final ModelMapper modelMapper;
    private final EloPointsRepository eloPointsRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final PlayerTrustScoreService playerTrustScoreService;

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

    public com.app.badminton_backend.profile.dtos.ProfileDtoResponse getMyProfile() {
        UUID userId = currentUserService.getCurrentUser().getId();
        Profile profile = findByUserId(userId);
        return modelMapper.map(profile, com.app.badminton_backend.profile.dtos.ProfileDtoResponse.class);
    }

    public com.app.badminton_backend.profile.dtos.ProfileDtoResponse updateMyProfile(com.app.badminton_backend.profile.dtos.ProfileUpdateDto updateDto) {
        UUID userId = currentUserService.getCurrentUser().getId();
        Profile profile = findByUserId(userId);
        
        if (updateDto.getFirstName() != null) profile.setFirstName(updateDto.getFirstName());
        if (updateDto.getLastName() != null) profile.setLastName(updateDto.getLastName());
        if (updateDto.getProfilePictureUrl() != null) profile.setProfilePictureUrl(updateDto.getProfilePictureUrl());
        if (updateDto.getHomeCity() != null) profile.setHomeCity(updateDto.getHomeCity());
        
        Profile savedProfile = profileRepository.save(profile);
        return modelMapper.map(savedProfile, com.app.badminton_backend.profile.dtos.ProfileDtoResponse.class);
    }

    public com.app.badminton_backend.profile.dtos.ProfileDtoResponse getProfileById(UUID userId) {
        Profile profile = findByUserId(userId);
        return modelMapper.map(profile, com.app.badminton_backend.profile.dtos.ProfileDtoResponse.class);
    }

    public com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse getStats() {
        UUID userId = currentUserService.getCurrentUser().getId();
        return buildStats(userId);
    }

    /**
     * Returns match statistics for any user — used by the public profile view.
     * Does not require the viewer to be the profile owner.
     */
    public com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse getPublicStats(UUID userId) {
        // Verify the profile exists before returning stats
        findByUserId(userId);
        return buildStats(userId);
    }

    /**
     * Case-insensitive partial search across first + last name.
     * Returns up to 50 results to keep response size bounded.
     */
    public List<com.app.badminton_backend.profile.dtos.ProfileDtoResponse> searchProfiles(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return profileRepository.searchByName(query.trim()).stream()
                .limit(50)
                .map(p -> modelMapper.map(p, com.app.badminton_backend.profile.dtos.ProfileDtoResponse.class))
                .collect(java.util.stream.Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse buildStats(UUID userId) {
        long matchesPlayed = matchPlayerRepository.countCompletedMatchesByUserId(userId);
        long wins = matchesPlayed > 0 ? matchPlayerRepository.countWinsByUserId(userId) : 0L;
        double winRate = matchesPlayed == 0 ? 0.0 : (wins / (double) matchesPlayed) * 100.0;

        PlayerTrustScore trustScore = playerTrustScoreService.getOrCreate(userId);

        return com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse.builder()
                .matchesPlayed((int) matchesPlayed)
                .winRate(winRate)
                .trustScore(trustScore.getScore())
                .build();
    }
}

