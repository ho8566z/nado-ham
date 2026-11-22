package com.office.nadoham.goal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 기간·수준·개인 목표에 따른 계획의 모양: 월 단계 배치, 주차별 활동, 월별 중간 목표, 마지막 주 판단
class CoursePlanTest {

    private static final String LONG_GOAL = "가".repeat(GoalService.MAX_PERSONAL_GOAL);

    private static CoursePlan plan(String code, int months, GoalLevel level) {
        return new CoursePlan(CourseCatalog.find(code).orElseThrow(), months, level, null);
    }

    private static List<Integer> stages(CoursePlan plan) {
        return java.util.stream.IntStream.range(0, plan.months()).map(plan::stageIndex).boxed().toList();
    }

    @Test
    @DisplayName("월 단계 배치: 첫 달은 기초, 마지막 달은 마무리이고 사이는 고르게 늘어난다")
    void stageIndex_spreadsStages() {
        assertThat(stages(plan("GUITAR_SONG", 3, GoalLevel.BEGINNER))).containsExactly(0, 2, 3);
        assertThat(stages(plan("GUITAR_SONG", 6, GoalLevel.BEGINNER))).containsExactly(0, 1, 1, 2, 2, 3);
        assertThat(stages(plan("GUITAR_SONG", 12, GoalLevel.BEGINNER))).containsExactly(0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3);
    }

    @Test
    @DisplayName("해 본 수준은 기초를 건너뛰고 연습 단계부터 시작한다")
    void stageIndex_experiencedSkipsBasics() {
        assertThat(stages(plan("GUITAR_SONG", 3, GoalLevel.EXPERIENCED))).containsExactly(1, 2, 3);
        assertThat(stages(plan("GUITAR_SONG", 6, GoalLevel.EXPERIENCED)).get(0)).isEqualTo(1);
    }

    @Test
    @DisplayName("1개월 과정: 처음 시작하면 과정의 주차별 활동, 해 본 수준이면 연습 단계 활동을 쓴다")
    void oneMonth_usesWeeksOrExperiencedStages() {
        assertThat(plan("EXERCISE_HABIT", 1, GoalLevel.BEGINNER).sessionsOfWeek(2).get(0).title()).isEqualTo("빠르게 걷기");

        CoursePlan experienced = plan("EXERCISE_HABIT", 1, GoalLevel.EXPERIENCED);
        assertThat(experienced.sessionsOfWeek(1)).isEqualTo(CourseCatalog.find("EXERCISE_HABIT").orElseThrow().stages().get(1).sessions());
        assertThat(experienced.sessionsOfWeek(4)).isEqualTo(CourseCatalog.find("EXERCISE_HABIT").orElseThrow().stages().get(3).sessions());
    }

    @Test
    @DisplayName("긴 과정: 주마다 안내 앞머리가 바뀌고, 같은 단계가 이어지는 달에는 더 깊게 하라는 안내가 붙는다")
    void longCourse_addsPhaseAndDeeperHints() {
        CoursePlan twelve = plan("DIET_START", 12, GoalLevel.BEGINNER);

        assertThat(twelve.sessionsOfWeek(1).get(0).guide()).startsWith("이번 주는 익히는 주예요. ").doesNotContain("더 깊게");
        assertThat(twelve.sessionsOfWeek(2).get(0).guide()).startsWith("이번 주는 반복하는 주예요. ");
        assertThat(twelve.sessionsOfWeek(4).get(0).guide()).startsWith("이번 주는 점검하는 주예요. ");
        // 둘째 달(주 5~8)은 첫 달과 같은 단계라 더 깊게 안내가 붙는다
        assertThat(twelve.sessionsOfWeek(5).get(0).guide()).contains("더 깊게");
        // 셋째 달은 다음 단계라 붙지 않는다
        assertThat(twelve.sessionsOfWeek(9).get(0).guide()).doesNotContain("더 깊게");
        assertThat(twelve.sessionsOfWeek(9).get(0).title()).isNotEqualTo(twelve.sessionsOfWeek(1).get(0).title());
    }

