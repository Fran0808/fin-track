package com.store.api.model.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class InstrumentAssignmentRequest {
    @Positive private Long financialInstrumentId;
}
