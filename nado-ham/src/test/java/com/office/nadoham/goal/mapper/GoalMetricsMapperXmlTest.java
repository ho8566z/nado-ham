package com.office.nadoham.goal.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// DB 없이 지표 매퍼 XML 이 읽히는지와 핵심 조건을 확인한다. 실제 쿼리 실행은 MySQL 이 있는 환경에서 런타임으로 확인한다.
class GoalMetricsMapperXmlTest {

    private static final String NAMESPACE = "com.office.nadoham.goal.mapper.GoalMetricsMapper";

    private Configuration config;

    @BeforeEach
    void setUp() throws Exception {
        config = new Configuration();
        String resource = "mybatis/mappers/goal-metrics-mapper.xml";
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
        }
    }

    private String sql(String id) {
        Map<String, Object> params = new HashMap<>();
        params.put("today", LocalDate.of(2026, 11, 22));
        BoundSql bound = config.getMappedStatement(NAMESPACE + "." + id).getBoundSql(params);

        return bound.getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("놓친 사용자: MISSED_DATE 또는 지난 예정 활동(일시 중지 목표 제외)의 가장 이른 날짜, 7일 안 완료 여부")
    void missedUsers() {
        String text = sql("selectMissedUsers");

        assertThat(text).contains("WHEN a.MISSED_DATE IS NOT NULL THEN a.MISSED_DATE");
        assertThat(text).contains("a.STATUS = 'PLANNED' AND g.STATUS <> 'PAUSED' AND a.ACT_DATE < ?");
        assertThat(text).contains("a2.STATUS IN ('DONE', 'SHORT_DONE')")
                .contains("DATE(a2.DONE_AT) > t.first_miss")
                .contains("DATE(a2.DONE_AT) <= DATE_ADD(t.first_miss, INTERVAL 7 DAY)");
        assertThat(text).contains("t.first_miss IS NOT NULL");
    }

    @Test
    @DisplayName("첫 활동: 사용자의 첫 목표(가장 작은 번호)에서 날짜·시각이 가장 이른 활동 한 건")
    void firstActivities() {
        String text = sql("selectFirstActivities");

        assertThat(text).contains("MIN(g2.NO)").contains("ORDER BY a2.ACT_DATE, a2.START_TIME, a2.NO LIMIT 1");
    }

    @Test
    @DisplayName("과정 마무리·활동 상태 집계 쿼리가 있고 비밀번호·메일 같은 컬럼은 조회하지 않는다")
    void courseUsersAndCounts() {
        assertThat(sql("selectCourseUsers")).contains("MAX(g.STATUS = 'DONE')").contains("g.END_DATE < ?").contains("GROUP BY g.OWNER_ID");
        assertThat(sql("selectActivityStatusCounts")).contains("COUNT(*)").contains("GROUP BY a.STATUS");

        for (String id : new String[]{"selectMissedUsers", "selectFirstActivities", "selectCourseUsers", "selectActivityStatusCounts"}) {
            assertThat(sql(id).toUpperCase()).doesNotContain("PW").doesNotContain("MAIL").doesNotContain("PHONE");
        }
    }

}
