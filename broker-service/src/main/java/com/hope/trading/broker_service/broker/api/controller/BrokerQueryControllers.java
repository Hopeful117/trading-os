package com.hope.trading.broker_service.broker.api.controller;

import com.hope.trading.broker_service.broker.application.service.BrokerOperationServices.*;
import com.hope.trading.broker_service.broker.domain.model.BrokerModels.*;
import java.util.*;
import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;

public final class BrokerQueryControllers {private BrokerQueryControllers(){}
    @RestController @RequestMapping("/internal/v1/broker-accounts") public static final class AccountController {private final GetAccountService service;public AccountController(GetAccountService s){service=s;}@GetMapping("/{id}")public ResponseEntity<AccountSnapshot> get(@PathVariable UUID id){return ResponseEntity.ok(service.get(id));}}
    @RestController @RequestMapping("/internal/v1/broker-accounts") public static final class PositionController {private final GetPositionsService service;public PositionController(GetPositionsService s){service=s;}@GetMapping("/{id}/positions")public ResponseEntity<List<PositionSnapshot>> get(@PathVariable UUID id){return ResponseEntity.ok(service.get(id));}}
    @RestController @RequestMapping("/internal/v1/broker-accounts") public static final class OrderController {private final GetOrdersService service;public OrderController(GetOrdersService s){service=s;}@GetMapping("/{id}/orders")public ResponseEntity<List<OrderSnapshot>> get(@PathVariable UUID id){return ResponseEntity.ok(service.get(id));}}
}
