package com.office.nadoham.goal;

import java.util.List;
import java.util.Optional;

// 첫 출시 범위: 1개월 과정 3개(운동·공부·자기개발). 3·6·12개월 과정과 다른 세부 테마는 확장 후보다.
public final class CourseCatalog {

    public static final int COURSE_WEEKS = 4;

    private static final List<Course> COURSES = List.of(
            new Course("EXERCISE_HABIT", "운동", "주 3회 움직이는 습관 만들기",
                    "1개월 동안 주 3회 움직이는 습관 만들기",
                    "4주 차에 주 3회를 채우는지, 한 번에 30분을 걸을 수 있는지 확인합니다.",
                    3, 20,
                    List.of(
                            List.of(new Course.Session("걷기 20분", "편한 속도로 20분 걷기. 숨이 차면 속도를 낮추세요."),
                                    new Course.Session("스트레칭 + 걷기", "5분 스트레칭 뒤 15분 걷기."),
                                    new Course.Session("걷기 20분", "어제보다 한 블록만 더 멀리 가 보세요.")),
                            List.of(new Course.Session("빠르게 걷기 20분", "2분 빠르게, 1분 천천히를 반복하세요."),
                                    new Course.Session("맨몸 운동 20분", "스쿼트 10회, 팔굽혀펴기 5회를 3세트(무릎 대고 해도 됩니다)."),
                                    new Course.Session("걷기 25분", "편한 속도로 25분 걷기.")),
                            List.of(new Course.Session("빠르게 걷기 25분", "3분 빠르게, 1분 천천히를 반복하세요."),
                                    new Course.Session("맨몸 운동 25분", "스쿼트 12회, 팔굽혀펴기 8회를 3세트."),
                                    new Course.Session("걷기 30분", "계단이나 언덕을 한 번 넣어 보세요.")),
                            List.of(new Course.Session("걷기 30분", "쉬지 않고 30분 걷기에 도전하세요."),
                                    new Course.Session("맨몸 운동 25분", "지난주보다 한 세트를 더하거나 횟수를 2회 늘려 보세요."),
                                    new Course.Session("한 달 마무리 운동 30분", "좋아하는 운동으로 30분, 끝나고 한 달을 돌아보세요.")))),
            new Course("TRAVEL_ENGLISH", "공부", "여행 영어로 주문하고 길 묻기",
                    "여행 영어로 주문과 길 묻기 연습하기",
                    "4주 차에 주문과 길 묻기 상황을 보지 않고 한 번씩 말해 봅니다.",
                    3, 15,
                    List.of(
                            List.of(new Course.Session("주문 표현 익히기", "주문 표현 3개를 소리 내어 말하고 한 번 녹음하기."),
                                    new Course.Session("주문 표현 복습", "어제 표현 3개를 보지 않고 말해 보고, 새 표현 1개 더하기."),
                                    new Course.Session("주문 상황 연습", "메뉴를 고르고 계산하는 흐름을 혼자 말해 보기.")),
                            List.of(new Course.Session("길 묻기 표현 익히기", "길 묻는 표현 3개를 소리 내어 말하기(예: Where is ...?)."),
                                    new Course.Session("길 묻기 복습", "표현 3개를 보지 않고 말하고, 방향 단어(left, right, straight)를 익히기."),
                                    new Course.Session("길 묻기 상황 연습", "지도를 보며 목적지까지 길을 묻고 답하는 연습.")),
                            List.of(new Course.Session("숙소·교통 표현 익히기", "체크인과 택시 이용에 쓰는 표현 3개를 말하기."),
                                    new Course.Session("숙소·교통 복습", "표현 3개를 보지 않고 말해 보기."),
                                    new Course.Session("여행 상황 연습", "공항에서 숙소까지 가는 상황을 처음부터 끝까지 말해 보기.")),
                            List.of(new Course.Session("주문 다시 말하기", "첫 주 주문 상황을 보지 않고 말하고 녹음해서 비교하기."),
                                    new Course.Session("길 묻기 다시 말하기", "둘째 주 길 묻기 상황을 보지 않고 말하고 녹음하기."),
                                    new Course.Session("한 달 마무리", "세 상황 중 하나를 골라 1분 동안 이어서 말해 보기.")))),
            new Course("GUITAR_SONG", "자기개발", "기타로 고른 곡 한 곡 연주하기",
                    "기타로 고른 곡 한 곡 연주하기",
                    "4주 차에 고른 곡을 느린 속도로 처음부터 끝까지 연주해 봅니다.",
                    3, 15,
                    List.of(
                            List.of(new Course.Session("G·D 코드 전환", "G 코드와 D 코드를 번갈아 바꾸는 연습을 15분 하기."),
                                    new Course.Session("코드 전환 + 스트럼", "G, D 코드를 번갈아 잡으며 아래로 긁는 연습."),
                                    new Course.Session("코드 한 개 더", "Em 코드를 익히고 G-D-Em을 천천히 돌려 보기.")),
                            List.of(new Course.Session("곡 고르기와 첫 구간", "연주할 곡을 정하고 코드 진행을 확인한 뒤 첫 구간만 연습하기."),
                                    new Course.Session("첫 구간 반복", "첫 구간을 느린 속도로 5번 반복하기."),
                                    new Course.Session("둘째 구간 연습", "둘째 구간을 코드만 잡으며 연습하기.")),
                            List.of(new Course.Session("구간 이어 연주", "첫 구간과 둘째 구간을 이어서 느리게 연주하기."),
                                    new Course.Session("후렴 연습", "후렴 구간의 코드 전환을 집중해서 연습하기."),
                                    new Course.Session("셋째 구간 연습", "남은 구간을 코드만 잡으며 연습하기.")),
                            List.of(new Course.Session("곡 전체 연주", "곡 전체를 아주 느린 속도로 처음부터 끝까지 연주하기."),
                                    new Course.Session("막히는 구간 보강", "틀린 구간만 골라 5번씩 반복하기."),
                                    new Course.Session("한 곡 완주", "곡 전체를 한 번 녹음하고 첫 주와 비교해 보기."))))
    );

    private CourseCatalog() {
    }

    public static List<Course> all() {
        return COURSES;
    }

    public static Optional<Course> find(String code) {
        return COURSES.stream().filter(course -> course.code().equals(code)).findFirst();
    }

}
