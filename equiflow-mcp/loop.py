import os
from pathlib import Path

import anthropic
from typing import Any, Callable, Coroutine

# Load .env from project root if ANTHROPIC_API_KEY isn't already in the environment
_env_file = Path(__file__).parent.parent / ".env"
if _env_file.exists() and not os.environ.get("ANTHROPIC_API_KEY"):
    for line in _env_file.read_text().splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            os.environ.setdefault(k.strip(), v.strip())


async def run_agent(
    system: str,
    tools: list[dict],
    call_tool_fn: Callable[[str, dict], Coroutine[Any, Any, str]],
    question: str,
    max_iterations: int = 10,
) -> str:
    client = anthropic.Anthropic()
    messages = [{"role": "user", "content": question}]

    for iteration in range(1, max_iterations + 1):
        print(f"\n[iteration {iteration}] sending to model...")
        response = client.messages.create(
            model="claude-opus-4-7",
            max_tokens=16000,
            system=system,
            tools=tools,
            messages=messages,
        )
        print(f"[iteration {iteration}] stop_reason={response.stop_reason}")

        if response.stop_reason == "end_turn":
            return next((b.text for b in response.content if hasattr(b, "text")), "")

        if response.stop_reason == "max_tokens":
            return "Agent stopped: response exceeded token limit."

        if response.stop_reason == "tool_use":
            messages.append({"role": "assistant", "content": response.content})

            tool_results = []
            for block in response.content:
                if block.type == "tool_use":
                    print(f"  >> {block.name}({block.input})")
                    result = await call_tool_fn(block.name, block.input)
                    print(f"  << {result}")
                    tool_results.append({
                        "type": "tool_result",
                        "tool_use_id": block.id,
                        "content": result,
                    })

            messages.append({"role": "user", "content": tool_results})

    return "Agent did not finish within the iteration limit."
