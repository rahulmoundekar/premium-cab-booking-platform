package com.suabhi.cabbooking.identity.api;

import com.suabhi.cabbooking.identity.domain.AppUserPrincipal;
import com.suabhi.cabbooking.identity.service.CustomerAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/profile")
public class CustomerProfileController {
    private final CustomerAccountService accountService;

    public CustomerProfileController(CustomerAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public CustomerAccountService.CustomerProfile getProfile(
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return accountService.findProfile(principal.getId());
    }

    @PatchMapping
    public CustomerAccountService.CustomerProfile updateProfile(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(principal.getId(), request.fullName(), request.phoneE164());
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 160) String fullName,
            @Pattern(regexp = "^$|\\+[1-9][0-9]{7,14}",
                    message = "Enter a phone number in international format, for example +919876543210.")
            String phoneE164) {}
}
