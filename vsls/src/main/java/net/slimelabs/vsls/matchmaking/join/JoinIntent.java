package net.slimelabs.vsls.matchmaking.join;

public sealed interface JoinIntent permits GameTypeJoin, BlueprintJoin, SpecificServerJoin {}