package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank(message = "{validation.supplier.code.required}")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$", message = "{validation.supplier.code.invalid}")
        String code,
        @NotBlank(message = "{validation.supplier.name.required}")
        @Size(max = 160, message = "{validation.supplier.name.tooLong}") String name,
        @Size(max = 80, message = "{validation.supplier.shortName.tooLong}") String shortName,
        @Pattern(regexp = "^$|^[0-9A-Z]{18}$", message = "{validation.supplier.creditCode.invalid}")
        String unifiedSocialCreditCode,
        @NotBlank(message = "{validation.supplier.category.required}")
        @Pattern(regexp = "^(MATERIAL|SERVICE|LOGISTICS|CONSULTING|OTHER)$",
                message = "{validation.supplier.category.invalid}") String category,
        @NotBlank(message = "{validation.supplier.level.required}")
        @Pattern(regexp = "^(STRATEGIC|PREFERRED|STANDARD|RESTRICTED)$",
                message = "{validation.supplier.level.invalid}") String supplierLevel,
        @Size(max = 80, message = "{validation.supplier.contactName.tooLong}") String contactName,
        @Size(max = 32, message = "{validation.supplier.contactPhone.tooLong}") String contactPhone,
        @Email(message = "{validation.supplier.contactEmail.invalid}")
        @Size(max = 160, message = "{validation.supplier.contactEmail.tooLong}") String contactEmail,
        @Size(max = 300, message = "{validation.supplier.address.tooLong}") String address,
        @Size(max = 120, message = "{validation.supplier.paymentTerms.tooLong}") String paymentTerms,
        @Size(max = 1000, message = "{validation.supplier.riskNote.tooLong}") String riskNote,
        Integer version
) {
}
