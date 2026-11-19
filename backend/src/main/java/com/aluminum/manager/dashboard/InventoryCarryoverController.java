package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryCarryoverController {
    private final InventoryCarryoverService service;
    public InventoryCarryoverController(InventoryCarryoverService service) { this.service = service; }

    @GetMapping("/api/v1/dashboard/opening-inventory")
    public ApiResponse<InventoryCarryoverService.Opening> get(@RequestParam int year, @RequestParam(required = false) String month) {
        return ApiResponse.ok(service.get(year, month), null);
    }
}
