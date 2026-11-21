package com.office.calendar.goal;

// 목표 요청이 규칙에 맞지 않을 때(컨트롤러가 400으로 바꾼다). code 는 화면이 구분해서 쓰는 값이다.
public class GoalException extends RuntimeException {

    private final String code;

    public GoalException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}
