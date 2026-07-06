from dataclasses import dataclass


@dataclass(frozen=True)
class KnowledgeDocument:
    id: int
    title: str
    parse_status: str
