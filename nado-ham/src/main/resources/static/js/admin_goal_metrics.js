// 관리자: 목표 실행 캘린더 지표 화면. 서버 값은 모두 textContent 로 넣는다.
document.addEventListener('DOMContentLoaded', function () {
    loadMetrics();
});

function percent(rate) {
    return rate === null || rate === undefined ? '아직 계산할 수 없음' : (Math.round(rate * 1000) / 10) + '%';
}

function setText(id, text) {
    document.getElementById(id).textContent = text;
}

async function loadMetrics() {
    let response = await fetch('/admin/goal-metrics/data');
    if (!response.ok) {
        setText('message', '지표를 불러오지 못했습니다.');
        return;
    }

    let m = await response.json();
    setText('as_of', '기준일 ' + m.asOf);

    let r = m.recovery;
    setText('recovery_rate', percent(r.rate));
    setText('recovery_counts', '놓친 사용자 ' + r.missedUsers + '명 = 다시 시작 ' + r.recovered + ' + 못 함 ' + r.notRecovered + ' + 관찰 중 ' + r.pending);

    let f = m.firstActivity;
    setText('first_rate', percent(f.rate));
    setText('first_counts', '사용자 ' + f.users + '명 = 완료 ' + f.completed + ' + 미완료 ' + f.notCompleted + ' + 관찰 중 ' + f.pending);

    let c = m.completion;
    setText('completion_rate', percent(c.rate));
    setText('completion_counts', '기간이 끝난 사용자 ' + c.endedUsers + '명 중 마무리 ' + c.completedUsers + '명');

    let a = m.activities;
    setText('activity_counts', '예정 ' + a.planned + ' · 완료 ' + a.done + ' · 5분만 하고 완료 ' + a.shortDone + ' · 건너뜀 ' + a.skipped);
    setText('short_share', '5분만 하고 완료 비중 ' + percent(a.shortShare));
}
