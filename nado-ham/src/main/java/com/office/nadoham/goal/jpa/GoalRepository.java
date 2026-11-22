package com.office.nadoham.goal.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<GoalEntity, Integer> {

    // 같은 목표의 다음 주 계획을 동시에 두 번 만들지 않도록 행을 잠그고 읽는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GoalEntity g where g.no = :no")
    Optional<GoalEntity> findWithLockByNo(@Param("no") int no);

    List<GoalEntity> findByOwnerIdOrderByNoDesc(String ownerId);

    List<GoalEntity> findByOwnerIdAndStatusOrderByNoAsc(String ownerId, GoalStatus status);

    long countByOwnerIdAndStatus(String ownerId, GoalStatus status);

    boolean existsByOwnerIdAndCourseCodeAndStatus(String ownerId, String courseCode, GoalStatus status);

}
