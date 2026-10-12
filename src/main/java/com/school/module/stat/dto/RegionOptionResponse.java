package com.school.module.stat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 区域下拉项：已配置 region_code 的去重列表及其学校数
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionOptionResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String regionCode;

    /**
     * 该区域内启用状态的学校数
     */
    private Integer schoolCount;

}
