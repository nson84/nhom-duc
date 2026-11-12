package com.aluminum.manager.finance;

import com.aluminum.manager.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class AnnualBookController {
    private final AnnualBookService service;

    public AnnualBookController(AnnualBookService service) {
        this.service = service;
    }

    @GetMapping("/annual-book")
    public ApiResponse<AnnualBookService.Book> book(@RequestParam int year, @RequestParam String shopCode) {
        return ApiResponse.ok(service.book(year, shopCode), null);
    }
}
