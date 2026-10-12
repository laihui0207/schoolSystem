package com.school.module.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;
import com.school.module.dict.mapper.DictDataMapper;
import com.school.module.dict.mapper.DictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class DictServiceImpl extends ServiceImpl<DictTypeMapper, DictType> implements DictService {

    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;

    @Override
    public List<DictData> getDictDataByType(String dictType) {
        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictData::getDictType, dictType)
                .eq(DictData::getStatus, 0)
                .orderByAsc(DictData::getDictSort);
        return dictDataMapper.selectList(wrapper);
    }

    @Override
    public Map<String, List<DictData>> getDictDataByTypes(Collection<String> dictTypes) {
        // ponytail: 用 LinkedHashMap 按请求顺序先预置空列表，前端能区分"该类型没数据"和"没请求这个类型"；
        // 一条 IN 查询取回全部再分组，而不是每种类型各查一次。
        Map<String, List<DictData>> grouped = new LinkedHashMap<>();
        if (dictTypes == null || dictTypes.isEmpty()) {
            return grouped;
        }
        List<String> requested = dictTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        requested.forEach(type -> grouped.put(type, new ArrayList<>()));
        if (requested.isEmpty()) {
            return grouped;
        }

        LambdaQueryWrapper<DictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DictData::getDictType, requested)
                .eq(DictData::getStatus, 0)
                .orderByAsc(DictData::getDictType, DictData::getDictSort);
        for (DictData row : dictDataMapper.selectList(wrapper)) {
            grouped.computeIfAbsent(row.getDictType(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    @Override
    public boolean createDictType(DictType dictType) {
        return dictTypeMapper.insert(dictType) > 0;
    }

    @Override
    public boolean createDictData(DictData dictData) {
        return dictDataMapper.insert(dictData) > 0;
    }

    @Override
    public boolean updateDictType(DictType dictType) {
        return dictTypeMapper.updateById(dictType) > 0;
    }

    @Override
    public boolean updateDictData(DictData dictData) {
        return dictDataMapper.updateById(dictData) > 0;
    }

    @Override
    public boolean deleteDictType(Long id) {
        return dictTypeMapper.deleteById(id) > 0;
    }

    @Override
    public boolean deleteDictData(Long id) {
        return dictDataMapper.deleteById(id) > 0;
    }

}
