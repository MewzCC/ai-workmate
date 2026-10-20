package com.aiworkmate.service.model;

/** Null means unchanged in a patch; an empty optional text explicitly clears that field. */
public record MeetingRoomAgentFields(String code, String name, String location, Integer capacity,
                                     String facilities, String status, String remark) { }
