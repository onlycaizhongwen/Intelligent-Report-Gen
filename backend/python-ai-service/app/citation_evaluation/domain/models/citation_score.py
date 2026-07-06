from dataclasses import dataclass


@dataclass(frozen=True)
class CitationScore:
    source_id: int
    total_score: float
    confidence_level: str
