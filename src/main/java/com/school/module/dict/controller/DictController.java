package com.school.module.dict.controller;

import com.school.common.result.Result;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;
import com.school.module.dict.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 字典管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /** 单次批量请求最多接受的字典类型数，防止把整张表拖回去 */
    private static final int MAX_BATCH_TYPES = 20;

    /**
     * 查询字典类型列表
     *
     * ponytail: 字典类型是低基数配置数据，直接全量返回；量级失控再加分页
     */
    @GetMapping("/types")
    public Result<List<DictType>> listDictTypes() {
        return Result.success(dictService.list());
    }

    /**
     * 根据字典类型查询字典数据
     */
    @GetMapping("/data/{dictType}")
    public Result<List<DictData>> getDictDataByType(@PathVariable String dictType) {
        List<DictData> data = dictService.getDictDataByType(dictType);
        return Result.success(data);
    }

    /**
     * 一次取多种字典：GET /dict/data/batch?types=gender,student_status
     * <p>
     * ponytail: 字面路径 /data/batch 比 /data/{dictType} 更具体，Spring 会优先匹配它，
     * 因此 "batch" 不能再用作字典类型编码。
     */
    @GetMapping("/data/batch")
    public Result<Map<String, List<DictData>>> getDictDataBatch(@RequestParam("types") String types) {
        List<String> typeList = Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_BATCH_TYPES)
                .toList();
        return Result.success(dictService.getDictDataByTypes(typeList));
    }

    // ponytail: 字典是全系统共享的（没有 school_id），改动会影响所有学校的下拉与展示，写操作只放给超管；
    // 读接口保持登录即可，前端各业务页下拉都要用。

    /**
     * 创建字典类型
     */
    @PostMapping("/types")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> createDictType(@RequestBody DictType dictType) {
        dictService.createDictType(dictType);
        return Result.success();
    }

    /**
     * 创建字典数据
     */
    @PostMapping("/data")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> createDictData(@RequestBody DictData dictData) {
        dictService.createDictData(dictData);
        return Result.success();
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/types/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> updateDictType(@PathVariable Long id, @RequestBody DictType dictType) {
        dictType.setId(id);
        dictService.updateDictType(dictType);
        return Result.success();
    }

    /**
     * 更新字典数据
     */
    @PutMapping("/data/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> updateDictData(@PathVariable Long id, @RequestBody DictData dictData) {
        dictData.setId(id);
        dictService.updateDictData(dictData);
        return Result.success();
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> deleteDictType(@PathVariable Long id) {
        dictService.deleteDictType(id);
        return Result.success();
    }

    /**
     * 删除字典数据
     */
    @DeleteMapping("/data/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> deleteDictData(@PathVariable Long id) {
        dictService.deleteDictData(id);
        return Result.success();
    }

}
