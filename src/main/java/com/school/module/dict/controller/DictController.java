package com.school.module.dict.controller;

import com.school.common.result.Result;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;
import com.school.module.dict.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 创建字典类型
     */
    @PostMapping("/types")
    public Result<Void> createDictType(@RequestBody DictType dictType) {
        dictService.createDictType(dictType);
        return Result.success();
    }

    /**
     * 创建字典数据
     */
    @PostMapping("/data")
    public Result<Void> createDictData(@RequestBody DictData dictData) {
        dictService.createDictData(dictData);
        return Result.success();
    }

    /**
     * 更新字典类型
     */
    @PutMapping("/types/{id}")
    public Result<Void> updateDictType(@PathVariable Long id, @RequestBody DictType dictType) {
        dictType.setId(id);
        dictService.updateDictType(dictType);
        return Result.success();
    }

    /**
     * 更新字典数据
     */
    @PutMapping("/data/{id}")
    public Result<Void> updateDictData(@PathVariable Long id, @RequestBody DictData dictData) {
        dictData.setId(id);
        dictService.updateDictData(dictData);
        return Result.success();
    }

    /**
     * 删除字典类型
     */
    @DeleteMapping("/types/{id}")
    public Result<Void> deleteDictType(@PathVariable Long id) {
        dictService.deleteDictType(id);
        return Result.success();
    }

    /**
     * 删除字典数据
     */
    @DeleteMapping("/data/{id}")
    public Result<Void> deleteDictData(@PathVariable Long id) {
        dictService.deleteDictData(id);
        return Result.success();
    }

}
