package com.office.nadoham.goal.mapper;

import com.office.nadoham.goal.NoticeGoal;
import com.office.nadoham.goal.ReminderTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

// 목표 알림 대상 조회(조인·시간 범위 조건이라 MyBatis). SQL은 goal-notice-mapper.xml
@Mapper
public interface GoalNoticeMapper {

    // from ~ to 에 시작하는 예정 활동 중 보낼 차례인 것: 알림 기록이 없거나, 실패했고 시도 횟수가 남았고 마지막 시도가 retryBefore 이전인 것
    List<ReminderTarget> selectDueReminders(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("maxAttempts") int maxAttempts,
                                            @Param("retryBefore") LocalDateTime retryBefore);

    // 알림을 켜고 텔레그램이 연동된 USER 의 진행 중 목표(회고 알림 후보)
    List<NoticeGoal> selectNoticeGoals();

}
