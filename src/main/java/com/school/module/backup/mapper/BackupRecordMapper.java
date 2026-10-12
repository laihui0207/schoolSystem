package com.school.module.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.backup.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份记录 Mapper
 * <p>
 * ponytail: 查询全部用 LambdaQueryWrapper，不写 XML——sys_backup 没有 school_id，不涉及租户 JOIN，
 * 没有任何需要手写 SQL 的地方。
 *
 * @author School System
 */
@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {
}
