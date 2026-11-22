// 목표 화면: 과정 선택 → 실행 조건 → 첫 주 계획 미리보기(날짜·시각 수정) → 시작. 서버 값은 모두 textContent 로 넣는다.
let selected_course = null;
let preview_seq = 0;        // 요청 번호(늦게 도착한 이전 응답이 새 결과를 덮어쓰지 않게 한다)

document.addEventListener('DOMContentLoaded', function () {
    console.log('GOAL READY!!');

    loadCourses();
    loadGoals();

    document.getElementById('condition_form').addEventListener('submit', function (event) {
        event.preventDefault();
        requestPreview();
    });
    document.getElementById('btn_start').addEventListener('click', startGoal);
});

function showMessage(text, isError) {
    let message = document.getElementById('message');
    message.textContent = text || '';
    message.classList.toggle('error', !!isError);
}

async function postJson(url, body) {
    let response = await fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
    });
    let data = await response.json().catch(function () { return {}; });
    return {ok: response.ok, data: data};
}

async function loadCourses() {
    let response = await fetch('/goal/courses');
    if (!response.ok) {
        showMessage('과정을 불러오지 못했습니다.', true);
        return;
    }

    let list = document.getElementById('course_list');
    list.replaceChildren();
    (await response.json()).forEach(function (course) {
        let button = document.createElement('button');
        button.type = 'button';
        button.className = 'course';

        let theme = document.createElement('span');
        theme.className = 'theme';
        theme.textContent = course.theme;
        let name = document.createElement('strong');
        name.textContent = course.name;
        let goal = document.createElement('span');
        goal.className = 'goal_text';
        goal.textContent = '1개월 · 기본 주 ' + course.defaultWeeklyCount + '회 · ' + course.defaultMinutes + '분';
        button.append(theme, name, goal);

        button.addEventListener('click', function () { selectCourse(course, button); });
        list.appendChild(button);
    });
}

function selectCourse(course, button) {
    selected_course = course;
    document.querySelectorAll('#course_list .course').forEach(function (item) {
        item.classList.toggle('selected', item === button);
    });

    let form = document.getElementById('condition_form');
    form.hidden = false;
    document.getElementById('course_goal').textContent = course.goal;
    form.elements['weekly_count'].value = String(course.defaultWeeklyCount);
    form.elements['minutes'].value = String(course.defaultMinutes);
    document.getElementById('preview').hidden = true;
    showMessage('');
}

function conditionBody() {
    let form = document.getElementById('condition_form');
    let days = Array.from(form.querySelectorAll('input[name="days"]:checked')).map(function (box) {
        return parseInt(box.value, 10);
    });

    return {
        courseCode: selected_course.code,
        startDate: form.elements['start_date'].value || null,
        weeklyCount: parseInt(form.elements['weekly_count'].value, 10),
        minutes: parseInt(form.elements['minutes'].value, 10),
        startTime: form.elements['start_time'].value,
        days: days
    };
}

async function requestPreview() {
    if (!selected_course) {
        return;
    }

    let seq = ++preview_seq;
    let result = await postJson('/goal/plan/preview', conditionBody());
    if (seq !== preview_seq) {
        return;
    }

    if (!result.ok) {
        document.getElementById('preview').hidden = true;
        showMessage(result.data.message || '계획을 만들지 못했습니다.', true);
        return;
    }

    showMessage('');
    renderPreview(result.data.activities);
}

function renderPreview(activities) {
    let list = document.getElementById('preview_list');
    list.replaceChildren();

    activities.forEach(function (activity) {
        let item = document.createElement('li');
        item.dataset.date = activity.date;
        item.classList.toggle('conflict', activity.conflict);

        let date = document.createElement('input');
        date.type = 'date';
        date.value = activity.date;
        date.className = 'slot_date';
        let time = document.createElement('input');
        time.type = 'time';
        time.value = activity.startTime;
        time.className = 'slot_time';

        let title = document.createElement('strong');
        title.textContent = activity.title + ' (' + activity.minutes + '분)';
        let guide = document.createElement('span');
        guide.className = 'guide';
        guide.textContent = activity.guide;

        item.append(date, time, title, guide);
        if (activity.conflict) {
            let warn = document.createElement('span');
            warn.className = 'warn';
            warn.textContent = '이 시간에 "' + activity.conflictTitle + '" 일정이 있습니다. 시간을 옮기거나 그대로 시작할 수 있습니다.';
            item.appendChild(warn);
        }
        list.appendChild(item);
    });

    document.getElementById('preview').hidden = false;
}

