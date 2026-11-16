package com.aiworkmate.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named="ai.channels.db.url", matches=".+")
class AiChannelsMigrationIT {
 @Test void migratesEmptyDatabaseAndReplaysChannelMigration() throws Exception {
  String url=System.getProperty("ai.channels.db.url");
  String user=System.getenv().getOrDefault("DB_USERNAME","postgres");
  String password=System.getenv().getOrDefault("DB_PASSWORD","postgres");
  var flyway=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load();
  flyway.migrate(); flyway.validate();
  try(var connection=DriverManager.getConnection(url,user,password); var statement=connection.createStatement()) {
   String sql=Files.readString(Path.of("src/main/resources/db/migration/V202611160900__ai_provider_channels.sql"));
   statement.execute(sql); statement.execute(sql);
   String usageSql=Files.readString(Path.of("src/main/resources/db/migration/V202611162130__ai_call_usage.sql"));
   statement.execute(usageSql); statement.execute(usageSql);
   statement.execute("INSERT INTO ai_provider_configuration(id,channel_name,base_url,model,encrypted_api_key,updated_by) VALUES(2,'Second','http://localhost/v1','same','test-only',1) ON CONFLICT DO NOTHING");
   try(var rows=statement.executeQuery("SELECT channel_name FROM ai_provider_configuration WHERE id=2")) {
    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Second");
   }
  }
  flyway.validate();
  var configuration=new org.apache.ibatis.session.Configuration();
  configuration.setMapUnderscoreToCamelCase(true);
  configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("usage-test",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
      new org.apache.ibatis.datasource.unpooled.UnpooledDataSource("org.postgresql.Driver",url,user,password)));
  configuration.addMapper(com.aiworkmate.mapper.AiUsageMapper.class);
  try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration).openSession(true)) {
   var mapper=session.getMapper(com.aiworkmate.mapper.AiUsageMapper.class);
   String model="usage-"+java.util.UUID.randomUUID();
   mapper.insert(2,model,"SUCCESS",10,10L,2L,12L);
   mapper.insert(2,model,"CANCELLED",10,null,null,null);
   var bucket=mapper.buckets(7).stream().filter(item -> item.model().equals(model)).findFirst().orElseThrow();
   assertThat(bucket.calls()).isEqualTo(2); assertThat(bucket.successes()).isEqualTo(1);
   assertThat(bucket.cancelled()).isEqualTo(1); assertThat(bucket.reportedCalls()).isEqualTo(1);
   assertThat(bucket.totalTokens()).isEqualTo(12); assertThat(bucket.durationMs()).isEqualTo(20);
   assertThat(bucket.channelName()).isEqualTo("Second");
  }
 }
}
