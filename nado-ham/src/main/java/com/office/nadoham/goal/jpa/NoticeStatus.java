package com.office.nadoham.goal.jpa;

// SENDING 발송 중(선점함), SENT 발송 완료, FAILED 실패했지만 다시 시도할 수 있음, GIVEUP 더 보내지 않음(차단·시도 초과·결과 불명)
public enum NoticeStatus {
    SENDING, SENT, FAILED, GIVEUP
}
