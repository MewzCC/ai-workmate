package com.aiworkmate.mapper;

import com.aiworkmate.entity.AssetOperation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetOperationMapper extends BaseMapper<AssetOperation> {
    @Select("SELECT * FROM asset_operation WHERE tenant_id=#{tenantId} "
            + "AND operator_user_id=#{userId} AND agent_operation_key=#{operationKey} LIMIT 1")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    AssetOperation findAgentOperation(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                      @Param("operationKey") String operationKey);
}
