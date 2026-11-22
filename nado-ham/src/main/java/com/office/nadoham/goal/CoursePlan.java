package com.office.nadoham.goal;

import com.office.nadoham.goal.jpa.GoalEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// 과정 + 기간(개월) + 현재 수준 + (개인 목표면) 목표 문장으로 정해지는 계획의 모양.
// 주차별 활동, 월별 중간 목표, 전체 주 수를 한 곳에서 정해 미리보기·회고·수정이 같은 기준을 쓰게 한다.
//
// 1개월 과정(처음 시작): 과정에 직접 쓴 주차별 활동을 그대로 쓴다.
// 3·6·12개월 과정: 네 개의 월 단계(기초→연습→적용→마무리)를 개월 수에 맞게 고르게 늘려 쓴다.
//   첫 달은 기초, 마지막 달은 마무리 단계이고, 같은 단계가 이어지는 달에는 '더 깊게' 안내를 덧붙인다.
//   한 달 안에서는 익히기→반복→적용→점검 순서로 주마다 안내 앞머리가 달라진다.
// 해 본 적이 있는 수준은 기초를 건너뛰고 연습 단계부터 시작한다.
public record CoursePlan(Course course, int months, GoalLevel level, String personalGoal) {

    public static final List<Integer> MONTH_OPTIONS = List.of(1, 3, 6, 12);
    public static final int WEEKS_PER_MONTH = 4;
    public static final int MAX_GUIDE_LENGTH = 300;         // GOAL_ACTIVITY.GUIDE 길이

    private static final List<String> WEEK_PHASES = List.of(
            "이번 주는 익히는 주예요. ",
            "이번 주는 반복하는 주예요. ",
            "이번 주는 써 보는 주예요. ",
            "이번 주는 점검하는 주예요. ");
    private static final String DEEPER = "지난달보다 한 단계 더 깊게 해 보세요. ";

    // 월별 중간 목표 한 건(month 는 1부터)
    public record Milestone(int month, String name, String goal) {
    }

    // 저장된 목표의 계획. 목표를 만든 뒤에는 과정 정보가 바뀌지 않는 것으로 본다
    public static CoursePlan of(GoalEntity goal) {
        Course course = CourseCatalog.find(goal.getCourseCode()).orElseThrow();
        GoalLevel level = GoalLevel.parse(goal.getLevel()).orElse(GoalLevel.BEGINNER);

        return new CoursePlan(course, goal.getMonths(), level, goal.getPersonalGoal());
    }

    // 그 주차가 마지막 주인지: 전체 주 수에 닿았거나, 사용자가 종료일을 앞당겨 다음 주가 종료일 뒤에 시작할 때
    public boolean isFinalWeek(int weekNo, LocalDate startDate, LocalDate endDate) {
        return weekNo >= totalWeeks() || startDate.plusDays((long) GoalService.WEEK_DAYS * weekNo).isAfter(endDate);
    }

    public int totalWeeks() {
        return months * WEEKS_PER_MONTH;
    }

    // 주차(1부터)가 속한 달(1부터)
    public int monthOf(int weekNo) {
        return (weekNo - 1) / WEEKS_PER_MONTH + 1;
    }

    public String displayName() {
        return course.personal() ? personalGoal : course.name();
    }

    public String goalText() {
        return personalize(course.goal());
    }

    // 계획이 끝났을 때 확인할 결과: 1개월은 과정의 결과, 긴 과정은 마지막 달의 중간 목표
    public String checkpoint() {
        if (months == 1) {
            return personalize(course.checkpoint());
        }

        List<Milestone> all = milestones();

        return "마지막 달(" + months + "개월 차) 목표: " + all.get(all.size() - 1).goal();
    }

    // 월별 중간 목표(1개월 과정은 과정 목표 하나)
    public List<Milestone> milestones() {
        List<Milestone> result = new ArrayList<>();
        if (months == 1) {
            result.add(new Milestone(1, personalize(course.theme() + " 한 달"), goalText()));
            return result;
        }

        for (int month = 0; month < months; month++) {
            Course.Stage stage = course.stages().get(stageIndex(month));
            result.add(new Milestone(month + 1, personalize(stage.name()), personalize(stage.goal())));
        }

        return result;
    }

    // 주차(1부터)에 할 활동 목록
    public List<Course.Session> sessionsOfWeek(int weekNo) {
        int last = course.stages().size() - 1;
        if (months == 1) {
            List<Course.Session> sessions = level == GoalLevel.BEGINNER
                    ? course.sessionsOfWeek(weekNo)
                    : course.stages().get(Math.min(weekNo, last)).sessions();

            return personalize(sessions, "");
        }

        int month = monthOf(weekNo) - 1;
        int stageIndex = stageIndex(month);
        boolean deeper = month > 0 && stageIndex(month - 1) == stageIndex;
        String prefix = WEEK_PHASES.get((weekNo - 1) % WEEKS_PER_MONTH) + (deeper ? DEEPER : "");

        return personalize(course.stages().get(stageIndex).sessions(), prefix);
    }

    // 달(0부터)에 쓰는 월 단계 번호: 처음 수준은 첫 단계부터, 해 본 수준은 둘째 단계부터 마지막 단계까지 고르게 늘린다
    int stageIndex(int month) {
        int last = course.stages().size() - 1;
        int first = level == GoalLevel.EXPERIENCED ? 1 : 0;
        if (months == 1) {
            return first;
        }

        return first + (int) Math.round(month * (double) (last - first) / (months - 1));
    }

    // 개인 목표 과정의 {goal} 자리를 목표 문장으로 바꾼다(그 밖의 과정은 그대로)
    public String personalize(String text) {
        if (!course.personal() || personalGoal == null) {
            return text;
        }

        return text.replace(Course.GOAL_SLOT, personalGoal);
    }

    private List<Course.Session> personalize(List<Course.Session> sessions, String prefix) {
        return sessions.stream()
                .map(session -> new Course.Session(personalize(session.title()), limit(prefix + personalize(session.guide()))))
                .toList();
    }

    private static String limit(String guide) {
        return guide.length() <= MAX_GUIDE_LENGTH ? guide : guide.substring(0, MAX_GUIDE_LENGTH);
    }

}
