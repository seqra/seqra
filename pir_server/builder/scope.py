"""Variable scope tracking for PIR lowering."""

from __future__ import annotations
from dataclasses import dataclass, field


@dataclass
class Scope:
    """Tracks local variables and their temp names."""

    locals: dict[str, str] = field(default_factory=dict)
    temp_counter: int = 0

    def new_temp(self) -> str:
        name = f"$t{self.temp_counter}"
        self.temp_counter += 1
        return name

    def resolve_local(self, name: str) -> str:
        """Get or create a local variable name."""
        if name not in self.locals:
            self.locals[name] = name
        return self.locals[name]


class ScopeStack:
    """Stack of scopes for nested functions/closures."""

    def __init__(self):
        self.scopes: list[Scope] = [Scope()]

    @property
    def current(self) -> Scope:
        return self.scopes[-1]

    def push(self) -> None:
        self.scopes.append(Scope())

    def pop(self) -> Scope:
        return self.scopes.pop()

    def new_temp(self) -> str:
        return self.current.new_temp()

    def resolve_local(self, name: str) -> str:
        return self.current.resolve_local(name)
