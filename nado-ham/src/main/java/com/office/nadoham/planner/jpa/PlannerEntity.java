package com.office.nadoham.planner.jpa;

import com.office.nadoham.planner.PlannerDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.office.nadoham.planner.util.StartTimes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PLAN")
public class PlannerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NO")
    private int planNo;

    @Column(name = "ORI_NO")
    private int planOriNo;

    @Column(name = "OWNER_ID")
    private String planOwnerId;

    @Column(name = "ORI_OWNER_ID")
    private String planOriOwnerId;

    @Column(name = "YEAR")
    private int planYear;

    @Column(name = "MONTH")
    private int planMonth;

    @Column(name = "DATE")
    private int planDate;

    @Column(name = "TITLE")
    private String planTitle;

    @Column(name = "BODY")
    private String planBody;

    @Column(name = "IMG_NAME")
    private String planImgName;

    // 시작 시간(선택). 없으면 NULL
    @Column(name = "START_TIME")
    private LocalTime planStartTime;

    // 종료 시간(선택). 시작 시간이 있을 때만 쓰고 같은 날 안에서 시작보다 뒤다. 없으면 NULL
    @Column(name = "END_TIME")
    private LocalTime planEndTime;

    @Column(name = "REG_DATE")
    private LocalDateTime planRegDate;

    @Column(name = "MOD_DATE")
    private LocalDateTime planModDate;

    @PrePersist
    private void prePersist() {
        planRegDate = LocalDateTime.now();
        planModDate = planRegDate;
    }

    @PreUpdate
    private void preUpdate() {
        planModDate = LocalDateTime.now();
    }

    public PlannerDto toDto() {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        return PlannerDto.builder()
                .no(planNo)
                .ori_no(planOriNo)
                .owner_id(planOwnerId)
                .ori_owner_id(planOriOwnerId)
                .year(planYear)
                .month(planMonth)
                .date(planDate)
                .title(planTitle)
                .body(planBody)
                .img_name(planImgName)
                .start_time(StartTimes.format(planStartTime))
                .end_time(StartTimes.format(planEndTime))
                .reg_date(planRegDate != null ? planRegDate.format(formatter) : null)
                .mod_date(planModDate != null ? planModDate.format(formatter) : null)
                .build();
    }

}
