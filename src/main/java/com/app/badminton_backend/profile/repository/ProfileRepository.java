package com.app.badminton_backend.profile.repository;

import com.app.badminton_backend.profile.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    /**
     * Case-insensitive partial-match search across first name OR last name.
     * Used by GET /profile/search?q=... .
     */
    @Query("SELECT p FROM Profile p WHERE " +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(p.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Profile> searchByName(@Param("q") String q);
}