package com.aiworkmate.service.impl;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.AiUsageMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AiUsageServiceImplTest {
 private final AiUsageMapper mapper=mock(AiUsageMapper.class);
 private final UserAccessService access=mock(UserAccessService.class);
 private final AiUsageServiceImpl service=new AiUsageServiceImpl(mapper,access);
 @Test void deniesNonAdminsBeforeQueryingUsage() {
  when(access.resolveActiveUser(1L)).thenReturn(new ResolvedUserAccess(1L,"employee","EMPLOYEE",List.of()));
  assertThatThrownBy(() -> service.report(1L,7)).isInstanceOf(BusinessException.class);
  verifyNoInteractions(mapper);
 }
 @Test void boundsReportPeriod() {
  when(access.resolveActiveUser(1L)).thenReturn(new ResolvedUserAccess(1L,"admin","SUPER_ADMIN",List.of("access:manage")));
  assertThatThrownBy(() -> service.report(1L,365)).isInstanceOf(BusinessException.class);
  service.report(1L,7); verify(mapper).buckets(7);
 }
 @Test void keepsMissingUsageNullAndDoesNotDisruptChatIfStorageFails() {
  service.record(2,"model","CANCELLED",15,null);
  verify(mapper).insert(2,"model","CANCELLED",15,null,null,null);
  doThrow(new IllegalStateException()).when(mapper).insert(2,"model","ERROR",20,null,null,null);
  assertThatCode(() -> service.record(2,"model","ERROR",20,null)).doesNotThrowAnyException();
 }
}
