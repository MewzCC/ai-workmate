package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AssetOperationRequest;
import com.aiworkmate.dto.AssetInventoryRequest;
import com.aiworkmate.dto.AssetMaintenanceRequest;
import com.aiworkmate.dto.AssetLedgerResponse;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.entity.AssetLedger;
import com.aiworkmate.entity.AssetOperation;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.VisitorBookingMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.AssetAgentClaimCommand;
import com.aiworkmate.service.model.AssetAgentClaimReceipt;
import com.aiworkmate.service.model.AssetAgentRepairStartCommand;
import com.aiworkmate.service.model.AssetAgentRepairStartReceipt;
import com.aiworkmate.service.model.AssetAgentReturnCommand;
import com.aiworkmate.service.model.AssetAgentReturnReceipt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetLifecycleServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long ASSET_ID = 9L;
    private static final long OWNER_ID = 2002L;

    @Mock private AssetLedgerMapper assetMapper;
    @Mock private AssetOperationMapper operationMapper;
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private MeetingRoomMapper meetingRoomMapper;
    @Mock private MeetingBookingMapper meetingBookingMapper;
    @Mock private VisitorBookingMapper visitorMapper;
    @Mock private SealUsageMapper sealMapper;
    @Mock private UserMapper userMapper;
    @Mock private WorkflowInstanceMapper instanceMapper;
    @Mock private WorkflowTaskMapper taskMapper;
    @Mock private WorkflowActionLogMapper actionLogMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private NotificationService notificationService;

    private AdminAssetsServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(AssetLedger.class, AssetLedgerMapper.class);
        initializeTableMetadata(AssetOperation.class, AssetOperationMapper.class);
        service = new AdminAssetsServiceImpl(assetMapper, operationMapper, accessControlMapper,
                meetingRoomMapper, meetingBookingMapper, visitorMapper, sealMapper, userMapper, instanceMapper,
                taskMapper, actionLogMapper, userAccessService, auditService, notificationService);
    }

    @Test
    void claimMovesIdleAssetToOwnerDepartmentAndWritesHistory() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger before = asset("IDLE", 10L, null, 2);
        AssetLedger after = asset("IN_USE", 20L, OWNER_ID, 3);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(before, after);
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);
        when(userMapper.selectById(OWNER_ID)).thenReturn(owner(20L));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenAnswer(invocation -> {
            AssetOperation operation = invocation.getArgument(0);
            operation.setId(77L);
            return 1;
        });
        when(operationMapper.selectList(any())).thenReturn(List.of());
        when(accessControlMapper.selectDepartments(TENANT_ID)).thenReturn(departments());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(owner(20L)));

        AssetLedgerResponse response = service.claimAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(2, OWNER_ID, 20L, "新员工领用"));

        assertThat(response.status()).isEqualTo("IN_USE");
        assertThat(response.departmentName()).isEqualTo("研发部");
        assertThat(response.ownerName()).isEqualTo("员工甲");
        ArgumentCaptor<AssetOperation> captor = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("CLAIM");
        assertThat(captor.getValue().getFromStatus()).isEqualTo("IDLE");
        assertThat(captor.getValue().getToOwnerUserId()).isEqualTo(OWNER_ID);
    }

    @Test
    void agentClaimUsesTrustedOperatorAndPersistsVersionedOperationKey() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(operationMapper.selectOne(any())).thenReturn(null);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 2));
        when(userMapper.selectById(OWNER_ID)).thenReturn(owner(20L));
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);

        AssetAgentClaimReceipt receipt = service.claimAssetAgent(ACTOR_ID,
                new AssetAgentClaimCommand(ASSET_ID, OWNER_ID, 2, "新员工领用"), "agent-operation");

        assertThat(receipt).isEqualTo(new AssetAgentClaimReceipt(ASSET_ID, "IN_USE", 3));
        ArgumentCaptor<AssetOperation> operation = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(operation.capture());
        assertThat(operation.getValue().getAgentOperationKey()).isEqualTo("agent-operation");
        assertThat(operation.getValue().getSourceVersion()).isEqualTo(2);
        assertThat(operation.getValue().getResultVersion()).isEqualTo(3);
        assertThat(operation.getValue().getToOwnerUserId()).isEqualTo(OWNER_ID);
        verify(auditService).recordTransactional(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void agentClaimReplayReturnsStoredReceiptWithoutRepeatingTheWrite() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetOperation stored = new AssetOperation();
        stored.setAssetId(ASSET_ID);
        stored.setOperationType("CLAIM");
        stored.setToStatus("IN_USE");
        stored.setToOwnerUserId(OWNER_ID);
        stored.setReason("新员工领用");
        stored.setSourceVersion(2);
        stored.setResultVersion(3);
        when(operationMapper.selectOne(any())).thenReturn(stored);

        assertThat(service.claimAssetAgent(ACTOR_ID,
                new AssetAgentClaimCommand(ASSET_ID, OWNER_ID, 2, "新员工领用"), "agent-operation"))
                .isEqualTo(new AssetAgentClaimReceipt(ASSET_ID, "IN_USE", 3));
        verify(assetMapper, never()).update(any(), any());
        verify(operationMapper, never()).insert(any(AssetOperation.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void agentClaimRejectsOperationKeyReuseWithDifferentCommand() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetOperation stored = new AssetOperation();
        stored.setAssetId(ASSET_ID);
        stored.setOperationType("CLAIM");
        stored.setToStatus("IN_USE");
        stored.setToOwnerUserId(OWNER_ID);
        stored.setReason("原原因");
        stored.setSourceVersion(2);
        stored.setResultVersion(3);
        when(operationMapper.selectOne(any())).thenReturn(stored);

        assertThatThrownBy(() -> service.claimAssetAgent(ACTOR_ID,
                new AssetAgentClaimCommand(ASSET_ID, OWNER_ID, 2, "变更原因"), "agent-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void agentClaimRejectsEmployeeOutsideTheAuthenticatedTenant() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(operationMapper.selectOne(any())).thenReturn(null);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 2));
        User otherTenantEmployee = owner(20L);
        otherTenantEmployee.setTenantId(TENANT_ID + 1);
        when(userMapper.selectById(OWNER_ID)).thenReturn(otherTenantEmployee);

        assertThatThrownBy(() -> service.claimAssetAgent(ACTOR_ID,
                new AssetAgentClaimCommand(ASSET_ID, OWNER_ID, 2, null), "agent-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(assetMapper, never()).update(any(), any());
        verify(operationMapper, never()).insert(any(AssetOperation.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void agentReturnClearsOwnerAndPersistsVersionedReceipt() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(operationMapper.selectOne(any())).thenReturn(null);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IN_USE", 20L, OWNER_ID, 3));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);

        assertThat(service.returnAssetAgent(ACTOR_ID,
                new AssetAgentReturnCommand(ASSET_ID, 3, "员工归还"), "return-operation"))
                .isEqualTo(new AssetAgentReturnReceipt(ASSET_ID, "IDLE", 4));
        ArgumentCaptor<AssetOperation> operation = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(operation.capture());
        assertThat(operation.getValue().getOperationType()).isEqualTo("RETURN");
        assertThat(operation.getValue().getToOwnerUserId()).isNull();
        assertThat(operation.getValue().getSourceVersion()).isEqualTo(3);
        assertThat(operation.getValue().getResultVersion()).isEqualTo(4);
        verify(auditService).recordTransactional(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void agentReturnReplayRejectsChangedReasonWithoutWritingAgain() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetOperation stored = new AssetOperation();
        stored.setAssetId(ASSET_ID);
        stored.setOperationType("RETURN");
        stored.setToStatus("IDLE");
        stored.setReason("原原因");
        stored.setSourceVersion(3);
        stored.setResultVersion(4);
        when(operationMapper.selectOne(any())).thenReturn(stored);

        assertThatThrownBy(() -> service.returnAssetAgent(ACTOR_ID,
                new AssetAgentReturnCommand(ASSET_ID, 3, "变更原因"), "return-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(assetMapper, never()).update(any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void agentRepairStartMovesIdleAssetAndPersistsVersionedReceipt() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(operationMapper.selectOne(any())).thenReturn(null);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 4));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);

        assertThat(service.startAssetRepairAgent(ACTOR_ID,
                new AssetAgentRepairStartCommand(ASSET_ID, 4, "电源故障送修"), "repair-operation"))
                .isEqualTo(new AssetAgentRepairStartReceipt(ASSET_ID, "REPAIRING", 5));
        ArgumentCaptor<AssetOperation> operation = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(operation.capture());
        assertThat(operation.getValue().getOperationType()).isEqualTo("REPAIR_START");
        assertThat(operation.getValue().getToStatus()).isEqualTo("REPAIRING");
        assertThat(operation.getValue().getAgentOperationKey()).isEqualTo("repair-operation");
        assertThat(operation.getValue().getSourceVersion()).isEqualTo(4);
        assertThat(operation.getValue().getResultVersion()).isEqualTo(5);
        verify(auditService).recordTransactional(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void agentRepairStartRejectsInUseAssetAndBlankReason() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());

        assertThatThrownBy(() -> service.startAssetRepairAgent(ACTOR_ID,
                new AssetAgentRepairStartCommand(ASSET_ID, 4, " "), "repair-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");

        when(operationMapper.selectOne(any())).thenReturn(null);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IN_USE", 10L, OWNER_ID, 4));
        assertThatThrownBy(() -> service.startAssetRepairAgent(ACTOR_ID,
                new AssetAgentRepairStartCommand(ASSET_ID, 4, "设备故障"), "repair-operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(assetMapper, never()).update(any(), any());
        verify(operationMapper, never()).insert(any(AssetOperation.class));
    }

    @Test
    void returnRejectsAssetThatIsNotInUse() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 1));

        assertThatThrownBy(() -> service.returnAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(1, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void transferRequiresNewDepartment() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 1));

        assertThatThrownBy(() -> service.transferAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(1, null, 10L, "原部门内移动")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
    }

    @Test
    void optimisticConflictDoesNotWriteOperationHistory() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IN_USE", 10L, OWNER_ID, 3));
        when(assetMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.returnAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(2, null, null, "归还")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");

        verify(operationMapper, never()).insert(any(AssetOperation.class));
    }

    @Test
    void crossTenantAssetIsHidden() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger other = asset("IDLE", 10L, null, 1);
        other.setTenantId(2L);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(other);

        assertThatThrownBy(() -> service.claimAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(1, OWNER_ID, 20L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void repairStartMovesIdleAssetToRepairingAndWritesAudit() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger idle = asset("IDLE", 10L, null, 4);
        AssetLedger repairing = asset("REPAIRING", 10L, null, 5);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(idle, repairing);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);
        stubDetail();

        AssetLedgerResponse started = service.startAssetRepair(ACTOR_ID, ASSET_ID,
                new AssetMaintenanceRequest(4, "电源故障送修"));

        assertThat(started.status()).isEqualTo("REPAIRING");
        ArgumentCaptor<AssetOperation> captor = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("REPAIR_START");
        assertThat(captor.getValue().getToStatus()).isEqualTo("REPAIRING");
        verify(auditService).recordTransactional(any(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void repairCompletionMovesRepairingAssetBackToIdle() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger repairing = asset("REPAIRING", 10L, null, 5);
        AssetLedger idle = asset("IDLE", 10L, null, 6);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(repairing, idle);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);
        stubDetail();

        AssetLedgerResponse completed = service.completeAssetRepair(ACTOR_ID, ASSET_ID,
                new AssetMaintenanceRequest(5, "更换电源后检测正常"));

        assertThat(completed.status()).isEqualTo("IDLE");
        ArgumentCaptor<AssetOperation> captor = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("REPAIR_COMPLETE");
        assertThat(captor.getValue().getFromStatus()).isEqualTo("REPAIRING");
        assertThat(captor.getValue().getToStatus()).isEqualTo("IDLE");
    }

    @Test
    void repairCompletionRejectsIdleAsset() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 5));

        assertThatThrownBy(() -> service.completeAssetRepair(ACTOR_ID, ASSET_ID,
                new AssetMaintenanceRequest(5, "维修完成")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(operationMapper, never()).insert(any(AssetOperation.class));
    }

    @Test
    void inventoryDiscrepancyKeepsLedgerStateAndCapturesActualSnapshot() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger before = asset("IDLE", 10L, null, 7);
        AssetLedger after = asset("IDLE", 10L, null, 8);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(before, after);
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);
        when(userMapper.selectById(OWNER_ID)).thenReturn(owner(20L));
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);
        stubDetail();

        AssetLedgerResponse response = service.inventoryAsset(ACTOR_ID, ASSET_ID,
                new AssetInventoryRequest(7, "LOCATION_MISMATCH", "IN_USE", 20L, OWNER_ID,
                        "实物在研发部使用"));

        assertThat(response.status()).isEqualTo("IDLE");
        ArgumentCaptor<AssetOperation> captor = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("INVENTORY");
        assertThat(captor.getValue().getInventoryResult()).isEqualTo("LOCATION_MISMATCH");
        assertThat(captor.getValue().getActualOwnerUserId()).isEqualTo(OWNER_ID);
    }

    @Test
    void matchedInventoryMustEqualBookSnapshot() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("IDLE", 10L, null, 2));
        when(accessControlMapper.countDepartment(TENANT_ID, 20L)).thenReturn(1);

        assertThatThrownBy(() -> service.inventoryAsset(ACTOR_ID, ASSET_ID,
                new AssetInventoryRequest(2, "MATCH", "IDLE", 20L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void scrappedAssetCannotBeClaimedTransferredRepairedOrInventoried() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        when(assetMapper.selectById(ASSET_ID)).thenReturn(asset("SCRAPPED", 10L, null, 9));

        assertThatThrownBy(() -> service.claimAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(9, OWNER_ID, 20L, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.transferAsset(ACTOR_ID, ASSET_ID,
                new AssetOperationRequest(9, null, 20L, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.startAssetRepair(ACTOR_ID, ASSET_ID,
                new AssetMaintenanceRequest(9, "尝试维修"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.inventoryAsset(ACTOR_ID, ASSET_ID,
                new AssetInventoryRequest(9, "MISSING", null, null, null, "复盘")))
                .isInstanceOf(BusinessException.class);
        verify(assetMapper, never()).update(any(), any());
    }

    @Test
    void scrapMovesIdleAssetToTerminalState() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(access());
        AssetLedger idle = asset("IDLE", 10L, null, 8);
        AssetLedger scrapped = asset("SCRAPPED", 10L, null, 9);
        when(assetMapper.selectById(ASSET_ID)).thenReturn(idle, scrapped);
        when(assetMapper.update(any(), any())).thenReturn(1);
        when(operationMapper.insert(any(AssetOperation.class))).thenReturn(1);
        stubDetail();

        AssetLedgerResponse response = service.scrapAsset(ACTOR_ID, ASSET_ID,
                new AssetMaintenanceRequest(8, "超过使用年限且无法修复"));

        assertThat(response.status()).isEqualTo("SCRAPPED");
        assertThat(response.canDelete()).isFalse();
        ArgumentCaptor<AssetOperation> captor = ArgumentCaptor.forClass(AssetOperation.class);
        verify(operationMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo("SCRAP");
        assertThat(captor.getValue().getToStatus()).isEqualTo("SCRAPPED");
    }

    private ResolvedUserAccess access() {
        return new ResolvedUserAccess(ACTOR_ID, "admin@example.com", TENANT_ID, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), List.of("assets:read", "asset:write", "asset:claim", "asset:return",
                        "asset:repair"),
                List.of("ALL"), 1L);
    }

    private AssetLedger asset(String status, Long departmentId, Long ownerId, int version) {
        AssetLedger asset = new AssetLedger();
        asset.setId(ASSET_ID);
        asset.setTenantId(TENANT_ID);
        asset.setAssetCode("ASSET-001");
        asset.setName("研发笔记本");
        asset.setCategory("IT设备");
        asset.setStatus(status);
        asset.setDepartmentId(departmentId);
        asset.setOwnerUserId(ownerId);
        asset.setOriginalValue(java.math.BigDecimal.valueOf(8000));
        asset.setVersion(version);
        asset.setDeleted(false);
        asset.setCreatedAt(LocalDateTime.now());
        asset.setUpdatedAt(LocalDateTime.now());
        return asset;
    }

    private User owner(Long departmentId) {
        User user = new User();
        user.setId(OWNER_ID);
        user.setTenantId(TENANT_ID);
        user.setDepartmentId(departmentId);
        user.setDisplayName("员工甲");
        user.setUsername("employee");
        user.setStatus(1);
        return user;
    }

    private List<DepartmentResponse> departments() {
        return List.of(new DepartmentResponse(10L, "ADMIN", "行政部", null, null, 1),
                new DepartmentResponse(20L, "RND", "研发部", null, null, 1));
    }

    private void stubDetail() {
        when(operationMapper.selectList(any())).thenReturn(List.of());
        when(accessControlMapper.selectDepartments(TENANT_ID)).thenReturn(departments());
    }

    private static void initializeTableMetadata(Class<?> entityClass, Class<?> mapperClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) return;
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace(mapperClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