async function startGoal() {
    let body = conditionBody();
    body.slots = Array.from(document.querySelectorAll('#preview_list li')).map(function (item) {
        return {date: item.querySelector('.slot_date').value, time: item.querySelector('.slot_time').value};
    });
    // 시작일을 비웠으면 서버가 오늘로 정하므로, 확정 요청에도 같은 기준(미리보기 첫 날짜 이전 = 오늘)을 쓰게 비워 둔다

    let button = document.getElementById('btn_start');
    button.disabled = true;
    let result = await postJson('/goal/start', body);
    button.disabled = false;

    if (!result.ok) {
        showMessage(result.data.message || '시작하지 못했습니다.', true);
        return;
    }

    showMessage('목표를 시작했습니다.');
    document.getElementById('preview').hidden = true;
    document.getElementById('condition_form').hidden = true;
    selected_course = null;
    document.querySelectorAll('#course_list .course').forEach(function (item) { item.classList.remove('selected'); });
    loadGoals();
    if (typeof loadToday === 'function') {
        loadToday();
    }
}

// 목록에서 예정이 아닌 활동에 붙이는 상태 표시
function statusSuffix(status) {
    let labels = {DONE: ' · 완료', SHORT_DONE: ' · 5분만 하고 완료', SKIPPED: ' · 건너뜀'};

    return labels[status] || '';
}

// 목표 제목 뒤에 붙이는 상태 표시(진행 중은 붙이지 않는다)
function goalStatusLabel(status) {
    let labels = {PAUSED: ' · 일시 중지', DONE: ' · 마침'};

    return labels[status] || '';
}

// 남은 기간 문구: 진행 중이면 " · 남은 기간 N일", 종료일이 지났으면 안내, 그 밖의 상태는 붙이지 않는다
function remainingText(goal) {
    if (goal.status !== 'ACTIVE') {
        return '';
    }

    let end = new Date(goal.endDate + 'T23:59:59');
    let days = Math.ceil((end.getTime() - Date.now()) / 86400000);

    return days >= 0 ? ' · 남은 기간 ' + days + '일' : ' · 예정 기간이 지났어요';
}

// 목표 카드의 일시 중지 / 다시 시작 버튼과 실행 조건 수정(마친 목표에는 없다)
function goalActions(goal, card) {
    if (goal.status !== 'ACTIVE' && goal.status !== 'PAUSED') {
        return null;
    }

    let box = document.createElement('div');
    box.className = 'goal_actions';

    let isPaused = goal.status === 'PAUSED';
    let button = document.createElement('button');
    button.type = 'button';
    button.textContent = isPaused ? '다시 시작' : '일시 중지';
    button.addEventListener('click', function () { changeGoalState(goal, isPaused ? 'resume' : 'pause', button); });
    box.appendChild(button);

    if (!isPaused) {
        let edit = document.createElement('button');
        edit.type = 'button';
        edit.textContent = '횟수·시간 수정';
        edit.addEventListener('click', function () { toggleSettingsForm(goal, card); });
        box.appendChild(edit);
    }

    if (isPaused) {
        let note = document.createElement('span');
        note.className = 'goal_note';
        note.textContent = '쉬는 동안 활동은 알림·오늘 탭·달력에서 빠집니다. 다시 시작하면 쉰 일수만큼 남은 활동이 뒤로 밀립니다.';
        box.appendChild(note);
    }

    return box;
}

// 실행 조건 수정 폼(카드 안에서 열고 닫는다)
function toggleSettingsForm(goal, card) {
    let existing = card.querySelector('.settings_form');
    if (existing) {
        existing.remove();
        return;
    }

    let form = document.createElement('form');
    form.className = 'settings_form';

    let count = document.createElement('select');
    count.name = 'weekly_count';
    for (let i = 1; i <= 7; i++) {
        let option = document.createElement('option');
        option.value = String(i);
        option.textContent = '주 ' + i + '회';
        count.appendChild(option);
    }
    count.value = String(goal.weeklyCount);

    let minutes = document.createElement('input');
    minutes.type = 'number';
    minutes.name = 'minutes';
    minutes.min = '5';
    minutes.max = '120';
    minutes.step = '5';
    minutes.value = String(goal.minutes);

    let time = document.createElement('input');
    time.type = 'time';
    time.name = 'start_time';
    time.value = goal.startTime;

    let hint = document.createElement('p');
    hint.className = 'settings_hint';
    hint.textContent = '소요 시간·시각은 아직 하지 않은 활동 전부에 적용돼요. 횟수는 이번 주에 바로 적용돼요(줄이면 뒤쪽 예정 활동이 지워지고, 늘리면 남은 빈 날에 활동이 더해져요). 끝낸 활동은 바뀌지 않아요.';

    let countLabel = document.createElement('label');
    countLabel.append('횟수 ', count);
    let minutesLabel = document.createElement('label');
    minutesLabel.append('1회 소요 시간(분) ', minutes);
    let timeLabel = document.createElement('label');
    timeLabel.append('시작 시각 ', time);

    let save = document.createElement('button');
    save.type = 'submit';
    save.textContent = '저장';

    form.append(countLabel, minutesLabel, timeLabel, hint, save);
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        saveSettings(goal, form, save);
    });
    card.appendChild(form);
}

