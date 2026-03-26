package com.example.gymbooking.repository;

import com.example.gymbooking.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
}