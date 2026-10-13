package com.hope.trading.trading_core.challenge.api;

import com.hope.trading.trading_core.challenge.application.ChallengeCatalogService;
import com.hope.trading.trading_core.challenge.application.StartChallengeCommand;
import com.hope.trading.trading_core.challenge.application.StartChallengeService;
import com.hope.trading.trading_core.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChallengeLifecycleController {
    private final ChallengeCatalogService catalog;
    private final StartChallengeService startChallenge;

    public ChallengeLifecycleController(ChallengeCatalogService catalog, StartChallengeService startChallenge) {
        this.catalog = catalog;
        this.startChallenge = startChallenge;
    }

    @GetMapping("/challenges")
    public ResponseEntity<List<ChallengeDefinitionResponse>> catalog(Authentication authentication) {
        requireUser(authentication);
        return ResponseEntity.ok(catalog.selectable().stream().map(ChallengeDefinitionResponse::from).toList());
    }

    @PostMapping("/accounts/{accountId}/challenge")
    public ResponseEntity<ChallengeInstanceResponse> start(@PathVariable UUID accountId,
                                                             @Valid @RequestBody StartChallengeRequest request,
                                                             Authentication authentication) {
        UserDto owner = requireUser(authentication);
        var challenge = startChallenge.start(new StartChallengeCommand(owner.getUserId(), accountId,
                request.definitionId(), request.definitionVersion()));
        return ResponseEntity.ok(ChallengeInstanceResponse.from(challenge));
    }

    private static UserDto requireUser(Authentication authentication) {
        return (UserDto) authentication.getPrincipal();
    }
}
