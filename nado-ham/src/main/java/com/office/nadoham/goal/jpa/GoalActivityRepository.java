package com.office.nadoham.goal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface GoalActivityRepository extends JpaRepository<GoalActivityEntity, Integer> {

    List<GoalActivityEntity> findByGoalNoInOrderByActDateAscStartTimeAsc(Collection<Integer> goalNos);

    List<GoalActivityEntity> findByGoalNoInAndActDateBetweenOrderByActDateAscStartTimeAsc(
            Collection<Integer> goalNos, LocalDate from, LocalDate to);

}
