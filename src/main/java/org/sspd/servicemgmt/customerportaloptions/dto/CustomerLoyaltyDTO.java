package org.sspd.servicemgmt.customerportaloptions.dto;

public record CustomerLoyaltyDTO(int currentPoints, int totalEarned, int pointsToNextTier, String tierName) {}
