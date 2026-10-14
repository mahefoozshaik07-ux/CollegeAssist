package com.collegeassist.backend.repository;

import com.collegeassist.backend.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByStudentEmailOrderByUpdatedAtDesc(String studentEmail);

    Optional<Conversation> findByIdAndStudentEmail(String id, String studentEmail);

    boolean existsByIdAndStudentEmail(String id, String studentEmail);

    void deleteByIdAndStudentEmail(String id, String studentEmail);
}
