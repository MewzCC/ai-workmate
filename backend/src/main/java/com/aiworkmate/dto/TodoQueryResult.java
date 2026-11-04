package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TodoQueryResult(List<UnifiedTodoResponse> records,long total,int page,int size,
        String businessType,String status,LocalDateTime from,LocalDateTime to) {
    public TodoQueryResult { records=List.copyOf(records); }
}
