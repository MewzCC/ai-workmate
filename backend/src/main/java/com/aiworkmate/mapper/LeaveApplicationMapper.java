package com.aiworkmate.mapper;

import com.aiworkmate.dto.LeaveApplicationView;
import com.aiworkmate.dto.ApproverCandidateResponse;
import com.aiworkmate.dto.LeaveApprovalContextRow;
import com.aiworkmate.entity.LeaveApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeaveApplicationMapper extends BaseMapper<LeaveApplication> {

    LeaveApplicationView selectView(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id);

    List<LeaveApplicationView> selectAll(
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("keyword") String keyword,
            @Param("leaveType") String leaveType,
            @Param("size") int size,
            @Param("offset") int offset);

    long countAll(
            @Param("tenantId") Long tenantId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("keyword") String keyword,
            @Param("leaveType") String leaveType);

    List<com.aiworkmate.dto.ApprovalStatusCountResponse> selectStatusCounts(
            @Param("tenantId") Long tenantId);

    List<LeaveApplicationView> selectMine(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") int offset);

    long countMine(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status);

    Long resolveApprover(
            @Param("tenantId") Long tenantId,
            @Param("applicantId") Long applicantId);

    List<ApproverCandidateResponse> selectApproverCandidates(
            @Param("tenantId") Long tenantId,
            @Param("applicantId") Long applicantId,
            @Param("recommendedId") Long recommendedId,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset);

    long countApproverCandidates(
            @Param("tenantId") Long tenantId,
            @Param("applicantId") Long applicantId,
            @Param("keyword") String keyword);

    int countEligibleApprover(
            @Param("tenantId") Long tenantId,
            @Param("applicantId") Long applicantId,
            @Param("approverId") Long approverId);

    Long selectLeaveDefinitionId(@Param("tenantId") Long tenantId);

    LeaveApprovalContextRow selectApprovalContext(
            @Param("tenantId") Long tenantId,
            @Param("applicantId") Long applicantId,
            @Param("approverId") Long approverId);
}
