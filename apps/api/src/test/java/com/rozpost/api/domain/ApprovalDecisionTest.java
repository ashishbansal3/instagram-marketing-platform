package com.rozpost.api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rozpost.api.domain.approval.ApprovalDecision;
import com.rozpost.api.domain.post.PostStatus;
import org.junit.jupiter.api.Test;

class ApprovalDecisionTest {
  @Test
  void parsesApproveVariants() {
    assertEquals(ApprovalDecision.approved, ApprovalDecision.fromButton("approve"));
    assertEquals(ApprovalDecision.approved, ApprovalDecision.fromButton("Approved"));
  }

  @Test
  void parsesDisapproveVariants() {
    assertEquals(ApprovalDecision.disapproved, ApprovalDecision.fromButton("disapprove"));
    assertEquals(ApprovalDecision.disapproved, ApprovalDecision.fromButton("DISAPPROVED"));
  }

  @Test
  void rejectsUnknownButton() {
    assertThrows(IllegalArgumentException.class, () -> ApprovalDecision.fromButton("maybe"));
  }

  @Test
  void awaitingApprovalCanDecide() {
    assertTrue(PostStatus.awaiting_approval.canDecideApproval());
    assertTrue(PostStatus.drafted.canDecideApproval());
  }
}
