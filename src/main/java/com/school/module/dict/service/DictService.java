package com.school.module.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.entity.DictType;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * 一次查询多种字典类型的数据（仅 status=0，按 dict_sort 升序）
     *
     * @param dictTypes 字典类型编码集合，空白项忽略
     * @return 类型编码 → 字典数据列表；请求过的类型即使没有数据也会出现（空列表），顺序与请求一致
     */
    Map<String, List<DictData>> getDictDataByTypes(Collection<String> dictTypes);

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
