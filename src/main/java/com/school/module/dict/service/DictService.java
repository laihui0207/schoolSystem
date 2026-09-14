package com.school.module.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;

import java.util.List;

/**
 * 字典服务接口
 *
 * @author School System
 */
public interface DictService extends IService<DictType> {

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据列表
     */
    List<DictData> getDictDataByType(String dictType);

    /**
     * 创建字典类型
     *
     * @param dictType 字典类型
     * @return 是否成功
     */
    boolean createDictType(DictType dictType);

    /**
     * 创建字典数据
     *
     * @param dictData 字典数据
     * @return 是否成功
     */
    boolean createDictData(DictData dictData);

    /**
     * 更新字典类型
     *
     * @param dictType 字典类型
     * @return 是否成功
     */
    boolean updateDictType(DictType dictType);

    /**
     * 更新字典数据
     *
     * @param dictData 字典数据
     * @return 是否成功
     */
    boolean updateDictData(DictData dictData);

    /**
     * 删除字典类型
     *
     * @param id 字典类型ID
     * @return 是否成功
     */
    boolean deleteDictType(Long id);

    /**
     * 删除字典数据
     *
     * @param id 字典数据ID
     * @return 是否成功
     */
    boolean deleteDictData(Long id);

}
