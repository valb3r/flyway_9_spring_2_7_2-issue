package com.example.flyway_9_spring_2_7_2.controller;

import com.example.flyway_9_spring_2_7_2.config.BigDecimalSerializer;
import com.example.flyway_9_spring_2_7_2.repository.DummyRepository;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/dummies")
public class DummyController {

    private final DummyRepository repository;

    @GetMapping
    public List<DummyDto> dummies() {
        return repository.findAll().stream().map(it -> new DummyDto(it.getId(), it.getAmount())).toList();
    }

    @Data
    @AllArgsConstructor
    public static class DummyDto {

        private Long id;

        @JsonSerialize(using = BigDecimalSerializer.class, as = Number.class)
        private BigDecimal amount;
    }
}
