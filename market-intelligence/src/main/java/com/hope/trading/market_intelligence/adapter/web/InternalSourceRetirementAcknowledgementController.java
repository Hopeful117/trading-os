package com.hope.trading.market_intelligence.adapter.web;

import com.hope.trading.market_intelligence.application.research.SourceRetirementAcknowledgementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/research/source-retirement")
public class InternalSourceRetirementAcknowledgementController {
    private final SourceRetirementAcknowledgementService service;

    public InternalSourceRetirementAcknowledgementController(SourceRetirementAcknowledgementService service) {
        this.service = service;
    }

    @PostMapping("/acknowledgements")
    public ResponseEntity<SourceRetirementAcknowledgementService.Acknowledgement> acknowledge(
            @RequestBody SourceRetirementAcknowledgementService.Request request) {
        return ResponseEntity.ok(service.acknowledge(request));
    }
}
