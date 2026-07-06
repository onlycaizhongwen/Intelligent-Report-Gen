from dataclasses import dataclass


@dataclass(frozen=True)
class ModelInvocation:
    provider: str
    model_name: str
    audit_event_key: str