async function saveSettings(goal, form, button) {
    button.disabled = true;
    let response = await fetch('/goal/' + goal.no + '/settings', {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            weeklyCount: parseInt(form.elements['weekly_count'].value, 10),
            minutes: parseInt(form.elements['minutes'].value, 10),
            startTime: form.elements['start_time'].value
        })
    });
    let data = await response.json().catch(function () { return {}; });
    button.disabled = false;

    if (!response.ok) {
        showMessage(data.message || '수정하지 못했습니다.', true);
        return;
    }

    showMessage(settingsResultText(data));
    loadGoals();
    if (typeof loadToday === 'function') {
        loadToday();
    }
    if (typeof loadRetros === 'function') {
        loadRetros();
    }
}

// 저장 결과 안내: 늘려서 만든 활동·지운 활동 수와 겹침 경고
function settingsResultText(result) {
    let text = '실행 조건을 바꿨어요.';
    if (result.removed > 0) {
        text += ' 이번 주 예정 활동 ' + result.removed + '개를 지웠어요.';
    }
    if (result.added.length > 0) {
        text += ' 이번 주에 활동 ' + result.added.length + '개를 더했어요(' + result.added.map(function (a) { return a.date; }).join(', ') + ').';
        let conflicts = result.added.filter(function (a) { return a.conflict; });
        if (conflicts.length > 0) {
            text += ' ' + conflicts.length + '개는 같은 시간대 일정과 겹쳐요. 오늘 탭에서 옮길 수 있어요.';
        }
    }

    return text;
}

async function changeGoalState(goal, action, button) {
    button.disabled = true;
    let result = await postJson('/goal/' + goal.no + '/' + action, {});
    button.disabled = false;

    if (result.ok) {
        showMessage(action === 'pause' ? '목표를 일시 중지했어요. 준비되면 다시 시작해 주세요.' : '목표를 다시 시작했어요. 남은 활동을 쉰 일수만큼 뒤로 밀었어요.');
    } else {
        showMessage(result.data.message || '처리하지 못했습니다.', true);
    }

    loadGoals();
    if (typeof loadToday === 'function') {
        loadToday();
    }
    if (typeof loadRetros === 'function') {
        loadRetros();
    }
}

async function loadGoals() {
    let response = await fetch('/goal/list');
    let list = document.getElementById('goal_list');
    list.replaceChildren();

    if (!response.ok) {
        showMessage('목표를 불러오지 못했습니다.', true);
        return;
    }

    let goals = (await response.json()).goals;
    if (goals.length === 0) {
        let empty = document.createElement('p');
        empty.className = 'empty';
        empty.textContent = '아직 목표가 없습니다. 아래에서 과정을 골라 시작해 보세요.';
        list.appendChild(empty);
        return;
    }

    goals.forEach(function (goal) {
        let card = document.createElement('div');
        card.className = 'goal_card';
        card.classList.toggle('paused', goal.status === 'PAUSED');

        let title = document.createElement('strong');
        title.textContent = '[' + goal.theme + '] ' + goal.courseName + goalStatusLabel(goal.status);
        let period = document.createElement('span');
        period.className = 'period';
        period.textContent = goal.startDate + ' ~ ' + goal.endDate + ' · 주 ' + goal.weeklyCount + '회 · ' + goal.minutes + '분 · ' + goal.startTime + ' 시작'
            + remainingText(goal);
        card.append(title, period);

        let checkpoint = document.createElement('span');
        checkpoint.className = 'checkpoint';
        checkpoint.textContent = '마지막 주에 확인할 결과: ' + goal.checkpoint;
        card.appendChild(checkpoint);

        let activities = document.createElement('ul');
        goal.activities.forEach(function (activity) {
            let item = document.createElement('li');
            item.textContent = activity.date + ' ' + activity.startTime + ' ' + activity.title + statusSuffix(activity.status);
            activities.appendChild(item);
        });
        card.appendChild(activities);

        let actions = goalActions(goal, card);
        if (actions) {
            card.appendChild(actions);
        }

        list.appendChild(card);
    });
}
