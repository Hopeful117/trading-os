package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.application.ChallengeProgress;
import com.hope.trading.trading_core.challenge.application.ChallengeProgressService;
import com.hope.trading.trading_core.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/challenge")
public class ChallengeProgressController {
    private final ChallengeProgressService progress;

    public ChallengeProgressController(ChallengeProgressService progress) {
        this.progress = progress;
    }

    @GetMapping
    public ResponseEntity<ChallengeProgress> get(@PathVariable UUID accountId, Authentication authentication) {
        UserDto owner = (UserDto) authentication.getPrincipal();
        return ResponseEntity.ok(progress.get(accountId, owner.getUserId()));
    }
}
