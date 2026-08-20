package com.workflowautomation.jiraplanner.web;

import com.workflowautomation.jiraplanner.dashboard.DashboardService;
import com.workflowautomation.jiraplanner.dashboard.DashboardSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardSnapshot dashboard() {
        return dashboardService.snapshot();
    }
}
