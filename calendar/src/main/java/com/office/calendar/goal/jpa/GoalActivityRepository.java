package com.office.calendar.goal.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GoalActivityRepository extends JpaRepository<GoalActivityEntity, Integer> {

    List<GoalActivityEntity> findByGoalNoInOrderByActDateAscStartTimeAsc(Collection<Integer> goalNos);

}
