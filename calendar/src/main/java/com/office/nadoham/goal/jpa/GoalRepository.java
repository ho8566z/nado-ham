package com.office.nadoham.goal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<GoalEntity, Integer> {

    List<GoalEntity> findByOwnerIdOrderByNoDesc(String ownerId);

    long countByOwnerIdAndStatus(String ownerId, GoalStatus status);

    boolean existsByOwnerIdAndCourseCodeAndStatus(String ownerId, String courseCode, GoalStatus status);

}
