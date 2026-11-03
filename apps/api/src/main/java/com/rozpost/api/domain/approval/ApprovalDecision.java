package com.rozpost.api.domain.approval;

public enum ApprovalDecision {
  approved,
  disapproved;

  public static ApprovalDecision fromButton(String button) {
    if (button == null) {
      throw new IllegalArgumentException("button required");
    }
    return switch (button.trim().toLowerCase()) {
      case "approve", "approved" -> approved;
      case "disapprove", "disapproved" -> disapproved;
      default -> throw new IllegalArgumentException("button must be approve|disapprove");
    };
  }
}
