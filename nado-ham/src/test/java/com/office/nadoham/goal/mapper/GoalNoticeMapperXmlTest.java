package com.office.nadoham.goal.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 알림 매퍼 XML 이 읽히는지, 대상·재시도 조건이 맞는지 확인한다. 실제 쿼리 실행은 MySQL 이 있는 환경에서 런타임으로 확인한다.
class GoalNoticeMapperXmlTest {

    private static final String NAMESPACE = "com.office.nadoham.goal.mapper.GoalNoticeMapper";

    private Configuration config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Configuration();
        String resource = "mybatis/mappers/goal-notice-mapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
    }

    private BoundSql bound(String id, Map<String, Object> params) {
        return config.getMappedStatement(NAMESPACE + "." + id).getBoundSql(params);
    }

    private static String normalized(BoundSql sql) {
        return sql.getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("활동 알림 대상: 알림을 켜고 텔레그램이 연동된 USER 의 진행 중 목표의 예정 활동만, 시각 범위 안에서")
    void dueReminders() {
        Map<String, Object> params = new HashMap<>();
        params.put("from", LocalDateTime.of(2026, 11, 23, 19, 0));
        params.put("to", LocalDateTime.of(2026, 11, 23, 19, 30));
        params.put("maxAttempts", 3);
        params.put("retryBefore", LocalDateTime.of(2026, 11, 23, 19, 27));
        BoundSql sql = bound("selectDueReminders", params);
        String text = normalized(sql);

        assertThat(text).contains("a.STATUS = 'PLANNED'").contains("g.STATUS = 'ACTIVE'");
        assertThat(text).contains("m.GOAL_NOTIFY_YN = 1").contains("m.TELEGRAM_CHAT_ID IS NOT NULL").contains("au.ROLE_NAME = 'USER'");
        assertThat(text).contains("TIMESTAMP(a.ACT_DATE, a.START_TIME) BETWEEN ? AND ?");
    }

    @Test
    @DisplayName("활동 알림 대상: 같은 활동·같은 시각의 기록이 있으면 빼되 실패했고 시도가 남았고 대기 시간이 지난 것은 다시 대상이다")
    void dueRemindersExcludeHandledButKeepRetryable() {
        Map<String, Object> params = new HashMap<>();
        params.put("from", LocalDateTime.now());
        params.put("to", LocalDateTime.now());
        params.put("maxAttempts", 3);
        params.put("retryBefore", LocalDateTime.now());
        BoundSql sql = bound("selectDueReminders", params);
        String text = normalized(sql);

        assertThat(text).contains("NOT EXISTS ( SELECT 1 FROM GOAL_NOTICE n WHERE n.KIND = 'REMINDER'")
                .contains("n.REF_NO = a.NO").contains("n.SCHEDULED_AT = TIMESTAMP(a.ACT_DATE, a.START_TIME)")
                .contains("NOT ( n.STATUS = 'FAILED' AND n.ATTEMPTS < ? AND n.LAST_ATTEMPT_AT <= ? )");
        assertThat(sql.getParameterMappings()).extracting(m -> m.getProperty())
                .containsExactly("from", "to", "maxAttempts", "retryBefore");
    }

    @Test
    @DisplayName("알림 대상 쿼리들은 비밀번호·메일·전화번호를 조회하지 않는다")
    void doesNotSelectSecrets() {
        for (String id : new String[]{"selectDueReminders", "selectNoticeGoals"}) {
            Map<String, Object> params = new HashMap<>();
            params.put("from", LocalDateTime.now());
            params.put("to", LocalDateTime.now());
            params.put("maxAttempts", 3);
            params.put("retryBefore", LocalDateTime.now());
            String text = normalized(bound(id, params)).toUpperCase();

            assertThat(text).doesNotContain("PW").doesNotContain("MAIL").doesNotContain("PHONE");
        }
    }

    @Test
    @DisplayName("회고 후보 목표: 알림을 켜고 텔레그램이 연동된 USER 의 진행 중 목표")
    void noticeGoals() {
        String text = normalized(bound("selectNoticeGoals", new HashMap<>()));

        assertThat(text).contains("g.STATUS = 'ACTIVE'").contains("m.GOAL_NOTIFY_YN = 1")
                .contains("m.TELEGRAM_CHAT_ID IS NOT NULL").contains("au.ROLE_NAME = 'USER'");
    }

}