    @Test
    @DisplayName("전체 주 수와 월별 중간 목표 수는 기간(개월)과 같다")
    void milestones_matchMonths() {
        for (int months : CoursePlan.MONTH_OPTIONS) {
            CoursePlan plan = plan("TRAVEL_ENGLISH", months, GoalLevel.BEGINNER);

            assertThat(plan.totalWeeks()).isEqualTo(months * 4);
            assertThat(plan.milestones()).hasSize(months);
            assertThat(plan.milestones()).extracting(CoursePlan.Milestone::month).containsExactlyElementsOf(
                    java.util.stream.IntStream.rangeClosed(1, months).boxed().toList());
        }
        CoursePlan three = plan("TRAVEL_ENGLISH", 3, GoalLevel.BEGINNER);
        assertThat(three.checkpoint()).startsWith("마지막 달(3개월 차) 목표: ").contains(three.milestones().get(2).goal());
        assertThat(plan("TRAVEL_ENGLISH", 1, GoalLevel.BEGINNER).checkpoint()).contains("마지막 주에");
    }

    @Test
    @DisplayName("마지막 주: 전체 주 수에 닿았거나, 종료일을 앞당겨 다음 주가 종료일 뒤에 시작할 때")
    void isFinalWeek() {
        LocalDate start = LocalDate.of(2026, 11, 16);
        CoursePlan three = plan("GUITAR_SONG", 3, GoalLevel.BEGINNER);

        assertThat(three.isFinalWeek(4, start, start.plusDays(83))).isFalse();
        assertThat(three.isFinalWeek(12, start, start.plusDays(83))).isTrue();
        // 종료일을 4주 차 끝(start+27)으로 줄이면 4주 차가 마지막이다
        assertThat(three.isFinalWeek(4, start, start.plusDays(27))).isTrue();
        assertThat(three.isFinalWeek(3, start, start.plusDays(27))).isFalse();
    }

    @Test
    @DisplayName("개인 목표: 목표 문장이 이름·중간 목표·활동 이름에 들어가고 {goal} 자리표시는 남지 않는다")
    void personal_fillsGoalSentence() {
        CoursePlan plan = new CoursePlan(CourseCatalog.find("PERSONAL_STUDY").orElseThrow(), 3, GoalLevel.BEGINNER, "토익 800점 받기");

        assertThat(plan.displayName()).isEqualTo("토익 800점 받기");
        assertThat(plan.milestones()).allSatisfy(m -> assertThat(m.goal()).contains("토익 800점 받기").doesNotContain("{goal}"));
        assertThat(plan.checkpoint()).doesNotContain("{goal}");
        assertThat(plan.sessionsOfWeek(1)).allSatisfy(s -> assertThat(s.title()).contains("토익 800점 받기").doesNotContain("{goal}"));
    }

    @Test
    @DisplayName("모든 과정·기간·수준의 모든 주차 활동은 저장 길이(이름 100자, 안내 300자) 안이다")
    void everySessionFitsColumns() {
        for (Course course : CourseCatalog.all()) {
            for (int months : CoursePlan.MONTH_OPTIONS) {
                for (GoalLevel level : GoalLevel.values()) {
                    CoursePlan plan = new CoursePlan(course, months, level, course.personal() ? LONG_GOAL : null);
                    for (int week = 1; week <= plan.totalWeeks(); week++) {
                        List<Course.Session> sessions = plan.sessionsOfWeek(week);
                        assertThat(sessions).as("%s %d개월 %s %d주", course.code(), months, level, week).isNotEmpty();
                        assertThat(sessions).allSatisfy(s -> {
                            assertThat(s.title()).isNotBlank().hasSizeLessThanOrEqualTo(100);
                            assertThat(s.guide()).isNotBlank().hasSizeLessThanOrEqualTo(300);
                        });
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("과정 목록: 세부 테마 9개와 개인 목표 3개가 있고 코드는 겹치지 않는다")
    void catalog_hasThemesAndPersonalCourses() {
        List<Course> all = CourseCatalog.all();

        assertThat(all).extracting(Course::code).doesNotHaveDuplicates();
        assertThat(all.stream().filter(course -> !course.personal())).hasSize(9);
        assertThat(all.stream().filter(Course::personal)).extracting(Course::theme).containsExactly("운동", "공부", "자기개발");
        assertThat(all).allSatisfy(course -> assertThat(course.stages()).hasSize(4));
    }

}
