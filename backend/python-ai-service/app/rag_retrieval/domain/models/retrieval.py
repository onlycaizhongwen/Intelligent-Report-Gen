from dataclasses import dataclass


@dataclass(frozen=True)
class RetrievalResult:
    chunk_id: int
    score: float
    content: str
