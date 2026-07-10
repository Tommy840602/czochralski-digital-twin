package com.twin.alarm.controller;

import com.twin.alarm.service.OeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oee")
@RequiredArgsConstructor
public class OeeController {

    private final OeeService oeeService;

    @GetMapping
    @PreAuthorize("hasAuthority('OEE_VIEW')")
    public OeeService.OeeResult getOee(
            @RequestParam String furnaceId,
            @RequestParam(defaultValue = "1440") int minutes) {
        return oeeService.calculate(furnaceId, minutes);
    }
}
