package com.qorbit.engine.controller;

import com.qorbit.engine.selenium.DriverManager;
import com.qorbit.engine.service.GravacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/selenium")
public class SeleniumStatusController {

    @Autowired private DriverManager driverManager;
    @Autowired private GravacaoService gravacaoService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean ativo = driverManager.isAtivo() || gravacaoService.isGravando();
        return Map.of("ativo", ativo);
    }
}
