package com.aiworkmate.mapper;
import com.aiworkmate.dto.AiUsageBucket;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface AiUsageMapper {
 @Insert("INSERT INTO ai_call_usage(channel_id,model,status,duration_ms,input_tokens,output_tokens,total_tokens) VALUES(#{channel},#{model},#{status},#{duration},#{input},#{output},#{total})")
 void insert(@Param("channel") long channel,@Param("model") String model,@Param("status") String status,@Param("duration") long duration,
        @Param("input") Long input,@Param("output") Long output,@Param("total") Long total);
 @Select("SELECT to_char(created_at AT TIME ZONE 'Asia/Shanghai','YYYY-MM-DD') AS day,channel_id,model,count(*) AS calls,count(*) FILTER(WHERE status='SUCCESS') AS successes,count(*) FILTER(WHERE status='CANCELLED') AS cancelled,sum(duration_ms)::double precision AS duration_ms,count(total_tokens) AS reported_calls,sum(input_tokens) AS input_tokens,sum(output_tokens) AS output_tokens,sum(total_tokens) AS total_tokens,(SELECT c.channel_name FROM ai_provider_configuration c WHERE c.id=u.channel_id) AS channel_name FROM ai_call_usage u WHERE created_at >= CURRENT_TIMESTAMP - (#{days} * INTERVAL '1 day') GROUP BY day,channel_id,model ORDER BY day,channel_id,model")
 List<AiUsageBucket> buckets(int days);
}
