package com.school.module.dict.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;
import com.school.module.dict.mapper.DictDataMapper;
import com.school.module.dict.mapper.DictTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
