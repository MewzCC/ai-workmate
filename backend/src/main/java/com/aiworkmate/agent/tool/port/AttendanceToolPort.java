package com.aiworkmate.agent.tool.port;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Transport-neutral attendance read contract; a remote adapter can replace the local implementation. */
public interface AttendanceToolPort {
    Result query(ToolActorContext context, Query query);
    ReissueWriteResult submitReissue(
            ToolActorContext context, ReissueCommand command, ToolOperationKey operationKey);

    enum Resource { TODAY, RECORDS, EXCEPTIONS, MY_REISSUES, PENDING_REISSUES, STATISTICS, SETTINGS }

    record Query(Resource resource, LocalDate from, LocalDate to, Long employeeId, String status,
                 Integer year, Integer month, int page, int size) { }
    record ReissueCommand(LocalDate clockDate, String clockType, String reason) { }
    record ReissueWriteResult(long reissueId, String status, LocalDate clockDate,
                              String clockType, LocalDateTime submittedAt)
            implements ToolWriteReceipt { }

    record Result(Resource resource, Today today, List<Record> records, List<Reissue> reissues,
                  Statistics statistics, Settings settings, long total, int page, int size) {
        public Result {
            records = List.copyOf(records);
            reissues = List.copyOf(reissues);
        }
    }

    record Today(Long id, LocalDate clockDate, LocalDateTime clockInTime, LocalDateTime clockOutTime,
                 String status, Integer lateMinutes, Integer earlyLeaveMinutes,
                 boolean canClockIn, boolean canClockOut) { }

    record Record(long id, String employeeName, LocalDate clockDate, LocalDateTime clockInTime,
                  LocalDateTime clockOutTime, String status, Integer lateMinutes,
                  Integer earlyLeaveMinutes) { }

    record Reissue(long id, String applicantName, String approverName, LocalDate clockDate,
                   String clockType, String reason, String status, String approverComment,
                   LocalDateTime submittedAt, LocalDateTime decidedAt,
                   boolean canDecide, boolean canWithdraw) { }

    record Statistics(LocalDate startDate, LocalDate endDate, PersonalStats personal,
                      List<TeamStats> team) {
        public Statistics { team = List.copyOf(team); }
    }

    record PersonalStats(String employeeName, int totalDays, int normalDays, int lateDays,
                         int earlyLeaveDays, int missingDays, int pendingReissueCount) { }

    record TeamStats(String employeeName, String departmentName, int totalDays, int normalDays,
                     int lateDays, int earlyLeaveDays, int missingDays) { }

    record Settings(LocalTime workStartTime, LocalTime workEndTime, Integer startFlexMinutes,
                    Integer endFlexMinutes, Boolean flexLinked, LocalDateTime updatedAt) { }
}
