package com.aiworkmate.mapper;
import org.apache.ibatis.annotations.*;
import java.util.Map;
@Mapper
public interface AiConfigurationMapper {
 @Select("SELECT id, channel_name, base_url, model, encrypted_api_key, version, models::text AS models FROM ai_provider_configuration ORDER BY id")
 java.util.List<Map<String,Object>> channels();
 @Select("SELECT id, channel_name, base_url, model, encrypted_api_key, version, models::text AS models FROM ai_provider_configuration WHERE id=#{id}")
 Map<String,Object> channel(long id);
 @Insert("INSERT INTO ai_provider_configuration(id,channel_name,base_url,model,encrypted_api_key,updated_by,models) VALUES(#{id},#{name},#{url},#{model},#{key},#{user},CAST(#{models} AS jsonb)) ON CONFLICT(id) DO NOTHING")
 int insertChannel(@Param("id") long id,@Param("name") String name,@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("models") String models);
 @Update("UPDATE ai_provider_configuration SET channel_name=#{name},base_url=#{url},model=#{model},encrypted_api_key=#{key},models=CAST(#{models} AS jsonb),version=version+1,updated_by=#{user},updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND version=#{version}")
 int updateChannel(@Param("id") long id,@Param("name") String name,@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("version") long version,@Param("models") String models);
 @Select("SELECT base_url, model, encrypted_api_key, version, models::text AS models FROM ai_provider_configuration WHERE id=1")
 Map<String,Object> load();
 @Insert("INSERT INTO ai_provider_configuration(id,base_url,model,encrypted_api_key,updated_by,models) VALUES(1,#{url},#{model},#{key},#{user},CAST(#{models} AS jsonb)) ON CONFLICT(id) DO NOTHING")
 int insert(@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("models") String models);
 @Update("UPDATE ai_provider_configuration SET base_url=#{url},model=#{model},encrypted_api_key=#{key},models=CAST(#{models} AS jsonb),version=version+1,updated_by=#{user},updated_at=CURRENT_TIMESTAMP WHERE id=1 AND version=#{version}")
 int update(@Param("url") String url,@Param("model") String model,@Param("key") String key,@Param("user") Long user,@Param("version") long version,@Param("models") String models);
}
