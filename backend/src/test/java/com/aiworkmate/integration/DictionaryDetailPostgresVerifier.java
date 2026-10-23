package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.DataDictionaryItemMapper;
import com.aiworkmate.mapper.DataDictionaryItemUsageMapper;
import com.aiworkmate.mapper.DataDictionaryTypeMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.DataDictionaryServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Verifies actual tenant/deletion predicates, not only mocked Mapper return values. */
final class DictionaryDetailPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        long tenant = tenant(jdbc, "DICT_DETAIL_A"); long foreignTenant = tenant(jdbc, "DICT_DETAIL_B");
        long user = user(jdbc, tenant, "dict-detail-a"); long otherUser = user(jdbc, foreignTenant, "dict-detail-b");
        long type = type(jdbc, tenant, user); long foreignType = type(jdbc, foreignTenant, otherUser);
        long item = item(jdbc, tenant, type, user); long foreignItem = item(jdbc, foreignTenant, foreignType, otherUser);
        jdbc.update("INSERT INTO data_dictionary_item_usage(tenant_id,dictionary_item_id,resource_type,resource_id) VALUES (?,?,'TEST','fixture')", tenant, item);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(DataDictionaryTypeMapper.class, DataDictionaryItemMapper.class, DataDictionaryItemUsageMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class); var audit = mock(BusinessAuditService.class);
        when(access.resolveActiveUser(user)).thenReturn(actor(user, tenant, List.of("route:dictionary")));
        when(access.resolveActiveUser(otherUser)).thenReturn(actor(otherUser, foreignTenant, List.of("route:dictionary")));
        var service = new DataDictionaryServiceImpl(session.getMapper(DataDictionaryTypeMapper.class),
                session.getMapper(DataDictionaryItemMapper.class), session.getMapper(DataDictionaryItemUsageMapper.class), access, audit);

        var typeDetail = service.typeDetail(user, type);
        assertThat(typeDetail.id()).isEqualTo(type); assertThat(typeDetail.version()).isEqualTo(3);
        assertThat(typeDetail.status()).isEqualTo("DISABLED"); assertThat(typeDetail.canManage()).isFalse();
        assertThat(typeDetail.itemCount()).isOne(); assertThat(typeDetail.activeItemCount()).isZero();
        var detail = service.itemDetail(user, item);
        assertThat(detail.dictionaryType().id()).isEqualTo(type);
        assertThat(detail.item().dictionaryTypeId()).isEqualTo(type); assertThat(detail.item().id()).isEqualTo(item);
        assertThat(detail.item().version()).isEqualTo(4); assertThat(detail.item().usageCount()).isOne();
        assertThat(detail.item().canManage()).isFalse(); assertThat(detail.item().canDelete()).isFalse();
        assertDenied(() -> service.typeDetail(user, foreignType), "RESOURCE_NOT_FOUND");
        assertDenied(() -> service.itemDetail(user, foreignItem), "RESOURCE_NOT_FOUND");
        assertDenied(() -> service.typeDetail(otherUser, type), "RESOURCE_NOT_FOUND");
        assertDenied(() -> service.itemDetail(otherUser, item), "RESOURCE_NOT_FOUND");

        when(access.resolveActiveUser(user)).thenReturn(actor(user, tenant, List.of("route:dictionary", "dictionary:manage")));
        assertThat(service.itemDetail(user, item).item().canManage()).isTrue();
        assertThat(service.itemDetail(user, item).item().canDelete()).isFalse();
        when(access.resolveActiveUser(user)).thenReturn(actor(user, tenant, List.of("dictionary:manage")));
        assertDenied(() -> service.typeDetail(user, type), "PERMISSION_DENIED");
        assertDenied(() -> service.itemDetail(user, item), "PERMISSION_DENIED");
        when(access.resolveActiveUser(user)).thenReturn(actor(user, tenant, List.of("route:dictionary")));
        jdbc.update("UPDATE data_dictionary_type SET deleted=TRUE WHERE id=?", type);
        assertDenied(() -> service.typeDetail(user, type), "RESOURCE_NOT_FOUND");
        assertDenied(() -> service.itemDetail(user, item), "RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE data_dictionary_type SET deleted=FALSE WHERE id=?", type);
        jdbc.update("UPDATE data_dictionary_item SET deleted=TRUE WHERE id=?", item);
        assertDenied(() -> service.itemDetail(user, item), "RESOURCE_NOT_FOUND");
        assertThat(service.typeDetail(user, type).itemCount()).isZero();
        assertDenied(() -> service.typeDetail(user, Long.MAX_VALUE), "RESOURCE_NOT_FOUND");
        assertDenied(() -> service.itemDetail(user, Long.MAX_VALUE), "RESOURCE_NOT_FOUND");
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            assertDenied(() -> service.typeDetail(user, invalid), "REQUEST_INVALID");
            assertDenied(() -> service.itemDetail(user, invalid), "REQUEST_INVALID");
        }
        when(access.resolveActiveUser(user)).thenReturn(null);
        assertDenied(() -> service.typeDetail(user, type), "AUTH_REQUIRED");
        assertDenied(() -> service.itemDetail(user, item), "AUTH_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT version FROM data_dictionary_type WHERE id=?", Integer.class, type)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT version FROM data_dictionary_item WHERE id=?", Integer.class, item)).isEqualTo(4);
        verifyNoInteractions(audit);
    }

    private static void assertDenied(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
    private static ResolvedUserAccess actor(long user, long tenant, List<String> permissions) {
        return new ResolvedUserAccess(user, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), permissions, List.of("TENANT"), 1L);
    }
    private static long tenant(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?, 'Dictionary detail fixture') RETURNING id", Long.class, code);
    }
    private static long user(JdbcTemplate jdbc, long tenant, String name) {
        return jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,status) VALUES (?,'test-only',?, ?,1) RETURNING id",
                Long.class, name, name + "@example.invalid", tenant);
    }
    private static long type(JdbcTemplate jdbc, long tenant, long user) {
        return jdbc.queryForObject("INSERT INTO data_dictionary_type(tenant_id,code,name,status,version,created_by,updated_by) VALUES (?,'DETAIL_FIXTURE','Type','DISABLED',3,?,?) RETURNING id",
                Long.class, tenant, user, user);
    }
    private static long item(JdbcTemplate jdbc, long tenant, long type, long user) {
        return jdbc.queryForObject("INSERT INTO data_dictionary_item(tenant_id,dictionary_type_id,item_value,label,status,version,created_by,updated_by) VALUES (?,?,'FIRST','Item','DISABLED',4,?,?) RETURNING id",
                Long.class, tenant, type, user, user);
    }
}
