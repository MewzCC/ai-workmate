import org.flywaydb.core.Flyway;
import java.nio.file.*;
import java.sql.*;
public class VerifyAiConfigurationMigration {
 public static void main(String[] args) throws Exception {
  String user=System.getenv().getOrDefault("DB_USERNAME","postgres");
  String password=System.getenv().getOrDefault("DB_PASSWORD","postgres");
  String url="jdbc:postgresql://postgres:5432/ai_config_verify_20261115";
  Flyway flyway=Flyway.configure().dataSource(url,user,password).locations("filesystem:/build/src/main/resources/db/migration").load();
  flyway.migrate(); flyway.validate();
  String sql=Files.readString(Path.of("/build/src/main/resources/db/migration/V202611152015__online_ai_configuration.sql"));
  try(var connection=DriverManager.getConnection(url,user,password); var statement=connection.createStatement()) {
   statement.execute(sql); statement.execute(sql);
   String capabilities=Files.readString(Path.of("/build/src/main/resources/db/migration/V202611152200__ai_model_capabilities.sql"));
   statement.execute(capabilities); statement.execute(capabilities);
  }
  flyway.validate();
  System.out.println("EMPTY_DATABASE_AND_REPLAY_VALIDATED migrations="+flyway.info().applied().length);
 }
}
