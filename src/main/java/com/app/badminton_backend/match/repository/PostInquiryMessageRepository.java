package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.PostInquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostInquiryMessageRepository extends JpaRepository<PostInquiryMessage, UUID> {

    /** All messages for a given post, ordered by sentAt ascending (chronological). */
    List<PostInquiryMessage> findByPostIdOrderBySentAtAsc(UUID postId);
}
