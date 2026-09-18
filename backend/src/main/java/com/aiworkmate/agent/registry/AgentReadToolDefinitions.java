package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentReadToolDefinitions {

    public static final String VISITOR_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"bookingId":{"type":"integer","minimum":1},"queue":{"type":"string","enum":["MINE","PENDING"]},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["bookingId"],"not":{"anyOf":[{"required":["queue"]},{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["bookingId"]}}]}
            """.strip();

    public static final String VISITOR_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","hostName","visitorName","purpose","expectedVisitAt","expectedLeaveAt","partySize","status","version","canWithdraw","canDecide","canCheckIn","canMarkVisited","canLeave","canMarkNoShow"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"hostName":{"type":"string","maxLength":120},"visitorName":{"type":"string","maxLength":120},"visitorCompany":{"type":"string","maxLength":200},"purpose":{"type":"string","maxLength":1000},"expectedVisitAt":{"type":"string","maxLength":32},"expectedLeaveAt":{"type":"string","maxLength":32},"partySize":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"version":{"type":"integer","minimum":0},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"registeredByName":{"type":"string","maxLength":120},"checkedInAt":{"type":"string","maxLength":32},"visitedAt":{"type":"string","maxLength":32},"leftAt":{"type":"string","maxLength":32},"noShowAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canCheckIn":{"type":"boolean"},"canMarkVisited":{"type":"boolean"},"canLeave":{"type":"boolean"},"canMarkNoShow":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String SEAL_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"usageId":{"type":"integer","minimum":1},"queue":{"type":"string","enum":["MINE","PENDING"]},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["usageId"],"not":{"anyOf":[{"required":["queue"]},{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["usageId"]}}]}
            """.strip();

    public static final String SEAL_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","sealType","documentTitle","usageReason","copies","status","version","canWithdraw","canDecide","canRegisterUse","canReturn","canArchiveDocument"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"sealType":{"type":"string","maxLength":80},"documentTitle":{"type":"string","maxLength":255},"usageReason":{"type":"string","maxLength":1000},"copies":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"taskStatus":{"type":"string","maxLength":40},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"actualCopies":{"type":"integer","minimum":1},"handlerName":{"type":"string","maxLength":120},"usedAt":{"type":"string","maxLength":32},"returnedAt":{"type":"string","maxLength":32},"canWithdraw":{"type":"boolean"},"canDecide":{"type":"boolean"},"canRegisterUse":{"type":"boolean"},"canReturn":{"type":"boolean"},"canArchiveDocument":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String ATTENDANCE_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["resource"],"properties":{"resource":{"type":"string","enum":["TODAY","RECORDS","EXCEPTIONS","MY_REISSUES","PENDING_REISSUES","STATISTICS","SETTINGS"]},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"},"employeeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"year":{"type":"integer","minimum":2000,"maximum":2100},"month":{"type":"integer","minimum":1,"maximum":12},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String ATTENDANCE_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["resource","records","reissues","total","page","size"],"properties":{"resource":{"type":"string","enum":["TODAY","RECORDS","EXCEPTIONS","MY_REISSUES","PENDING_REISSUES","STATISTICS","SETTINGS"]},"today":{"type":"object","additionalProperties":false,"required":["clockDate","status","canClockIn","canClockOut"],"properties":{"id":{"type":"integer","minimum":1},"clockDate":{"type":"string","maxLength":10},"clockInTime":{"type":"string","maxLength":32},"clockOutTime":{"type":"string","maxLength":32},"status":{"type":"string","maxLength":40},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0},"canClockIn":{"type":"boolean"},"canClockOut":{"type":"boolean"}}},"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","employeeName","clockDate","status"],"properties":{"id":{"type":"integer","minimum":1},"employeeName":{"type":"string","maxLength":120},"clockDate":{"type":"string","maxLength":10},"clockInTime":{"type":"string","maxLength":32},"clockOutTime":{"type":"string","maxLength":32},"status":{"type":"string","maxLength":40},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0}}}},"reissues":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","clockDate","clockType","reason","status","canDecide","canWithdraw"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"clockDate":{"type":"string","maxLength":10},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"approverComment":{"type":"string","maxLength":1000},"submittedAt":{"type":"string","maxLength":32},"decidedAt":{"type":"string","maxLength":32},"canDecide":{"type":"boolean"},"canWithdraw":{"type":"boolean"}}}},"statistics":{"type":"object","additionalProperties":false,"required":["startDate","endDate","personal","team"],"properties":{"startDate":{"type":"string","maxLength":10},"endDate":{"type":"string","maxLength":10},"personal":{"type":"object","additionalProperties":false,"required":["employeeName","totalDays","normalDays","lateDays","earlyLeaveDays","missingDays","pendingReissueCount"],"properties":{"employeeName":{"type":"string","maxLength":120},"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"missingDays":{"type":"integer","minimum":0},"pendingReissueCount":{"type":"integer","minimum":0}}},"team":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["employeeName","totalDays","normalDays","lateDays","earlyLeaveDays","missingDays"],"properties":{"employeeName":{"type":"string","maxLength":120},"departmentName":{"type":"string","maxLength":120},"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"missingDays":{"type":"integer","minimum":0}}}}}},"settings":{"type":"object","additionalProperties":false,"required":["workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked"],"properties":{"workStartTime":{"type":"string","maxLength":16},"workEndTime":{"type":"string","maxLength":16},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"},"updatedAt":{"type":"string","maxLength":32}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String APPROVAL_CONFIGURATION_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["resource"],"properties":{"resource":{"type":"string","enum":["FORM","PROCESS","RULE"]},"keyword":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","ENABLED","DISABLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String APPROVAL_CONFIGURATION_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","resource","key","name","status","version","updatedAt"],"properties":{"id":{"type":"integer","minimum":1},"resource":{"type":"string","enum":["FORM","PROCESS","RULE"]},"key":{"type":"string","maxLength":120},"name":{"type":"string","maxLength":200},"description":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["DRAFT","ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0},"formName":{"type":"string","maxLength":200},"ruleType":{"type":"string","maxLength":80},"priority":{"type":"integer"},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String APPROVAL_TASK_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"keyword":{"type":"string","maxLength":200},"leaveType":{"type":"string","maxLength":40},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String APPROVAL_TASK_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["applicationId","applicantName","leaveType","durationDays","status","version","overdue"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"durationDays":{"type":"number","minimum":0.5},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String HR_ORGANIZATION_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"limit":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String HR_ORGANIZATION_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["departments","positions","employees"],"properties":{"departments":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":120},"parentId":{"type":"integer","minimum":1},"status":{"type":"integer","minimum":0,"maximum":1}}}},"positions":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","status"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":120},"status":{"type":"integer","minimum":0,"maximum":1}}}},"employees":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","name","role","status"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"role":{"type":"string","maxLength":80},"status":{"type":"integer","minimum":0,"maximum":1},"departmentId":{"type":"integer","minimum":1},"positionId":{"type":"integer","minimum":1},"approverName":{"type":"string","maxLength":120}}}}}}
            """.strip();

    public static final String HR_EMPLOYEE_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["employeeId"],"properties":{"employeeId":{"type":"integer","minimum":1}}}
            """.strip();

    public static final String HR_EMPLOYEE_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["id","name","role","status","employmentHistory","attendance","recentActivities"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"role":{"type":"string","maxLength":80},"status":{"type":"integer","minimum":0,"maximum":1},"createdAt":{"type":"string","maxLength":32},"departmentName":{"type":"string","maxLength":120},"positionName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"employmentHistory":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","changeType","effectiveDate"],"properties":{"id":{"type":"integer","minimum":1},"changeType":{"type":"string","maxLength":40},"effectiveDate":{"type":"string","maxLength":10},"targetDepartmentName":{"type":"string","maxLength":120},"targetPositionName":{"type":"string","maxLength":120},"targetSupervisorName":{"type":"string","maxLength":120},"appliedAt":{"type":"string","maxLength":32}}}},"attendance":{"type":"object","additionalProperties":false,"required":["totalDays","normalDays","lateDays","earlyLeaveDays","lateAndEarlyDays","missingClockDays"],"properties":{"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"lateAndEarlyDays":{"type":"integer","minimum":0},"missingClockDays":{"type":"integer","minimum":0}}},"recentActivities":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","type","title","status","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"type":{"type":"string","maxLength":40},"title":{"type":"string","maxLength":200},"status":{"type":"string","maxLength":40},"startDate":{"type":"string","maxLength":10},"endDate":{"type":"string","maxLength":10},"createdAt":{"type":"string","maxLength":32}}}}}}
            """.strip();

    public static final String EMPLOYEE_CHANGE_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PENDING","APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"keyword":{"type":"string","maxLength":200},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String EMPLOYEE_CHANGE_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","employeeName","applicantName","reviewApproverName","changeType","effectiveDate","reason","status","version","canApprove","canWithdraw"],"properties":{"id":{"type":"integer","minimum":1},"employeeName":{"type":"string","maxLength":120},"applicantName":{"type":"string","maxLength":120},"reviewApproverName":{"type":"string","maxLength":120},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"effectiveDate":{"type":"string","maxLength":10},"currentDepartmentName":{"type":"string","maxLength":120},"currentPositionName":{"type":"string","maxLength":120},"targetDepartmentName":{"type":"string","maxLength":120},"targetPositionName":{"type":"string","maxLength":120},"targetSupervisorName":{"type":"string","maxLength":120},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["PENDING","APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":0},"canApprove":{"type":"boolean"},"canWithdraw":{"type":"boolean"},"submittedAt":{"type":"string","maxLength":32},"decidedAt":{"type":"string","maxLength":32},"appliedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String ASSET_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"category":{"type":"string","maxLength":80},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String ASSET_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","assetCode","name","category","status","version","canEdit","canDelete"],"properties":{"id":{"type":"integer","minimum":1},"assetCode":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":160},"category":{"type":"string","maxLength":80},"specification":{"type":"string","maxLength":500},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"departmentName":{"type":"string","maxLength":120},"ownerName":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","maxLength":10},"originalValue":{"type":"number","minimum":0},"remark":{"type":"string","maxLength":1000},"version":{"type":"integer","minimum":0},"canEdit":{"type":"boolean"},"canDelete":{"type":"boolean"},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String MEETING_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"keyword":{"type":"string","maxLength":200},"roomStatus":{"type":"string","enum":["OPEN","CLOSED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"bookingStatus":{"type":"string","enum":["BOOKED","CANCELLED"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String MEETING_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["rooms","bookings","bookingTotal","page","size"],"properties":{"rooms":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","code","name","capacity","status","canEdit","canDelete"],"properties":{"id":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":80},"name":{"type":"string","maxLength":160},"location":{"type":"string","maxLength":255},"capacity":{"type":"integer","minimum":0},"facilities":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["OPEN","CLOSED"]},"remark":{"type":"string","maxLength":1000},"canEdit":{"type":"boolean"},"canDelete":{"type":"boolean"}}}},"bookings":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","roomId","title","startAt","endAt","attendeeCount","status","version","canCancel"],"properties":{"id":{"type":"integer","minimum":1},"roomId":{"type":"integer","minimum":1},"roomCode":{"type":"string","maxLength":80},"roomName":{"type":"string","maxLength":160},"roomLocation":{"type":"string","maxLength":255},"organizerName":{"type":"string","maxLength":120},"title":{"type":"string","maxLength":200},"agenda":{"type":"string","maxLength":2000},"startAt":{"type":"string","maxLength":32},"endAt":{"type":"string","maxLength":32},"attendeeCount":{"type":"integer","minimum":1},"status":{"type":"string","enum":["BOOKED","CANCELLED"]},"version":{"type":"integer","minimum":0},"cancelledByName":{"type":"string","maxLength":120},"cancelledAt":{"type":"string","maxLength":32},"cancelReason":{"type":"string","maxLength":1000},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32},"canCancel":{"type":"boolean"}}}},"bookingTotal":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String TODO_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String TODO_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicationId","applicantName","leaveType","durationHalfDays","status","version","overdue"],"properties":{"id":{"type":"integer","minimum":1},"applicationId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"durationHalfDays":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String LEAVE_MINE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["applicationId"],"not":{"anyOf":[{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["applicationId"]}}]}
            """.strip();

    public static final String LEAVE_MINE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","leaveType","startDate","startPeriod","endDate","endPeriod","durationHalfDays","durationDays","reason","status","version","createdAt","updatedAt"],"properties":{"id":{"type":"integer","minimum":1},"approverName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"startDate":{"type":"string","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"durationHalfDays":{"type":"integer","minimum":1},"durationDays":{"type":"number","minimum":0.5},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String KNOWLEDGE_SEARCH_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":1000},"topK":{"type":"integer","minimum":1,"maximum":10},"minScore":{"type":"number","minimum":0.0,"maximum":1.0}}}
            """.strip();

    public static final String KNOWLEDGE_SEARCH_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","untrustedContent","usagePolicy"],"properties":{"items":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["content","score","matchType","citation"],"properties":{"content":{"type":"string","maxLength":12000},"score":{"type":"number","minimum":-1.0,"maximum":1.0},"matchType":{"type":"string","enum":["DENSE","SPARSE","HYBRID"]},"citation":{"type":"object","additionalProperties":false,"required":["documentId","chunkId","filename","chunkIndex"],"properties":{"documentId":{"type":"integer","minimum":1},"chunkId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"chunkIndex":{"type":"integer","minimum":0}}}}}},"untrustedContent":{"type":"boolean","const":true},"usagePolicy":{"type":"string","const":"DISPLAY_OR_SUMMARIZE_ONLY"}}}
            """.strip();

    public static final String NOTIFICATION_MINE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String NOTIFICATION_MINE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","type","title","content","read","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"type":{"type":"string","maxLength":40},"title":{"type":"string","maxLength":200},"content":{"type":"string","maxLength":2000},"businessType":{"type":"string","maxLength":40},"read":{"type":"boolean"},"createdAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    @Bean
    ToolDefinition visitorQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.VISITOR_QUERY, "Query my visitor bookings",
                "Returns a bounded owned, assigned or explicitly visible visitor booking summary.",
                "Display visitor workflow and arrival status without phone, plate or internal identity fields.",
                objectMapper.readTree(VISITOR_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(VISITOR_QUERY_OUTPUT_SCHEMA),
                Set.of("visitor:read:self"), OwnershipPolicy.SELF, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition sealQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.SEAL_QUERY, "Query my seal usages",
                "Returns a bounded owned, assigned or explicitly visible seal usage summary.",
                "Display approval and execution status without workflow identities or archive storage paths.",
                objectMapper.readTree(SEAL_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(SEAL_QUERY_OUTPUT_SCHEMA),
                Set.of("seal:read:self"), OwnershipPolicy.SELF, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition attendanceQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.ATTENDANCE_QUERY, "Query attendance views",
                "Returns one bounded attendance view selected from the authenticated page context.",
                "Display current, record, exception, reissue, statistics or settings data after live scope checks.",
                objectMapper.readTree(ATTENDANCE_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(ATTENDANCE_QUERY_OUTPUT_SCHEMA),
                Set.of("attendance:read"), OwnershipPolicy.TENANT_SCOPED, 50, 196608, 15000);
    }

    @Bean
    ToolDefinition approvalConfigurationQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.APPROVAL_CONFIGURATION_QUERY, "Query approval configuration",
                "Returns bounded approval forms, processes or rules from the authenticated tenant.",
                "Display tenant-scoped approval configuration summaries without executable schema or rule payloads.",
                objectMapper.readTree(APPROVAL_CONFIGURATION_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(APPROVAL_CONFIGURATION_QUERY_OUTPUT_SCHEMA),
                Set.of("approval:read"), OwnershipPolicy.TENANT_SCOPED, 50, 65536, 15000);
    }

    @Bean
    ToolDefinition approvalTaskQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.APPROVAL_TASK_QUERY, "Query tenant approval tasks",
                "Returns bounded approval tasks visible to the authenticated actor's live tenant data scope.",
                "Display approval-center task summaries without internal identities or workflow payloads.",
                objectMapper.readTree(APPROVAL_TASK_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(APPROVAL_TASK_QUERY_OUTPUT_SCHEMA),
                Set.of("approval:read"), OwnershipPolicy.TENANT_SCOPED, 50, 65536, 15000);
    }

    @Bean
    ToolDefinition hrOrganizationQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.HR_ORGANIZATION_QUERY, "Query visible organization",
                "Returns bounded departments, positions and employees visible in the authenticated actor's data scope.",
                "Display an organization overview without emails, avatars or internal permission data.",
                objectMapper.readTree(HR_ORGANIZATION_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(HR_ORGANIZATION_QUERY_OUTPUT_SCHEMA),
                Set.of("hr:read"), OwnershipPolicy.TENANT_SCOPED, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition hrEmployeeQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.HR_EMPLOYEE_QUERY, "Query visible employee profile",
                "Returns one employee profile only when visible in the authenticated actor's live data scope.",
                "Display employment, attendance summary and recent activity without contact or attachment data.",
                objectMapper.readTree(HR_EMPLOYEE_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(HR_EMPLOYEE_QUERY_OUTPUT_SCHEMA),
                Set.of("hr:read"), OwnershipPolicy.TENANT_SCOPED, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition employeeChangeQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.HR_CHANGE_QUERY, "Query employee changes",
                "Returns bounded employee changes visible to the authenticated tenant actor.",
                "Display employee-change summaries without internal user identities or decision payloads.",
                objectMapper.readTree(EMPLOYEE_CHANGE_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(EMPLOYEE_CHANGE_QUERY_OUTPUT_SCHEMA),
                Set.of("hr:read"), OwnershipPolicy.TENANT_SCOPED, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition assetQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.ASSET_QUERY, "Query tenant assets",
                "Returns bounded asset ledger records visible to the authenticated tenant actor.",
                "Display asset summaries without internal owner, department or operation identities.",
                objectMapper.readTree(ASSET_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(ASSET_QUERY_OUTPUT_SCHEMA),
                Set.of("assets:read"), OwnershipPolicy.TENANT_SCOPED, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition meetingQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.MEETING_QUERY, "Query meeting rooms and my bookings",
                "Returns bounded tenant meeting rooms and bookings owned by the authenticated actor.",
                "Display room availability and the actor's booking summaries without internal user identities.",
                objectMapper.readTree(MEETING_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(MEETING_QUERY_OUTPUT_SCHEMA),
                Set.of("meeting:read:self"), OwnershipPolicy.TENANT_SCOPED, 50, 196608, 15000);
    }

    @Bean
    ToolDefinition todoQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.TODO_QUERY,
                "Query my approval tasks",
                "Returns approval tasks assigned to the authenticated user in the authenticated tenant.",
                "Display a bounded, read-only list of the current user's approval tasks.",
                objectMapper.readTree(TODO_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(TODO_QUERY_OUTPUT_SCHEMA),
                Set.of("todo:read"),
                OwnershipPolicy.ASSIGNED_TO_SELF,
                50,
                65536,
                15000
        );
    }

    @Bean
    ToolDefinition leaveMineToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.LEAVE_MINE,
                "Query my leave applications",
                "Returns leave applications owned by the authenticated user in the authenticated tenant.",
                "Display a bounded list or one owned leave application without exposing internal identities.",
                objectMapper.readTree(LEAVE_MINE_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_MINE_OUTPUT_SCHEMA),
                Set.of("leave:read:self"),
                OwnershipPolicy.SELF,
                50,
                65536,
                15000
        );
    }

    @Bean
    ToolDefinition knowledgeSearchToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.KNOWLEDGE_SEARCH, "Search authorized knowledge",
                "Searches only ready knowledge chunks owned by the authenticated user and tenant.",
                "Return cited untrusted knowledge data for display or a non-recursive summary.",
                objectMapper.readTree(KNOWLEDGE_SEARCH_INPUT_SCHEMA),
                objectMapper.readTree(KNOWLEDGE_SEARCH_OUTPUT_SCHEMA),
                Set.of("knowledge:search"), OwnershipPolicy.SELF, 10, 131072, 15000);
    }

    @Bean
    ToolDefinition notificationMineToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.NOTIFICATION_MINE, "Query my notifications",
                "Returns notifications owned by the authenticated user in the authenticated tenant.",
                "Display a bounded read-only notification list without internal business identifiers.",
                objectMapper.readTree(NOTIFICATION_MINE_INPUT_SCHEMA),
                objectMapper.readTree(NOTIFICATION_MINE_OUTPUT_SCHEMA),
                Set.of("notification:read:self"), OwnershipPolicy.SELF, 50, 65536, 15000);
    }
}
