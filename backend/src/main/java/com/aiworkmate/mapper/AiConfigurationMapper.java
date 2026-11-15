package com.aiworkmate.mapper;
import org.apache.ibatis.annotations.*;
import java.util.Map;
@Mapper
public interface AiConfigurationMapper {
 @Select("SELECT base_url, model, encrypted_api_key, version, models::text AS models FROM ai_provider_configuration WHERE id=1")
 Map<String,Object> load();
 @Insert("INSERT INTO ai_provider_configuration(id,base_url,model,encrypted_api_key,updated_by,models) VALUES(1,#{url},#{model},#{key},#{user},CAST(#{models} AS jsonb)) ON CONFLICT(id) DO NOTHING")
 int insert(@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("models") String models);
 @Update("UPDATE ai_provider_configuration SET base_url=#{url},model=#{model},encrypted_api_key=#{key},models=CAST(#{models} AS jsonb),version=version+1,updated_by=#{user},updated_at=CURRENT_TIMESTAMP WHERE id=1 AND version=#{version}")
 int update(@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("version") long version,@Param("models") String models);
}
