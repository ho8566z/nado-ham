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

        let title = document.createElement('strong');
        title.textContent = '[' + goal.theme + '] ' + goal.courseName;
        let period = document.createElement('span');
        period.className = 'period';
        period.textContent = goal.startDate + ' ~ ' + goal.endDate + ' · 주 ' + goal.weeklyCount + '회 · ' + goal.minutes + '분';
        card.append(title, period);

        let activities = document.createElement('ul');
        goal.activities.forEach(function (activity) {
            let item = document.createElement('li');
            item.textContent = activity.date + ' ' + activity.startTime + ' ' + activity.title;
            activities.appendChild(item);
        });
        card.appendChild(activities);

        list.appendChild(card);
    });
}
