package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Stable Agent-facing query port; implementations retain domain authorization. */
public interface TodoToolPort {
    Page query(ToolActorContext context, Query query);

    record Query(String status, LocalDateTime from, LocalDateTime to, int page, int size, String businessType) {
        public Query(String status,LocalDateTime from,LocalDateTime to,int page,int size) { this(status,from,to,page,size,"ALL"); }
    }
    record Page(List<Item> items, long total, int page, int size, String businessType,String status,LocalDateTime from,LocalDateTime to) {
        public Page { items = List.copyOf(items); }
        public Page(List<Item> items,long total,int page,int size) { this(items,total,page,size,"ALL","PENDING",null,null); }
    }
    record Item(Long id, Long applicationId, String applicantName, String leaveType,
                Integer durationHalfDays, String status, int version, LocalDateTime submittedAt,
                LocalDateTime dueAt, boolean overdue,String businessType,String title,String nodeName,boolean detailAvailable) {
        public Item(Long id,Long applicationId,String applicantName,String leaveType,int durationHalfDays,String status,
                    int version,LocalDateTime submittedAt,LocalDateTime dueAt,boolean overdue) {
            this(id,applicationId,applicantName,leaveType,durationHalfDays,status,version,submittedAt,dueAt,overdue,
                    "LEAVE_APPLICATION","LV-"+String.format(java.util.Locale.ROOT,"%06d",applicationId),null,true);
        }
    }
}
