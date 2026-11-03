package com.rozpost.api.interfaces.rest.v1;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rozpost.api.application.approval.ApprovalCommandService;
import com.rozpost.api.interfaces.error.ApiException;
import com.rozpost.api.interfaces.rest.ApiExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Cross-tenant IDOR: GET /api/v1/posts/{id} must require X-Tenant-Id and 404 on foreign tenant
 * (same as missing — no existence leak).
 */
class PostTenantIdorTest {
  private ApprovalCommandService approval;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    approval = mock(ApprovalCommandService.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new ApprovalV1Controller(approval, jdbc))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void matchingTenantReturnsPost() throws Exception {
    UUID postId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    when(approval.snapshotForTenant(eq(postId), eq(tenantId)))
        .thenReturn(
            new ApprovalCommandService.PostSnapshot(
                postId.toString(),
                "awaiting_approval",
                0,
                3,
                "cap",
                "instr",
                null,
                null,
                null));

    mvc.perform(get("/api/v1/posts/{id}", postId).header(ApprovalV1Controller.TENANT_HEADER, tenantId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(postId.toString()))
        .andExpect(jsonPath("$.status").value("awaiting_approval"));

    verify(approval).snapshotForTenant(postId, tenantId);
  }

  @Test
  void foreignTenantReturns404NotFound() throws Exception {
    UUID postId = UUID.randomUUID();
    UUID foreignTenant = UUID.randomUUID();
    when(approval.snapshotForTenant(eq(postId), eq(foreignTenant)))
        .thenThrow(ApiException.notFound("post_not_found", "post not found: " + postId));

    mvc.perform(
            get("/api/v1/posts/{id}", postId)
                .header(ApprovalV1Controller.TENANT_HEADER, foreignTenant))
        .andExpect(status().isNotFound());
  }

  @Test
  void missingTenantHeaderReturns400() throws Exception {
    UUID postId = UUID.randomUUID();
    mvc.perform(get("/api/v1/posts/{id}", postId)).andExpect(status().isBadRequest());
  }
}
