package com.office.nadoham.goal;

import com.office.nadoham.goal.mapper.GoalMetricsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

// 목표 실행 캘린더 지표 계산(읽기 전용). 집계 쿼리는 MyBatis 가 하고, 기준 기간·관찰 중 구분은 여기서 한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalMetricsService {

    public static final int RECOVERY_DAYS = 7;      // 핵심 지표: 첫 미실행 뒤 이 일수 안에 다시 완료했는지

    private final GoalMetricsMapper goalMetricsMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GoalMetrics compute() {
        log.info("compute()");

        LocalDate today = LocalDate.now(clock);

        return new GoalMetrics(today.toString(),
                recovery(goalMetricsMapper.selectMissedUsers(today), today),
                firstActivity(goalMetricsMapper.selectFirstActivities(), today),
                completion(goalMetricsMapper.selectCourseUsers(today)),
                activities(goalMetricsMapper.selectActivityStatusCounts()));
    }

    // 회복했으면 성공, 아직 기간(첫 미실행 + 7일)이 남았으면 관찰 중, 기간이 끝났는데 못 했으면 실패
    static GoalMetrics.Recovery recovery(List<MetricRows.MissedUser> users, LocalDate today) {
        int recovered = 0;
        int notRecovered = 0;
        int pending = 0;
        for (MetricRows.MissedUser user : users) {
            boolean windowClosed = today.isAfter(user.firstMiss().plusDays(RECOVERY_DAYS));
            if (user.recovered()) {
                recovered++;
            } else if (windowClosed) {
                notRecovered++;
            } else {
                pending++;
            }
        }

        return new GoalMetrics.Recovery(users.size(), recovered, notRecovered, pending, rate(recovered, recovered + notRecovered));
    }

    // 첫 활동을 끝냈으면 완료, 아직 예정이고 날짜가 안 지났으면 관찰 중, 그 밖에(놓침·건너뜀)는 미완료.
    // 첫 활동 날짜가 오늘 이후인 사용자는 아직 '첫 계획을 확정만 한' 상태라 대상에서 뺀다
    static GoalMetrics.FirstActivity firstActivity(List<MetricRows.FirstActivityRow> rows, LocalDate today) {
        int completed = 0;
        int notCompleted = 0;
        int pending = 0;
        for (MetricRows.FirstActivityRow row : rows) {
            boolean done = "DONE".equals(row.status()) || "SHORT_DONE".equals(row.status());
            if (done) {
                completed++;
            } else if ("PLANNED".equals(row.status()) && !row.actDate().isBefore(today)) {
                pending++;
            } else {
                notCompleted++;
            }
        }

        return new GoalMetrics.FirstActivity(rows.size(), completed, notCompleted, pending, rate(completed, completed + notCompleted));
    }

    static GoalMetrics.Completion completion(List<MetricRows.CourseUser> users) {
        int ended = 0;
        int completed = 0;
        for (MetricRows.CourseUser user : users) {
            if (user.ended()) {
                ended++;
                if (user.completed()) {
                    completed++;
                }
            }
        }

        return new GoalMetrics.Completion(ended, completed, rate(completed, ended));
    }

    static GoalMetrics.Activities activities(List<MetricRows.StatusCount> counts) {
        long planned = countOf(counts, "PLANNED");
        long done = countOf(counts, "DONE");
        long shortDone = countOf(counts, "SHORT_DONE");
        long skipped = countOf(counts, "SKIPPED");

        return new GoalMetrics.Activities(planned, done, shortDone, skipped, rate(shortDone, done + shortDone));
    }

    private static long countOf(List<MetricRows.StatusCount> counts, String status) {
        return counts.stream().filter(c -> status.equals(c.status())).mapToLong(MetricRows.StatusCount::total).sum();
    }

    // 분모가 0 이면 null(아직 계산할 수 없음)
    private static Double rate(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

}
