package com.store.api.service.instrument;

import com.store.api.service.transaction.TransactionService;
import com.store.api.service.transaction.InstrumentAssignmentService;
import com.store.api.config.GlobalExceptionHandler;
import com.store.api.config.security.JwtAuthFilter;
import com.store.api.config.security.UserContext;
import com.store.api.controller.instrument.FinancialInstrumentController;
import com.store.api.controller.transaction.TransactionController;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.entity.*;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.*;
import com.store.api.service.auth.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Opt in only against a separately created disposable PostgreSQL database.
@EnabledIfEnvironmentVariable(named = "INSTRUMENT_TEST_DATABASE_URL",
        matches = "^jdbc:postgresql://localhost:[0-9]+/fintrack_instruments_eval_[a-z0-9_]+$")
@SpringBootTest(properties = {
        "spring.datasource.url=${INSTRUMENT_TEST_DATABASE_URL}",
        "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.show-sql=false", "mail.sync.enabled=false",
        "jwt.secret=instrument-tests-only-signing-key-at-least-32-bytes"
})
@Transactional
class FinancialInstrumentPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private FinancialInstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionService transactionService;
    @Autowired private InstrumentAssignmentService assignments;
    @Autowired private FinancialInstrumentController instrumentController;
    @Autowired private TransactionController transactionController;
    @Autowired private JwtAuthFilter filter;
    @Autowired private JwtService jwt;
    private User owner;
    private User other;
    private MockMvc mvc;
    private String token;

    @BeforeEach public void setup() {
        owner = users.saveAndFlush(User.builder().email("owner@example.test").build());
        other = users.saveAndFlush(User.builder().email("other@example.test").build());
        token = "Bearer " + jwt.generateToken(owner);
        mvc = MockMvcBuilders.standaloneSetup(instrumentController, transactionController)
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(filter).build();
    }
    @AfterEach public void clear() { UserContext.clear(); }

    @Test void validatesHttpRequestsAndCreatesPrivateProducts() throws Exception {
        mvc.perform(post("/api/v1/financial-instruments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"BANK_ACCOUNT","alias":"Main","bank":"BCP","lastFour":"1234"}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.currency").value("PEN"));
        mvc.perform(post("/api/v1/financial-instruments").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"CREDIT_CARD","alias":"Invalid","bank":"BCP","lastFour":"12345"}
                """)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/financial-instruments").header("Authorization", "Bearer " + jwt.generateToken(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/financial-instruments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/financial-instruments").header("X-Device-Token", owner.getDevicePairingToken()))
                .andExpect(status().isForbidden());
    }

    @Test void assignmentsFilterOnlyDirectOwnedMovementsAndDoNotChangeTotalsOrRetries() throws Exception {
        UserContext.setCurrentUser(owner);
        var account = instruments.save(FinancialInstrument.builder().user(owner).alias("Account")
                .bank(com.store.api.model.enums.Bank.BCP).type(com.store.api.model.enums.InstrumentType.BANK_ACCOUNT).build());
        var card = instruments.save(FinancialInstrument.builder().user(owner).alias("Debit")
                .bank(com.store.api.model.enums.Bank.BCP).type(com.store.api.model.enums.InstrumentType.DEBIT_CARD).linkedAccount(account).lastFour("1234").build());
        var request = request("original");
        long id = transactionService.processAndSave(request).getId();
        BigDecimal before = transactions.sumAmountByFlowType(FlowType.EXPENSE, owner.getId());
        assignments.assign(id, card.getId());
        assertEquals(before, transactions.sumAmountByFlowType(FlowType.EXPENSE, owner.getId()));
        assertEquals(card.getId(), transactionService.processAndSave(request).getFinancialInstrument().getId());
        assertEquals(1, transactions.countByUserId(owner.getId()));
        mvc.perform(get("/api/v1/transactions").header("Authorization", token)
                .param("financialInstrumentId", card.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].financialInstrument.alias").value("Debit"));
        mvc.perform(get("/api/v1/transactions").header("Authorization", token)
                .param("financialInstrumentId", account.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer " + jwt.generateToken(other))
                .param("financialInstrumentId", card.getId().toString())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/transactions/" + id + "/financial-instrument")
                .header("Authorization", "Bearer " + jwt.generateToken(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"financialInstrumentId\":null}")).andExpect(status().isNotFound());
    }

    @Test void archivedHistorySurvivesAndSuggestionsNeverMutate() throws Exception {
        UserContext.setCurrentUser(owner);
        var card = instruments.save(FinancialInstrument.builder().user(owner).alias("Debit")
                .bank(com.store.api.model.enums.Bank.BCP).type(com.store.api.model.enums.InstrumentType.DEBIT_CARD).lastFour("1234").build());
        long id = transactionService.processAndSave(request("history")).getId();
        mvc.perform(get("/api/v1/transactions/" + id + "/financial-instrument-suggestions").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertNull(transactions.findById(id).orElseThrow().getFinancialInstrument());
        mvc.perform(patch("/api/v1/transactions/" + id + "/financial-instrument").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"financialInstrumentId\":" + card.getId() + "}"))
                .andExpect(status().isOk());
        card.setActive(false); instruments.saveAndFlush(card);
        mvc.perform(get("/api/v1/transactions/" + id + "/financial-instrument-suggestions").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/transactions").header("Authorization", token))
                .andExpect(jsonPath("$.content[0].financialInstrument.active").value(false));
        mvc.perform(patch("/api/v1/transactions/" + id + "/financial-instrument").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"financialInstrumentId\":" + card.getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/transactions/" + id + "/financial-instrument").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"financialInstrumentId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.financialInstrument").doesNotExist());
    }

    private TransactionSyncRequest request(String hash) {
        var request = new TransactionSyncRequest();
        request.setAmount(new BigDecimal("25.50")); request.setFlowType(FlowType.EXPENSE);
        request.setContactName("Store"); request.setChannel("TARJETA_DEBITO_BCP");
        request.setCardLast4("1234"); request.setTransactionHash(hash);
        request.setTransactionDate(LocalDateTime.of(2026, 10, 1, 12, 0));
        return request;
    }
}
