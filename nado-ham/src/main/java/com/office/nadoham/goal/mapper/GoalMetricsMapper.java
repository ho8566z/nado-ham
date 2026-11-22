package com.office.nadoham.goal.mapper;

import com.office.nadoham.goal.MetricRows;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

// 목표 지표 집계(서브쿼리·집계라 MyBatis). SQL은 goal-metrics-mapper.xml. 읽기 전용이다.
@Mapper
public interface GoalMetricsMapper {

    // 예정일이 지난 활동을 한 번이라도 놓친 사용자와 첫 미실행일. 일시 중지한 목표의 지난 예정 활동은 놓친 것으로 세지 않는다
    List<MetricRows.MissedUser> selectMissedUsers(@Param("today") LocalDate today);

    List<MetricRows.FirstActivityRow> selectFirstActivities();

    List<MetricRows.CourseUser> selectCourseUsers(@Param("today") LocalDate today);

    List<MetricRows.StatusCount> selectActivityStatusCounts();

}
