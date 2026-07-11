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
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.match.repository.MatchPostRepository;
import com.app.badminton_backend.profile.dtos.ProfileAnalyticsDtoResponse;
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
    private final MatchRepository matchRepository;
    private final MatchPostRepository matchPostRepository;

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

    public ProfileAnalyticsDtoResponse getAnalytics() {
        UUID userId = currentUserService.getCurrentUser().getId();
        return buildAnalytics(userId);
    }

    public ProfileAnalyticsDtoResponse getPublicAnalytics(UUID userId) {
        findByUserId(userId);
        return buildAnalytics(userId);
    }

    private ProfileAnalyticsDtoResponse buildAnalytics(UUID userId) {
        List<MatchPlayer> completedMatchPlayers = matchPlayerRepository.findCompletedMatchesByUserId(userId);
        
        int totalMatches = completedMatchPlayers.size();
        
        // Find current Elo
        Optional<EloPoints> eloOpt = eloPointsRepository.findById(userId);
        int currentElo = eloOpt.map(EloPoints::getElo).orElse(1000);
        
        // Find peak Elo (highest eloAfter, fallback to currentElo)
        int peakElo = currentElo;
        for (MatchPlayer mp : completedMatchPlayers) {
            if (mp.getEloAfter() != null && mp.getEloAfter() > peakElo) {
                peakElo = mp.getEloAfter();
            }
        }
        
        long wins = 0;
        int singlesCount = 0;
        int doublesCount = 0;
        java.util.Map<String, Integer> cityCounts = new java.util.HashMap<>();
        
        for (MatchPlayer mp : completedMatchPlayers) {
            Optional<Match> matchOpt = matchRepository.findById(mp.getMatchId());
            if (matchOpt.isPresent()) {
                Match match = matchOpt.get();
                
                // Win count
                if (mp.getTeam() != null && mp.getTeam() == match.getWinnerTeam()) {
                    wins++;
                }
                
                // Match type count
                if (match.getMatchType() != null) {
                    if (match.getMatchType() == com.app.badminton_backend.match.enums.MatchType.SINGLES) {
                        singlesCount++;
                    } else if (match.getMatchType() == com.app.badminton_backend.match.enums.MatchType.DOUBLES) {
                        doublesCount++;
                    }
                }
                
                // City count
                if (match.getPostId() != null) {
                    Optional<com.app.badminton_backend.match.entity.MatchPost> postOpt = matchPostRepository.findById(match.getPostId());
                    if (postOpt.isPresent()) {
                        String city = postOpt.get().getCity();
                        if (city != null) {
                            if ("Other".equalsIgnoreCase(city) && postOpt.get().getCityOther() != null) {
                                city = postOpt.get().getCityOther();
                            }
                            cityCounts.put(city, cityCounts.getOrDefault(city, 0) + 1);
                        }
                    }
                }
            }
        }
        
        double winRate = totalMatches == 0 ? 0.0 : (wins / (double) totalMatches) * 100.0;
        
        // Streak calculation
        String currentStreak = "0";
        if (!completedMatchPlayers.isEmpty()) {
            MatchPlayer latestMp = completedMatchPlayers.get(0);
            Optional<Match> latestMatchOpt = matchRepository.findById(latestMp.getMatchId());
            if (latestMatchOpt.isPresent()) {
                Match latestMatch = latestMatchOpt.get();
                boolean latestIsWin = latestMp.getTeam() != null && latestMp.getTeam() == latestMatch.getWinnerTeam();
                boolean latestIsLoss = latestMp.getTeam() != null && latestMatch.getWinnerTeam() != null && latestMp.getTeam() != latestMatch.getWinnerTeam();
                
                String streakType = latestIsWin ? "W" : (latestIsLoss ? "L" : "D");
                int streakCount = 0;
                
                for (MatchPlayer mp : completedMatchPlayers) {
                    Optional<Match> mOpt = matchRepository.findById(mp.getMatchId());
                    if (mOpt.isPresent()) {
                        Match m = mOpt.get();
                        boolean isWin = mp.getTeam() != null && mp.getTeam() == m.getWinnerTeam();
                        boolean isLoss = mp.getTeam() != null && m.getWinnerTeam() != null && mp.getTeam() != m.getWinnerTeam();
                        
                        String outcome = isWin ? "W" : (isLoss ? "L" : "D");
                        if (outcome.equals(streakType)) {
                            streakCount++;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                currentStreak = streakCount + streakType;
            }
        }
        
        // Recent form
        java.util.List<String> recentForm = new java.util.ArrayList<>();
        int formLimit = Math.min(5, completedMatchPlayers.size());
        java.util.List<MatchPlayer> subList = completedMatchPlayers.subList(0, formLimit);
        for (int i = subList.size() - 1; i >= 0; i--) {
            MatchPlayer mp = subList.get(i);
            Optional<Match> mOpt = matchRepository.findById(mp.getMatchId());
            if (mOpt.isPresent()) {
                Match m = mOpt.get();
                boolean isWin = mp.getTeam() != null && mp.getTeam() == m.getWinnerTeam();
                boolean isLoss = mp.getTeam() != null && m.getWinnerTeam() != null && mp.getTeam() != m.getWinnerTeam();
                recentForm.add(isWin ? "W" : (isLoss ? "L" : "D"));
            }
        }
        
        // Most played type
        String mostPlayedMatchType = "None";
        if (singlesCount > 0 || doublesCount > 0) {
            mostPlayedMatchType = singlesCount >= doublesCount ? "SINGLES" : "DOUBLES";
        }
        
        // Most frequent city
        String mostFrequentCity = "None";
        int maxCityCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : cityCounts.entrySet()) {
            if (entry.getValue() > maxCityCount) {
                maxCityCount = entry.getValue();
                mostFrequentCity = entry.getKey();
            }
        }
        if ("None".equals(mostFrequentCity)) {
            Profile profile = findByUserId(userId);
            if (profile.getHomeCity() != null && !profile.getHomeCity().isBlank()) {
                mostFrequentCity = profile.getHomeCity();
            }
        }
        
        return ProfileAnalyticsDtoResponse.builder()
                .totalMatchesPlayed(totalMatches)
                .winRate(winRate)
                .currentElo(currentElo)
                .peakElo(peakElo)
                .currentStreak(currentStreak)
                .recentForm(recentForm)
                .mostPlayedMatchType(mostPlayedMatchType)
                .mostFrequentCity(mostFrequentCity)
                .build();
    }
}

