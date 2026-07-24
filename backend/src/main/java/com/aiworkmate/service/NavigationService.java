package com.aiworkmate.service;

import com.aiworkmate.dto.NavigationRouteResponse;

import java.util.List;

public interface NavigationService {

    List<NavigationRouteResponse> navigation(Long userId);
}
