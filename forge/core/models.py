"""Validated data models used by the human-readable Forge memory."""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class ForgeModel(BaseModel):
    """Base model with predictable JSON output and forward-compatible fields."""

    model_config = ConfigDict(extra="allow", validate_assignment=True)


class Evidence(ForgeModel):
    kind: str = "reference"
    reference: str
    description: str = ""
    verified: bool = False


class Metrics(ForgeModel):
    tool_calls: int | str | None = None
    implementation_complexity: int | float | str | None = None
    execution_cost: int | float | str | None = None
    execution_time: int | float | str | None = None
    reliability: int | float | str | None = None
    maintainability: int | float | str | None = None
    performance: int | float | str | None = None
    compatibility: int | float | str | None = None


class SolutionVersion(ForgeModel):
    id: str
    version: int = Field(ge=1)
    description: str
    changes: list[str] = Field(default_factory=list)
    advantages: list[str] = Field(default_factory=list)
    disadvantages: list[str] = Field(default_factory=list)
    test_results: list[str] = Field(default_factory=list)
    metrics: Metrics = Field(default_factory=Metrics)
    created_at: datetime = Field(default_factory=utc_now)
    supersedes: str | None = None
    status: Literal["proposed", "experimental", "working", "preferred", "deprecated"] = "proposed"

    @field_validator("id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        if not value.startswith("SOL-"):
            raise ValueError("solution id must start with SOL-")
        return value


class Failure(ForgeModel):
    id: str
    experience_id: str
    attempt: str
    reason_failed: str
    environment: str | dict[str, Any] = ""
    error_signature: str = ""
    conditions: list[str] = Field(default_factory=list)
    workaround: str = ""
    resolved_by: str | None = None
    date: datetime = Field(default_factory=utc_now)
    source_refs: list[Evidence] = Field(default_factory=list)

    @field_validator("id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        if not value.startswith("FAIL-"):
            raise ValueError("failure id must start with FAIL-")
        return value


class Experience(ForgeModel):
    id: str
    title: str
    summary: str = ""
    status: Literal["active", "experimental", "stable", "deprecated", "archived"] = "active"
    created_at: datetime = Field(default_factory=utc_now)
    updated_at: datetime = Field(default_factory=utc_now)
    domains: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)
    engines: list[str] = Field(default_factory=list)
    languages: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    problem_class: str = ""
    problem_description: str = ""
    context: str = ""
    capabilities_required: list[str] = Field(default_factory=list)
    inputs: list[str] = Field(default_factory=list)
    outputs: list[str] = Field(default_factory=list)
    constraints: list[str] = Field(default_factory=list)
    known_attempts: list[str] = Field(default_factory=list)
    known_failures: list[str] = Field(default_factory=list)
    current_solution: str | None = None
    solution_versions: list[SolutionVersion] = Field(default_factory=list)
    patterns: list[str] = Field(default_factory=list)
    related_experiences: list[str] = Field(default_factory=list)
    transferability: str = ""
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    validation_status: Literal["observation", "experimental", "validated", "stable", "deprecated"] = "observation"
    metrics: Metrics = Field(default_factory=Metrics)
    tags: list[str] = Field(default_factory=list)
    project_refs: list[str] = Field(default_factory=list)
    source_refs: list[Evidence] = Field(default_factory=list)

    @field_validator("id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        if not value.startswith("EXP-"):
            raise ValueError("experience id must start with EXP-")
        return value


class Pattern(ForgeModel):
    id: str
    name: str
    description: str
    problem_classes: list[str] = Field(default_factory=list)
    applicable_domains: list[str] = Field(default_factory=list)
    conditions: list[str] = Field(default_factory=list)
    tradeoffs: list[str] = Field(default_factory=list)
    evidence: list[Evidence] = Field(default_factory=list)
    related_experiences: list[str] = Field(default_factory=list)
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    status: Literal["observation", "experimental", "validated", "stable", "deprecated"] = "observation"
    created_at: datetime = Field(default_factory=utc_now)
    updated_at: datetime = Field(default_factory=utc_now)
    tags: list[str] = Field(default_factory=list)

    @field_validator("id")
    @classmethod
    def validate_id(cls, value: str) -> str:
        if not value.startswith("PAT-"):
            raise ValueError("pattern id must start with PAT-")
        return value


class ProjectMemory(ForgeModel):
    id: str
    name: str
    summary: str = ""
    technologies: list[str] = Field(default_factory=list)
    constraints: list[str] = Field(default_factory=list)
    decisions: list[str] = Field(default_factory=list)
    experience_refs: list[str] = Field(default_factory=list)
    updated_at: datetime = Field(default_factory=utc_now)


class EvolutionProposal(ForgeModel):
    id: str
    problem: str
    component: str
    proposed_change: str
    expected_benefit: str
    risks: list[str] = Field(default_factory=list)
    required_tests: list[str] = Field(default_factory=list)
    status: Literal["proposed", "accepted", "rejected", "implemented"] = "proposed"
    created_at: datetime = Field(default_factory=utc_now)


class ProblemSpec(ForgeModel):
    text: str
    problem_class: str = ""
    domains: list[str] = Field(default_factory=list)
    capabilities_required: list[str] = Field(default_factory=list)
    inputs: list[str] = Field(default_factory=list)
    outputs: list[str] = Field(default_factory=list)
    constraints: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    tags: list[str] = Field(default_factory=list)
    event_type: str | None = None
    proposed_solution: str | None = None
    failed_attempt: str | None = None


class SearchHit(ForgeModel):
    id: str
    kind: Literal["experience", "pattern", "failure"]
    title: str
    score: float = Field(ge=0.0, le=1.0)
    lexical_score: float = 0.0
    structured_score: float = 0.0
    capability_overlap: list[str] = Field(default_factory=list)
    technology_overlap: list[str] = Field(default_factory=list)
    analogy: bool = False
    compatibility_note: str = ""
    path: str = ""


class NoveltyResult(ForgeModel):
    decision: Literal[
        "NEW_EXPERIENCE",
        "UPDATE_EXISTING",
        "NEW_SOLUTION_VERSION",
        "NEW_FAILURE",
        "NEW_PATTERN",
        "PROJECT_ONLY_UPDATE",
    ]
    similar_experiences: list[str] = Field(default_factory=list)
    similarity_scores: dict[str, float] = Field(default_factory=dict)
    reasoning_summary: str


class ContextBundle(ForgeModel):
    task: str
    project: str | None = None
    generated_at: datetime = Field(default_factory=utc_now)
    experiences: list[dict[str, Any]] = Field(default_factory=list)
    patterns: list[dict[str, Any]] = Field(default_factory=list)
    failures_to_avoid: list[dict[str, Any]] = Field(default_factory=list)
    project_context: dict[str, Any] | None = None
    analogy_notes: list[str] = Field(default_factory=list)
    estimated_tokens: int = 0
    truncated: bool = False
