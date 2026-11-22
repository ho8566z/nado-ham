package com.office.nadoham.planner;

import com.office.nadoham.planner.jpa.PlannerEntity;
import com.office.nadoham.planner.util.StartTimes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerDto {

    private int no;
    private int ori_no;
    private String owner_id;
    private String ori_owner_id;
    private int year;
    private int month;
    private int date;
    private String title;
    private String body;
    private String img_name;
    private String start_time;      // 시작 시간(HH:mm, 선택)
    private String end_time;        // 종료 시간(HH:mm, 선택. 시작 시간이 있을 때만)
    private String reg_date;
    private String mod_date;

    public PlannerEntity toEntity() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return PlannerEntity.builder()
                .planNo(no)
                .planOriNo(ori_no)
                .planOwnerId(owner_id)
                .planOriOwnerId(ori_owner_id)
                .planYear(year)
                .planMonth(month)
                .planDate(date)
                .planTitle(title)
                .planBody(body)
                .planImgName(img_name)
                .planStartTime(StartTimes.toLocalTime(start_time))
                .planEndTime(StartTimes.toLocalTime(end_time))
                .planRegDate(reg_date != null ? LocalDateTime.parse(reg_date, formatter) : null)
                .planModDate(mod_date != null ? LocalDateTime.parse(mod_date, formatter) : null)
                .build();

    }

}
