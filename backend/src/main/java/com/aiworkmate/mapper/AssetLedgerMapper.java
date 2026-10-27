package com.aiworkmate.mapper;

import com.aiworkmate.entity.AssetLedger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 资产台账 Mapper。
 *
 * <p>多条件查询在 Service 层用 {@code LambdaQueryWrapper} 完成，
 * 此处仅保留 {@link BaseMapper} 提供的标准 CRUD。
 */
@Mapper
public interface AssetLedgerMapper extends BaseMapper<AssetLedger> {
    @Select("SELECT * FROM asset_ledger WHERE tenant_id=#{tenantId} AND id=#{id} AND deleted=FALSE FOR UPDATE")
    AssetLedger lockMetadata(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
