package com.app.badminton_backend.auth.repository;

import com.app.badminton_backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByPhoneNumber(String phoneNumber);

}
