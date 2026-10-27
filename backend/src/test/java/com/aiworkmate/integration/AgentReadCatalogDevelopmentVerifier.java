package com.aiworkmate.integration;

import com.aiworkmate.AiWorkMateApplication;
import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.registry.ToolCatalog;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit local-only maintenance entry point; never selected by the test runner. */
public final class AgentReadCatalogDevelopmentVerifier {
    private AgentReadCatalogDevelopmentVerifier() { }

    public static void main(String[] args) {
        if (args.length != 0 || !"true".equals(System.getenv("AGENT_DEVELOPMENT_VERIFY"))
                || !"jdbc:postgresql://localhost:5432/ai_workmate_dev".equals(System.getenv("AGENT_TEST_DB_URL"))) {
            throw new IllegalArgumentException("Explicit opt-in and the exact local development database are required");
        }
        // Reuse the proven owner fixture for a disposable Redis. Its disposable DB is never used by the app.
        // Thus notification consumers cannot consume the user's Redis queue while verifying a real app restart.
        try (var isolated = new AgentIntegrationEnvironment()) {
            Map<String,Object> properties = new LinkedHashMap<>();
            isolated.register((name,value) -> properties.put(name,value.get()));
            properties.put("spring.datasource.url",System.getenv("AGENT_TEST_DB_URL"));
            properties.put("spring.datasource.username",System.getenv().getOrDefault("AGENT_TEST_DB_USERNAME","postgres"));
            properties.put("spring.datasource.password",System.getenv().getOrDefault("AGENT_TEST_DB_PASSWORD","postgres"));
            properties.put("spring.datasource.hikari.connection-init-sql","SET search_path TO public");
            properties.put("spring.flyway.default-schema","public");
            properties.put("spring.flyway.schemas","public");
            properties.put("server.port","0");
            properties.put("logging.level.root","WARN");
            properties.put("logging.level.com.aiworkmate","WARN");
            properties.put("spring.ai.openai.api-key","development-only-unconfigured");
            properties.put("spring.ai.openai.base-url","http://127.0.0.1:1");
            String before;
            try (var connection = java.sql.DriverManager.getConnection(System.getenv("AGENT_TEST_DB_URL"),
                    (String)properties.get("spring.datasource.username"),(String)properties.get("spring.datasource.password"))) {
                before = switches(new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true)));
            } catch (java.sql.SQLException failure) { throw new IllegalStateException("Local database preflight failed",failure); }
            for (int cycle=1;cycle<=2;cycle++) {
                try (var app = new SpringApplicationBuilder(AiWorkMateApplication.class,NoScheduledJobs.class)
                        .initializers(context -> {
                            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("localDevelopmentVerification",properties));
                            // A standalone Boot launch has no Spring Test bootstrapper to exclude test configurations.
                            context.getBeanFactory().registerSingleton("maintenanceTestClassesFilter",new VerificationTypeExcludeFilter());
                        }).run()) {
                    var jdbc = app.getBean(JdbcTemplate.class);
                    require("ai_workmate_dev".equals(jdbc.queryForObject("SELECT current_database()",String.class)),"Unexpected database");
                    require("public".equals(jdbc.queryForObject("SELECT current_schema()",String.class)),"Unexpected schema");
                    require(!app.containsBean(NoScheduledJobs.SCHEDULER),"Background jobs must remain disabled");
                    var runtime = app.getBean(AgentRuntimeProperties.class);
                    require(!runtime.isEnabled() && !runtime.isExecutionEnabled() && !runtime.isWriteToolsEnabled(),"Agent execution must remain disabled");
                    var flyway = app.getBean(Flyway.class);
                    flyway.validate();
                    require(flyway.info().pending().length==0,"Pending migrations");
                    require(flyway.migrate().migrationsExecuted==0,"Restart attempted additional migrations");
                    require(jdbc.queryForObject("SELECT count(*) FROM rbac_route WHERE enabled AND route_type='PAGE' AND permission_code<>'route:' || route_key",Integer.class)==0,"Route permission mismatch");
                    for (String code : new String[]{"visitor.query","accessGovernance.query","dataPermission.query","aiPermission.query","attendance.query"}) {
                        require(app.getBean(ToolCatalog.class).find(code).orElseThrow().schemaHash().equals(jdbc.queryForObject(
                                "SELECT schema_hash FROM agent_tool WHERE tenant_id IS NULL AND code=?",String.class,code)),"Tool schema mismatch");
                    }
                    require(before.equals(switches(jdbc)),"Release switches changed");
                    require(Boolean.TRUE.equals(jdbc.queryForObject("SELECT required_permissions='[\"leave:read:self\",\"leave:remind\"]'::jsonb FROM agent_tool WHERE tenant_id IS NULL AND code='leave.remind'",Boolean.class)),"Reminder permission contract mismatch");
                    System.out.println("Development restart "+cycle+": verified; pending=0; write switches unchanged");
                }
            }
        }
    }

    private static String switches(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tenant_id,enabled,write_tools_enabled FROM agent_tenant_policy ORDER BY tenant_id").toString()
                + jdbc.queryForList("SELECT id,enabled FROM agent_tool ORDER BY id").toString();
    }
    private static void require(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }

    static final class VerificationTypeExcludeFilter extends org.springframework.boot.context.TypeExcludeFilter {
        private static final String TEST_OUTPUT=AgentReadCatalogDevelopmentVerifier.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        @Override public boolean match(org.springframework.core.type.classreading.MetadataReader reader,
                org.springframework.core.type.classreading.MetadataReaderFactory factory) throws java.io.IOException {
            return reader.getResource().getURL().toExternalForm().startsWith(TEST_OUTPUT);
        }
        @Override public boolean equals(Object other) { return other instanceof VerificationTypeExcludeFilter; }
        @Override public int hashCode() { return VerificationTypeExcludeFilter.class.hashCode(); }
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class NoScheduledJobs {
        static final String SCHEDULER="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
        @Bean static BeanDefinitionRegistryPostProcessor removeScheduledJobs() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    if (!registry.containsBeanDefinition(SCHEDULER)) throw new IllegalStateException("Scheduler configuration changed");
                    registry.removeBeanDefinition(SCHEDULER);
                }
                @Override public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) { }
            };
        }
    }
}
